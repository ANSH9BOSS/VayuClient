/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.network;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fastclient.hud.FastClientHUDClient;

public final class FastClientUserCache {
    private static final String MANIFEST_URL = "https://files.fastclient.net/fastclient/active-users.txt";
    private static final String MD5_URL = "https://files.fastclient.net/fastclient/players.md5";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5L);
    private static final long REFRESH_INTERVAL_BASE_SEC = 60L;
    private static final long REFRESH_JITTER_SEC = 10L;
    private static volatile FastClientUserCache instance;
    private volatile Set<String> activeUsers = Collections.emptySet();
    private String lastMd5;
    private volatile String localUsername;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FC-UserCache");
        t.setDaemon(true);
        return t;
    });

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static FastClientUserCache getInstance() {
        if (instance != null) return instance;
        Class<FastClientUserCache> clazz = FastClientUserCache.class;
        synchronized (FastClientUserCache.class) {
            if (instance != null) return instance;
            instance = new FastClientUserCache();
            // ** MonitorExit[var0] (shouldn't be in output)
            return instance;
        }
    }

    private FastClientUserCache() {
        FastClientHUDClient.LOGGER.info("[FCUserCache] Initialised \u2014 fetching immediately");
        this.scheduler.schedule(this::refreshAndReschedule, 0L, TimeUnit.SECONDS);
    }

    public boolean isFastClientUser(String username) {
        return username != null && this.activeUsers.contains(username.toLowerCase(Locale.ROOT));
    }

    public void pingServer(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        this.localUsername = username;
        FastClientHUDClient.LOGGER.info("[FCUserCache] Pinging server for user: {}", (Object)username);
        this.scheduler.execute(() -> {
            try {
                String body = "{\"username\":\"" + username + "\"}";
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.fastclient.net/api/fastclient/ping")).timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                FastClientHUDClient.LOGGER.info("[FCUserCache] Ping response: {}", (Object)response.statusCode());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    this.addActiveUser(username);
                }
            }
            catch (Exception e) {
                FastClientHUDClient.LOGGER.warn("[FCUserCache] Ping failed: {}", (Object)e.getMessage());
            }
        });
    }

    public void onDisconnect() {
        FastClientHUDClient.LOGGER.info("[FCUserCache] Disconnected \u2014 clearing {} cached users", (Object)this.activeUsers.size());
        this.activeUsers = Collections.emptySet();
        this.lastMd5 = null;
    }

    private void refreshAndReschedule() {
        this.refresh();
        long nextInterval = 60L + (long)(Math.random() * 10.0 * 2.0) - 10L;
        FastClientHUDClient.LOGGER.info("[FCUserCache] Next refresh in {}s", (Object)nextInterval);
        this.scheduler.schedule(this::refreshAndReschedule, nextInterval, TimeUnit.SECONDS);
    }

    private void refresh() {
        block13: {
            try {
                HttpRequest md5Request = HttpRequest.newBuilder().uri(URI.create(MD5_URL)).timeout(REQUEST_TIMEOUT).GET().build();
                HttpResponse<String> md5Response = this.httpClient.send(md5Request, HttpResponse.BodyHandlers.ofString());
                if (md5Response.statusCode() != 200) {
                    FastClientHUDClient.LOGGER.warn("[FCUserCache] MD5 fetch returned {}", (Object)md5Response.statusCode());
                    return;
                }
                String remoteMd5 = md5Response.body().trim();
                if (remoteMd5.equals(this.lastMd5)) {
                    FastClientHUDClient.LOGGER.info("[FCUserCache] MD5 unchanged ({}) \u2014 skipping manifest fetch", (Object)remoteMd5);
                    return;
                }
                FastClientHUDClient.LOGGER.info("[FCUserCache] MD5 changed ({} -> {}) \u2014 fetching manifest", (Object)this.lastMd5, (Object)remoteMd5);
                HttpRequest manifestRequest = HttpRequest.newBuilder().uri(URI.create(MANIFEST_URL)).timeout(REQUEST_TIMEOUT).GET().build();
                HttpResponse<String> response = this.httpClient.send(manifestRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    HashSet<String> users = new HashSet<String>();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream)new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));){
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            users.add(line.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    this.lastMd5 = remoteMd5;
                    this.activeUsers = users;
                    FastClientHUDClient.LOGGER.info("[FCUserCache] Loaded {} active users", (Object)users.size());
                    String local = this.localUsername;
                    if (local != null && !users.contains(local.toLowerCase(Locale.ROOT))) {
                        FastClientHUDClient.LOGGER.info("[FCUserCache] {} not in active users \u2014 re-pinging", (Object)local);
                        this.pingServer(local);
                    }
                    break block13;
                }
                FastClientHUDClient.LOGGER.warn("[FCUserCache] Unexpected status {} from manifest", (Object)response.statusCode());
            }
            catch (Exception e) {
                FastClientHUDClient.LOGGER.warn("[FCUserCache] Refresh failed: {}", (Object)e.getMessage());
            }
        }
    }

    private synchronized void addActiveUser(String username) {
        HashSet<String> users = new HashSet<String>(this.activeUsers);
        users.add(username.toLowerCase(Locale.ROOT));
        this.activeUsers = Collections.unmodifiableSet(users);
    }
}

