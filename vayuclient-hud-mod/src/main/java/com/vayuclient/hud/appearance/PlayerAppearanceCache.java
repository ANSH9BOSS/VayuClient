/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.appearance;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

public final class PlayerAppearanceCache<T>
implements AutoCloseable {
    public static final long SUCCESS_TTL_MILLIS = 900000L;
    public static final long NOT_FOUND_TTL_MILLIS = 900000L;
    public static final long TRANSIENT_TTL_MILLIS = 60000L;
    public static final int MAX_TEXTURE_BYTES = 25600;
    private static final String CDN_ROOT = "https://files.vayuclient.net/textures/";
    private static final byte[] PNG_SIGNATURE = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
    private final Path cacheDirectory;
    private final Eligibility eligibility;
    private final TextureRegistrar<T> registrar;
    private final HttpFetcher fetcher;
    private final Clock clock;
    private final ExecutorService executor;
    private final Map<String, Entry<T>> entries = new ConcurrentHashMap<String, Entry<T>>();
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean enabled = true;

    public PlayerAppearanceCache(Path cacheDirectory, Eligibility eligibility, TextureRegistrar<T> registrar) {
        this(cacheDirectory, eligibility, registrar, PlayerAppearanceCache::fetchHttp, Clock.systemUTC(), Executors.newFixedThreadPool(3, PlayerAppearanceCache.daemonThreadFactory()));
    }

    PlayerAppearanceCache(Path cacheDirectory, Eligibility eligibility, TextureRegistrar<T> registrar, HttpFetcher fetcher, Clock clock, ExecutorService executor) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Appearance<T> resolve(String exactUsername) {
        if (!this.enabled || exactUsername == null || exactUsername.isBlank() || !this.eligibility.isEligible(exactUsername)) {
            return Appearance.empty();
        }
        String key = exactUsername.toLowerCase(Locale.ROOT);
        Entry entry = this.entries.computeIfAbsent(key, ignored -> new Entry());
        long now = this.clock.millis();
        this.scheduleSkin(entry, key, exactUsername, now);
        this.scheduleCape(entry, key, exactUsername, now);
        Object skin = entry.skin.expiresAt > now ? (Object)entry.skin.texture : null;
        Object cape = entry.cape.expiresAt > now ? (Object)entry.cape.texture : null;
        boolean slim = skin != null && entry.slim;
        String skinUrl = skin != null ? entry.skinUrl : null;
        return new Appearance<T>((T)skin, slim, (T)cape, skinUrl);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.generation.incrementAndGet();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    private void scheduleSkin(Entry<T> entry, String key, String exactUsername, long now) {
        Part<T> part = entry.skin;
        if (part.expiresAt > now || part.inFlight != null) {
            return;
        }
        synchronized (part) {
            if (part.expiresAt > now || part.inFlight != null || !this.enabled) {
                return;
            }
            long requestGeneration = this.generation.get();
            CompletableFuture<Void> request = CompletableFuture.supplyAsync(() -> this.loadSkin(key, exactUsername), this.executor)
                .thenCompose(candidate -> this.register(candidate, key))
                .handle((registered, error) -> {
                    this.completeSkin(entry, (Registered<T>)registered, (Throwable)error, requestGeneration);
                    return null;
                });
            part.inFlight = request;
            request.whenComplete((res, ex) -> clearCompletedRequest(part, request));
        }
    }

    private void scheduleCape(Entry<T> entry, String key, String exactUsername, long now) {
        Part<T> part = entry.cape;
        if (part.expiresAt > now || part.inFlight != null) {
            return;
        }
        synchronized (part) {
            if (part.expiresAt > now || part.inFlight != null || !this.enabled) {
                return;
            }
            long requestGeneration = this.generation.get();
            CompletableFuture<Void> request = CompletableFuture.supplyAsync(() -> this.loadCape(key, exactUsername), this.executor)
                .thenCompose(candidate -> this.register(candidate, key))
                .handle((registered, error) -> {
                    this.completeCape(entry, (Registered<T>)registered, (Throwable)error, requestGeneration);
                    return null;
                });
            part.inFlight = request;
            request.whenComplete((res, ex) -> clearCompletedRequest(part, request));
        }
    }

    private static <T> void clearCompletedRequest(Part<T> part, CompletableFuture<Void> request) {
        synchronized (part) {
            if (part.inFlight == request) {
                part.inFlight = null;
            }
        }
    }

    private CompletableFuture<Registered<T>> register(Candidate candidate, String key) {
        if (candidate.outcome != Outcome.SUCCESS) {
            return CompletableFuture.completedFuture(new Registered<T>(candidate, null));
        }
        return this.registrar.register(candidate.kind, key, candidate.file, candidate.url).thenApply(texture -> new Registered<T>(candidate, (T)texture));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private void completeSkin(Entry<T> entry, Registered<T> registered, Throwable error, long requestGeneration) {
        long now = this.clock.millis();
        Part part = entry.skin;
        synchronized (part) {
            try {
                if (!this.enabled) return;
                if (this.generation.get() != requestGeneration) {
                    return;
                }
                if (error != null || registered == null) {
                    entry.skin.texture = null;
                    entry.skin.expiresAt = now + 60000L;
                    return;
                }
                Candidate candidate = registered.candidate;
                entry.skin.texture = registered.texture;
                entry.slim = candidate.slim;
                entry.skinUrl = candidate.outcome == Outcome.SUCCESS ? candidate.url : null;
                entry.skin.expiresAt = now + PlayerAppearanceCache.ttl(candidate.outcome);
            }
            finally {
                entry.skin.inFlight = null;
            }
            return;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private void completeCape(Entry<T> entry, Registered<T> registered, Throwable error, long requestGeneration) {
        long now = this.clock.millis();
        Part part = entry.cape;
        synchronized (part) {
            try {
                if (!this.enabled) return;
                if (this.generation.get() != requestGeneration) {
                    return;
                }
                if (error != null || registered == null) {
                    entry.cape.texture = null;
                    entry.cape.expiresAt = now + 60000L;
                    return;
                }
                Candidate candidate = registered.candidate;
                entry.cape.texture = registered.texture;
                entry.cape.expiresAt = now + PlayerAppearanceCache.ttl(candidate.outcome);
            }
            finally {
                entry.cape.inFlight = null;
            }
            return;
        }
    }

    private Candidate loadSkin(String key, String exactUsername) {
        Path skinDirectory = this.cacheDirectory.resolve("skins");
        Path primary = skinDirectory.resolve(key + ".png");
        Path primaryMissing = skinDirectory.resolve(key + ".missing");
        String primaryUrl = "https://files.vayuclient.net/textures/skins/" + exactUsername + ".png";
        Candidate cachedPrimary = this.cachedCandidate(TextureKind.SKIN, primary, primaryUrl, false);
        if (cachedPrimary != null) {
            return cachedPrimary;
        }
        FetchAttempt primaryAttempt = this.attempt(primary, primaryMissing, primaryUrl, 64, 64);
        if (primaryAttempt.outcome == Outcome.SUCCESS) {
            return Candidate.success(TextureKind.SKIN, primary, primaryUrl, primaryAttempt.slim);
        }
        Path fallback = skinDirectory.resolve(key + "-slim.png");
        Path fallbackMissing = skinDirectory.resolve(key + "-slim.missing");
        String fallbackUrl = "https://files.vayuclient.net/textures/skins/" + exactUsername + "-slim.png";
        Candidate cachedFallback = this.cachedCandidate(TextureKind.SKIN, fallback, fallbackUrl, true);
        if (cachedFallback != null) {
            return cachedFallback;
        }
        FetchAttempt fallbackAttempt = this.attempt(fallback, fallbackMissing, fallbackUrl, 64, 64);
        if (fallbackAttempt.outcome == Outcome.SUCCESS) {
            return Candidate.success(TextureKind.SKIN, fallback, fallbackUrl, true);
        }
        if (primaryAttempt.outcome == Outcome.NOT_FOUND && fallbackAttempt.outcome == Outcome.NOT_FOUND) {
            return Candidate.failure(TextureKind.SKIN, Outcome.NOT_FOUND);
        }
        return Candidate.failure(TextureKind.SKIN, Outcome.TRANSIENT);
    }

    private Candidate loadCape(String key, String exactUsername) {
        Path capeDirectory = this.cacheDirectory.resolve("capes");
        Path cape = capeDirectory.resolve(key + ".png");
        Path missing = capeDirectory.resolve(key + ".missing");
        String url = "https://files.vayuclient.net/textures/capes/" + exactUsername + ".png";
        Candidate cached = this.cachedCandidate(TextureKind.CAPE, cape, url, false);
        if (cached != null) {
            return cached;
        }
        FetchAttempt attempt = this.attempt(cape, missing, url, 64, 32);
        if (attempt.outcome == Outcome.SUCCESS) {
            return Candidate.success(TextureKind.CAPE, cape, url, false);
        }
        return Candidate.failure(TextureKind.CAPE, attempt.outcome);
    }

    private Candidate cachedCandidate(TextureKind kind, Path file, String url, boolean forceSlim) {
        if (!this.isFresh(file, 900000L)) {
            return null;
        }
        try {
            ValidatedImage image = PlayerAppearanceCache.validate(Files.readAllBytes(file), kind == TextureKind.SKIN ? 64 : 64, kind == TextureKind.SKIN ? 64 : 32);
            return Candidate.success(kind, file, url, forceSlim || image.slim);
        }
        catch (IOException | IllegalArgumentException ignored) {
            PlayerAppearanceCache.deleteQuietly(file);
            return null;
        }
    }

    private FetchAttempt attempt(Path file, Path missingMarker, String url, int width, int height) {
        if (this.isFresh(missingMarker, 900000L)) {
            return FetchAttempt.notFound();
        }
        FetchResponse response = this.fetcher.fetch(url);
        if (response.statusCode == 404) {
            PlayerAppearanceCache.deleteQuietly(file);
            PlayerAppearanceCache.touch(missingMarker);
            return FetchAttempt.notFound();
        }
        if (response.statusCode < 200 || response.statusCode >= 300 || response.body == null) {
            return FetchAttempt.transientFailure();
        }
        try {
            ValidatedImage image = PlayerAppearanceCache.validate(response.body, width, height);
            Files.createDirectories(file.getParent(), new FileAttribute[0]);
            Files.write(file, response.body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            PlayerAppearanceCache.deleteQuietly(missingMarker);
            return FetchAttempt.success(image.slim);
        }
        catch (IOException | IllegalArgumentException ignored) {
            PlayerAppearanceCache.deleteQuietly(file);
            return FetchAttempt.transientFailure();
        }
    }

    static ValidatedImage validate(byte[] bytes, int expectedWidth, int expectedHeight) {
        if (bytes == null || bytes.length == 0 || bytes.length > 25600 || !PlayerAppearanceCache.hasPngSignature(bytes)) {
            throw new IllegalArgumentException("Invalid PNG data");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                throw new IllegalArgumentException("Unexpected texture dimensions");
            }
            return new ValidatedImage(PlayerAppearanceCache.detectSlim(image));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Unable to decode PNG", e);
        }
    }

    static boolean detectSlim(BufferedImage image) {
        if (image.getWidth() != 64 || image.getHeight() != 64) {
            return false;
        }
        int background = image.getRGB(63, 20);
        boolean transparentBackground = (background >>> 24 & 0xFF) == 0;
        for (int x = 54; x <= 55; ++x) {
            for (int y = 20; y <= 31; ++y) {
                int pixel = image.getRGB(x, y);
                if (!(transparentBackground ? (pixel >>> 24 & 0xFF) != 0 : pixel != background)) continue;
                return false;
            }
        }
        return true;
    }

    private boolean isFresh(Path path, long ttl) {
        try {
            return Files.isRegularFile(path, new LinkOption[0]) && this.clock.millis() - Files.getLastModifiedTime(path, new LinkOption[0]).toMillis() < ttl;
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private static long ttl(Outcome outcome) {
        return switch (outcome.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> 900000L;
            case 1 -> 900000L;
            case 2 -> 60000L;
        };
    }

    private static boolean hasPngSignature(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; ++i) {
            if (bytes[i] == PNG_SIGNATURE[i]) continue;
            return false;
        }
        return true;
    }

    private static void touch(Path marker) {
        try {
            Files.createDirectories(marker.getParent(), new FileAttribute[0]);
            Files.write(marker, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    /*
     * Exception decompiling
     */
    private static FetchResponse fetchHttp(String url) {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Tried to end blocks [3[TRYBLOCK]], but top level block is 20[WHILELOOP]
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.processEndingBlocks(Op04StructuredStatement.java:435)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:484)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
         *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicLong index = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, "FC-Appearance-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        this.generation.incrementAndGet();
        this.executor.shutdownNow();
    }

    private static /* synthetic */ void lambda$scheduleCape$8(Part part, CompletableFuture request, Void ignored, Throwable error) {
        PlayerAppearanceCache.clearCompletedRequest(part, request);
    }

    private static /* synthetic */ void lambda$scheduleSkin$4(Part part, CompletableFuture request, Void ignored, Throwable error) {
        PlayerAppearanceCache.clearCompletedRequest(part, request);
    }

    static interface HttpFetcher {
        public FetchResponse fetch(String var1);
    }

    public static interface Eligibility {
        public boolean isEligible(String var1);
    }

    public static interface TextureRegistrar<T> {
        public CompletableFuture<T> register(TextureKind var1, String var2, Path var3, String var4);
    }

    public record Appearance<T>(T skin, boolean slim, T cape, String skinUrl) {
        private static <T> Appearance<T> empty() {
            return new Appearance<T>(null, false, null, null);
        }
    }

    private static final class Entry<T> {
        private final Part<T> skin = new Part();
        private final Part<T> cape = new Part();
        private volatile boolean slim;
        private volatile String skinUrl;

        private Entry() {
        }
    }

    private static final class Part<T> {
        private volatile T texture;
        private volatile long expiresAt;
        private volatile CompletableFuture<Void> inFlight;

        private Part() {
        }
    }

    private record Candidate(TextureKind kind, Outcome outcome, Path file, String url, boolean slim) {
        private static Candidate success(TextureKind kind, Path file, String url, boolean slim) {
            return new Candidate(kind, Outcome.SUCCESS, file, url, slim);
        }

        private static Candidate failure(TextureKind kind, Outcome outcome) {
            return new Candidate(kind, outcome, null, null, false);
        }
    }

    private static enum Outcome {
        SUCCESS,
        NOT_FOUND,
        TRANSIENT;

    }

    private record Registered<T>(Candidate candidate, T texture) {
    }

    public static enum TextureKind {
        SKIN,
        CAPE;

    }

    private record FetchAttempt(Outcome outcome, boolean slim) {
        private static FetchAttempt success(boolean slim) {
            return new FetchAttempt(Outcome.SUCCESS, slim);
        }

        private static FetchAttempt notFound() {
            return new FetchAttempt(Outcome.NOT_FOUND, false);
        }

        private static FetchAttempt transientFailure() {
            return new FetchAttempt(Outcome.TRANSIENT, false);
        }
    }

    record ValidatedImage(boolean slim) {
    }

    record FetchResponse(int statusCode, byte[] body) {
    }
}

