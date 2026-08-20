package com.vayuclient.hud.network;

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
import com.vayuclient.hud.VayuHUDClient;

public final class VayuUserCache {
    private static final String API_ACTIVE_USERS_URL = "https://vayu.rencloud.online/api/v1/presence/active-users";
    private static final String FALLBACK_MANIFEST_URL = "https://files.vayuclient.net/vayuclient/active-users.txt";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5L);
    private static final long REFRESH_INTERVAL_SEC = 20L;
    private static volatile VayuUserCache instance;
    private volatile Set<String> activeUsers = Collections.emptySet();
    private volatile String localUsername;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Vayu-UserCache");
        t.setDaemon(true);
        return t;
    });

    public static VayuUserCache getInstance() {
        if (instance != null) return instance;
        synchronized (VayuUserCache.class) {
            if (instance != null) return instance;
            instance = new VayuUserCache();
            return instance;
        }
    }

    private VayuUserCache() {
        VayuHUDClient.LOGGER.info("[VayuUserCache] Initialised — fetching active users from vayu.rencloud.online");
        this.scheduler.scheduleAtFixedRate(this::refresh, 0L, REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public boolean isVayuClientUser(String username) {
        if (username == null) return false;
        String clean = username.trim().toLowerCase(Locale.ROOT);
        return this.activeUsers.contains(clean);
    }

    public Set<String> getActiveUsers() {
        return this.activeUsers;
    }

    public void pingServer(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        this.localUsername = username;
        this.addActiveUser(username);
        this.scheduler.execute(() -> {
            try {
                String body = "{\"username\":\"" + username + "\",\"serverAddress\":\"\",\"serverName\":\"\"}";
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://vayu.rencloud.online/api/v1/friends/server-status"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                this.httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }

    public void onDisconnect() {
        this.activeUsers = Collections.emptySet();
    }

    private void refresh() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_ACTIVE_USERS_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                HashSet<String> users = new HashSet<>();
                // Parse JSON array of strings e.g. ["user1","user2"] or fallback newline
                if (body.startsWith("[") && body.endsWith("]")) {
                    String content = body.substring(1, body.length() - 1);
                    String[] tokens = content.split(",");
                    for (String token : tokens) {
                        String clean = token.trim().replace("\"", "").toLowerCase(Locale.ROOT);
                        if (!clean.isEmpty()) users.add(clean);
                    }
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isBlank()) users.add(line.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                if (this.localUsername != null) {
                    users.add(this.localUsername.toLowerCase(Locale.ROOT));
                }
                this.activeUsers = Collections.unmodifiableSet(users);
                return;
            }
        } catch (Exception e) {
            // Non-fatal, fallback to previous cache or fallback url
        }

        // Fallback attempt
        try {
            HttpRequest manifestRequest = HttpRequest.newBuilder()
                .uri(URI.create(FALLBACK_MANIFEST_URL))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> response = this.httpClient.send(manifestRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                HashSet<String> users = new HashSet<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) users.add(line.trim().toLowerCase(Locale.ROOT));
                    }
                }
                if (this.localUsername != null) {
                    users.add(this.localUsername.toLowerCase(Locale.ROOT));
                }
                this.activeUsers = Collections.unmodifiableSet(users);
            }
        } catch (Exception ignored) {}
    }

    public synchronized void addActiveUser(String username) {
        if (username == null || username.isBlank()) return;
        HashSet<String> users = new HashSet<>(this.activeUsers);
        users.add(username.toLowerCase(Locale.ROOT));
        this.activeUsers = Collections.unmodifiableSet(users);
    }
}
