/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 */
package net.fastclient.hud.store;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.fastclient.hud.FastClientHUDClient;

public class CosmeticTextureCache {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    private static volatile CosmeticTextureCache instance;
    private final Path cacheDir = FabricLoader.getInstance().getConfigDir().resolve("fastclient-hud-cosmetic-cache");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, CompletableFuture<Path>> pendingDownloads = new ConcurrentHashMap();

    public CosmeticTextureCache() {
        try {
            Files.createDirectories(this.cacheDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            FastClientHUDClient.LOGGER.warn("[TextureCache] Failed to create cache dir: {}", (Object)e.getMessage());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static CosmeticTextureCache getInstance() {
        if (instance != null) return instance;
        Class<CosmeticTextureCache> clazz = CosmeticTextureCache.class;
        synchronized (CosmeticTextureCache.class) {
            if (instance != null) return instance;
            instance = new CosmeticTextureCache();
            // ** MonitorExit[var0] (shouldn't be in output)
            return instance;
        }
    }

    public CompletableFuture<Path> getOrFetch(String cosmeticId, String assetUrl) {
        if (assetUrl == null || assetUrl.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        Path cachedFile = this.cacheDir.resolve(CosmeticTextureCache.sanitizeFileName(cosmeticId) + ".png");
        if (Files.exists(cachedFile, new LinkOption[0])) {
            return CompletableFuture.completedFuture(cachedFile);
        }
        return this.pendingDownloads.computeIfAbsent(cosmeticId, k -> {
            CompletableFuture future = new CompletableFuture();
            this.executor.execute(() -> {
                block12: {
                    try {
                        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(assetUrl)).timeout(REQUEST_TIMEOUT).GET().build();
                        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() == 200) {
                            try (InputStream in = response.body();){
                                byte[] data = in.readAllBytes();
                                Files.write(cachedFile, data, new OpenOption[0]);
                                FastClientHUDClient.LOGGER.info("[TextureCache] Cached {} ({} bytes)", (Object)cosmeticId, (Object)data.length);
                                future.complete(cachedFile);
                                break block12;
                            }
                        }
                        future.complete(null);
                    }
                    catch (Exception e) {
                        FastClientHUDClient.LOGGER.warn("[TextureCache] Failed to fetch {}: {}", (Object)cosmeticId, (Object)e.getMessage());
                        future.complete(null);
                    }
                    finally {
                        this.pendingDownloads.remove(cosmeticId);
                    }
                }
            });
            return future;
        });
    }

    private static String sanitizeFileName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}

