package com.vayuclient.hud.social;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.network.VayuUserCache;
import com.vayuclient.hud.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class VayuSocialManager {
    public static final String BACKEND_URL = "https://vayu.rencloud.online";
    private static final Duration TIMEOUT = Duration.ofSeconds(4L);
    private static volatile VayuSocialManager instance;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Vayu-SocialManager");
        t.setDaemon(true);
        return t;
    });

    private List<FriendInfo> friends = Collections.emptyList();
    private List<FriendRequestInfo> incomingRequests = Collections.emptyList();
    private List<FriendRequestInfo> outgoingRequests = Collections.emptyList();
    private List<String> activePlayers = Collections.emptyList();
    private String lastKnownServer = "";
    private long lastInviteTimestamp = 0L;

    public static record FriendInfo(
        String username,
        boolean isOnline,
        String currentServerAddress,
        String currentServerName,
        String accountType
    ) {}

    public static record FriendRequestInfo(
        String id,
        String fromUsername,
        String toUsername,
        String createdAt
    ) {}

    public static VayuSocialManager getInstance() {
        if (instance != null) return instance;
        synchronized (VayuSocialManager.class) {
            if (instance != null) return instance;
            instance = new VayuSocialManager();
            return instance;
        }
    }

    private VayuSocialManager() {
        // Periodic sync every 8 seconds
        this.executor.scheduleAtFixedRate(this::pollSocialData, 1L, 8L, TimeUnit.SECONDS);
    }

    public List<FriendInfo> getFriends() {
        return this.friends;
    }

    public List<FriendRequestInfo> getIncomingRequests() {
        return this.incomingRequests;
    }

    public List<FriendRequestInfo> getOutgoingRequests() {
        return this.outgoingRequests;
    }

    public List<String> getActivePlayers() {
        return this.activePlayers;
    }

    public String getLocalUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return PlayerUtils.getProfileName(mc.player.getGameProfile());
        }
        if (mc.getUser() != null) {
            return mc.getUser().getName();
        }
        return "Player";
    }

    public void pollSocialData() {
        String local = getLocalUsername();
        if (local == null || local.equalsIgnoreCase("Player") || local.isBlank()) {
            return;
        }

        // 1. Sync Current Server
        syncCurrentServer(local);

        // 2. Fetch Friends
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/v1/friends?username=" + local))
                .timeout(TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                this.friends = parseFriendsJson(resp.body());
            }
        } catch (Exception ignored) {}

        // 3. Fetch Requests
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/v1/friends/requests?username=" + local))
                .timeout(TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parseRequestsJson(resp.body());
            }
        } catch (Exception ignored) {}

        // 4. Fetch Active Online Vayu Players
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/v1/presence/active-users"))
                .timeout(TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                this.activePlayers = parseActivePlayersJson(resp.body(), local);
            }
        } catch (Exception ignored) {}
    }

    private void syncCurrentServer(String localUser) {
        Minecraft mc = Minecraft.getInstance();
        String currentServer = "";
        String serverName = "";
        if (mc.getCurrentServer() != null) {
            currentServer = mc.getCurrentServer().ip;
            serverName = mc.getCurrentServer().name;
        } else if (mc.hasSingleplayerServer()) {
            currentServer = "Singleplayer";
            serverName = "Singleplayer";
        }

        if (!currentServer.equals(this.lastKnownServer)) {
            this.lastKnownServer = currentServer;
            try {
                String payload = String.format("{\"username\":\"%s\",\"serverAddress\":\"%s\",\"serverName\":\"%s\"}",
                    localUser, currentServer, serverName);
                HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/server-status"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                this.http.send(post, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        }
    }

    public void sendFriendRequest(String targetUsername, Consumer<String> callback) {
        this.executor.execute(() -> {
            try {
                String local = getLocalUsername();
                String payload = String.format("{\"requester\":\"%s\",\"addressee\":\"%s\"}", local, targetUsername.trim());
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/request"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
                String msg = resp.statusCode() == 200 ? "Friend request sent to " + targetUsername : "Could not send request.";
                if (callback != null) callback.accept(msg);
                pollSocialData();
            } catch (Exception e) {
                if (callback != null) callback.accept("Connection error: " + e.getMessage());
            }
        });
    }

    public void respondFriendRequest(String requesterUsername, boolean accept, Consumer<String> callback) {
        this.executor.execute(() -> {
            try {
                String local = getLocalUsername();
                String payload = String.format("{\"addressee\":\"%s\",\"requester\":\"%s\",\"accept\":%b}",
                    local, requesterUsername.trim(), accept);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/respond"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
                String msg = accept ? "Accepted " + requesterUsername : "Declined " + requesterUsername;
                if (callback != null) callback.accept(msg);
                pollSocialData();
            } catch (Exception e) {
                if (callback != null) callback.accept("Error: " + e.getMessage());
            }
        });
    }

    public void removeFriend(String targetUsername, Consumer<String> callback) {
        this.executor.execute(() -> {
            try {
                String local = getLocalUsername();
                String payload = String.format("{\"userA\":\"%s\",\"userB\":\"%s\"}", local, targetUsername.trim());
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/remove"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                this.http.send(req, HttpResponse.BodyHandlers.discarding());
                if (callback != null) callback.accept("Removed " + targetUsername);
                pollSocialData();
            } catch (Exception e) {
                if (callback != null) callback.accept("Error: " + e.getMessage());
            }
        });
    }

    public void sendServerInvite(String targetUsername, Consumer<String> callback) {
        this.executor.execute(() -> {
            try {
                String local = getLocalUsername();
                Minecraft mc = Minecraft.getInstance();
                String serverIp = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "";
                String serverName = mc.getCurrentServer() != null ? mc.getCurrentServer().name : "Multiplayer Server";

                if (serverIp.isBlank()) {
                    if (callback != null) callback.accept("You must be in a multiplayer server to invite.");
                    return;
                }

                String payload = String.format("{\"fromUser\":\"%s\",\"toUser\":\"%s\",\"serverAddress\":\"%s\",\"serverName\":\"%s\"}",
                    local, targetUsername.trim(), serverIp, serverName);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/invite"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
                String msg = resp.statusCode() == 200 ? "Invite sent to " + targetUsername : "Could not send invite.";
                if (callback != null) callback.accept(msg);
            } catch (Exception e) {
                if (callback != null) callback.accept("Error: " + e.getMessage());
            }
        });
    }

    public void shareCoordinates(String targetFriendOrNull, Consumer<String> callback) {
        this.executor.execute(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    if (callback != null) callback.accept("You must be in a world/server to share coordinates.");
                    return;
                }

                double x = Math.round(mc.player.getX() * 10.0) / 10.0;
                double y = Math.round(mc.player.getY() * 10.0) / 10.0;
                double z = Math.round(mc.player.getZ() * 10.0) / 10.0;

                String dimension = "Overworld";
                if (mc.level != null) {
                    try {
                        String dimKey = mc.level.dimension().identifier().getPath().toLowerCase(Locale.ROOT);
                        if (dimKey.contains("nether")) dimension = "Nether";
                        else if (dimKey.contains("end")) dimension = "The End";
                        else dimension = "Overworld";
                    } catch (Exception ignored) {
                        dimension = "Overworld";
                    }
                }

                String local = getLocalUsername();
                String serverIp = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "";

                String targetJson = targetFriendOrNull != null ? "\"" + targetFriendOrNull.trim() + "\"" : "null";
                String payload = String.format(Locale.ROOT,
                    "{\"fromUser\":\"%s\",\"targetFriend\":%s,\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"dimension\":\"%s\",\"serverAddress\":\"%s\"}",
                    local, targetJson, x, y, z, dimension, serverIp);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/friends/share-coords"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());

                String targetDesc = targetFriendOrNull != null ? targetFriendOrNull : "all friends on server";
                String msg = resp.statusCode() == 200
                    ? String.format("Shared coords (%s: %.0f, %.0f, %.0f) with %s", dimension, x, y, z, targetDesc)
                    : "Could not share coordinates.";

                if (callback != null) callback.accept(msg);

                // Echo locally in client chat
                final String fDim = dimension;
                final double fx = x;
                final double fy = y;
                final double fz = z;
                final String fTarget = targetDesc;
                Minecraft.getInstance().execute(() -> {
                    try {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage((Component)Component.literal(
                                String.format("§b[VayuClient] §7Shared coordinates with §f%s§7: §eX: %.0f, Y: %.0f, Z: %.0f §8(%s)",
                                    fTarget, fx, fy, fz, fDim)));
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                if (callback != null) callback.accept("Error: " + e.getMessage());
            }
        });
    }

    public static void joinServer(Screen parent, String serverIp) {
        if (serverIp == null || serverIp.isBlank() || serverIp.equalsIgnoreCase("Singleplayer")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ServerAddress address = ServerAddress.parseString(serverIp);
            ServerData data = new ServerData("Friend's Server", serverIp, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(parent != null ? parent : new TitleScreen(), mc, address, data, false, null);
        } catch (Exception e) {
            VayuHUDClient.LOGGER.error("Failed to connect to friend's server {}: {}", serverIp, e.getMessage());
        }
    }

    public static void showToast(String title, String message) {
        Minecraft.getInstance().execute(() -> {
            try {
                SystemToast.add(
                    Minecraft.getInstance().gui.toastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title),
                    Component.literal(message)
                );
            } catch (Exception ignored) {}
        });
    }

    // ── JSON Parsers ────────────────────────────────────────────────────────────

    private List<FriendInfo> parseFriendsJson(String json) {
        List<FriendInfo> list = new ArrayList<>();
        int friendsIdx = json.indexOf("\"friends\":");
        if (friendsIdx == -1) return list;
        int start = json.indexOf('[', friendsIdx);
        int end = json.lastIndexOf(']');
        if (start == -1 || end <= start) return list;

        String arrayContent = json.substring(start + 1, end);
        String[] objects = arrayContent.split("\\},\\{");
        for (String obj : objects) {
            String uname = extractValue(obj, "username");
            boolean online = "true".equalsIgnoreCase(extractValue(obj, "isOnline"));
            String serverAddr = extractValue(obj, "currentServerAddress");
            String serverName = extractValue(obj, "currentServerName");
            String atype = extractValue(obj, "accountType");
            if (!uname.isEmpty()) {
                list.add(new FriendInfo(uname, online, serverAddr, serverName, atype));
            }
        }
        return list;
    }

    private void parseRequestsJson(String json) {
        List<FriendRequestInfo> in = new ArrayList<>();
        List<FriendRequestInfo> out = new ArrayList<>();

        int incIdx = json.indexOf("\"incoming\":");
        if (incIdx != -1) {
            int incStart = json.indexOf('[', incIdx);
            int incEnd = json.indexOf(']', incStart);
            if (incStart != -1 && incEnd > incStart) {
                String sub = json.substring(incStart + 1, incEnd);
                for (String obj : sub.split("\\},\\{")) {
                    String id = extractValue(obj, "id");
                    String from = extractValue(obj, "fromUsername");
                    String to = extractValue(obj, "toUsername");
                    String date = extractValue(obj, "createdAt");
                    if (!from.isEmpty()) in.add(new FriendRequestInfo(id, from, to, date));
                }
            }
        }

        int outIdx = json.indexOf("\"outgoing\":");
        if (outIdx != -1) {
            int outStart = json.indexOf('[', outIdx);
            int outEnd = json.indexOf(']', outStart);
            if (outStart != -1 && outEnd > outStart) {
                String sub = json.substring(outStart + 1, outEnd);
                for (String obj : sub.split("\\},\\{")) {
                    String id = extractValue(obj, "id");
                    String from = extractValue(obj, "fromUsername");
                    String to = extractValue(obj, "toUsername");
                    String date = extractValue(obj, "createdAt");
                    if (!to.isEmpty()) out.add(new FriendRequestInfo(id, from, to, date));
                }
            }
        }

        this.incomingRequests = in;
        this.outgoingRequests = out;
    }

    private List<String> parseActivePlayersJson(String json, String localUser) {
        List<String> list = new ArrayList<>();
        String clean = json.replace("[", "").replace("]", "").replace("\"", "");
        for (String item : clean.split(",")) {
            String u = item.trim();
            if (!u.isEmpty() && !u.equalsIgnoreCase(localUser)) {
                list.add(u);
            }
        }
        return list;
    }

    private static String extractValue(String block, String key) {
        String search = "\"" + key + "\":";
        int idx = block.indexOf(search);
        if (idx == -1) return "";
        int valStart = idx + search.length();
        while (valStart < block.length() && (block.charAt(valStart) == ' ' || block.charAt(valStart) == '"')) {
            valStart++;
        }
        int valEnd = valStart;
        while (valEnd < block.length() && block.charAt(valEnd) != '"' && block.charAt(valEnd) != ',' && block.charAt(valEnd) != '}') {
            valEnd++;
        }
        if (valEnd > valStart) {
            String res = block.substring(valStart, valEnd).trim();
            if (res.equalsIgnoreCase("null")) return "";
            return res;
        }
        return "";
    }
}
