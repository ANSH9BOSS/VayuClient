/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.minecraft.client.Minecraft
 */
package com.vayuclient.hud.store;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.store.StoreCosmetic;
import net.minecraft.client.Minecraft;

public class MarketplaceClient {
    private static final String DEFAULT_BASE_URL = "http://localhost:8787";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8L);
    private static volatile MarketplaceClient instance;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;

    public MarketplaceClient() {
        this(DEFAULT_BASE_URL);
    }

    public MarketplaceClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FC-Marketplace");
            t.setDaemon(true);
            return t;
        });
        this.gson = new Gson();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static MarketplaceClient getInstance() {
        if (instance != null) return instance;
        Class<MarketplaceClient> clazz = MarketplaceClient.class;
        synchronized (MarketplaceClient.class) {
            if (instance != null) return instance;
            instance = new MarketplaceClient();
            // ** MonitorExit[var0] (shouldn't be in output)
            return instance;
        }
    }

    public CompletableFuture<BootstrapResult> bootstrap(String username) {
        return this.supplyAsync(() -> {
            JsonObject json = this.get("/api/store/bootstrap?username=" + this.urlEncode(username));
            JsonArray catalogArray = json.getAsJsonArray("catalog");
            List<StoreCosmetic> catalog = this.parseCatalog(catalogArray);
            JsonObject userJson = json.getAsJsonObject("user");
            UserState userState = this.parseUserState(userJson);
            int dailyClaimAmount = json.has("dailyCoinClaimAmount") ? json.get("dailyCoinClaimAmount").getAsInt() : 100;
            return new BootstrapResult(catalog, userState, dailyClaimAmount);
        });
    }

    public CompletableFuture<ApiResult> claimDaily(String username) {
        return this.supplyAsync(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("username", username);
            JsonObject json = this.post("/api/store/claim", request);
            return this.parseApiResult(json);
        });
    }

    public CompletableFuture<ApiResult> purchase(String username, String itemId) {
        return this.supplyAsync(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("username", username);
            request.addProperty("itemId", itemId);
            JsonObject json = this.post("/api/store/purchase", request);
            return this.parseApiResult(json);
        });
    }

    public CompletableFuture<ApiResult> equip(String username, String itemId) {
        return this.supplyAsync(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("username", username);
            request.addProperty("itemId", itemId);
            JsonObject json = this.post("/api/store/equip", request);
            return this.parseApiResult(json);
        });
    }

    public CompletableFuture<ApiResult> unequip(String username, String itemId) {
        return this.supplyAsync(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("username", username);
            request.addProperty("itemId", itemId);
            JsonObject json = this.post("/api/store/unequip", request);
            return this.parseApiResult(json);
        });
    }

    public CompletableFuture<ApiResult> removeAll(String username) {
        return this.supplyAsync(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("username", username);
            JsonObject json = this.post("/api/store/remove-all", request);
            return this.parseApiResult(json);
        });
    }

    public CompletableFuture<List<PublicCosmeticProfile>> publicEquipped(Collection<String> usernames) {
        return this.supplyAsync(() -> {
            if (usernames.isEmpty()) {
                return List.of();
            }
            StringJoiner joiner = new StringJoiner(",");
            for (String username : usernames) {
                if (username == null || username.isBlank()) continue;
                joiner.add(username);
            }
            JsonObject json = this.get("/api/store/public-equipped?usernames=" + this.urlEncode(joiner.toString()));
            return this.parsePublicProfiles(json);
        });
    }

    private JsonObject get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + path)).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MarketplaceException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return this.gson.fromJson((String)response.body(), JsonObject.class);
    }

    private JsonObject post(String path, JsonObject body) throws Exception {
        String bodyStr = this.gson.toJson((JsonElement)body);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + path)).timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(bodyStr)).build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MarketplaceException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return this.gson.fromJson((String)response.body(), JsonObject.class);
    }

    private <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        CompletableFuture future = new CompletableFuture();
        this.executor.execute(() -> {
            try {
                future.complete(supplier.get());
            }
            catch (Exception e) {
                VayuHUDClient.LOGGER.warn("[MarketplaceClient] Request failed: {}", (Object)e.getMessage());
                future.completeExceptionally(new MarketplaceException(e.getMessage(), e));
            }
        });
        return future;
    }

    private List<StoreCosmetic> parseCatalog(JsonArray catalogArray) {
        ArrayList<StoreCosmetic> items = new ArrayList<StoreCosmetic>();
        for (JsonElement element : catalogArray) {
            JsonObject obj = element.getAsJsonObject();
            try {
                items.add(StoreCosmetic.fromJson(obj));
            }
            catch (Exception e) {
                VayuHUDClient.LOGGER.warn("[MarketplaceClient] Skipping invalid catalog item: {}", (Object)e.getMessage());
            }
        }
        return items;
    }

    private UserState parseUserState(JsonObject userJson) {
        int coinBalance = userJson.get("coinBalance").getAsInt();
        List<String> owned = this.jsonArrayToList(userJson.getAsJsonArray("owned"));
        List<String> equipped = this.jsonArrayToList(userJson.getAsJsonArray("equipped"));
        long lastCoinClaimEpochDay = userJson.has("lastCoinClaimEpochDay") ? userJson.get("lastCoinClaimEpochDay").getAsLong() : Long.MIN_VALUE;
        return new UserState(coinBalance, owned, equipped, lastCoinClaimEpochDay);
    }

    private ApiResult parseApiResult(JsonObject json) {
        String message = json.has("message") ? json.get("message").getAsString() : "";
        JsonObject userJson = json.getAsJsonObject("user");
        UserState userState = userJson != null ? this.parseUserState(userJson) : null;
        return new ApiResult(userState, message);
    }

    private List<String> jsonArrayToList(JsonArray array) {
        ArrayList<String> list = new ArrayList<String>();
        if (array == null) {
            return list;
        }
        for (JsonElement e : array) {
            list.add(e.getAsString());
        }
        return list;
    }

    private List<PublicCosmeticProfile> parsePublicProfiles(JsonObject json) {
        JsonArray profilesArray = json.getAsJsonArray("profiles");
        ArrayList<PublicCosmeticProfile> profiles = new ArrayList<PublicCosmeticProfile>();
        if (profilesArray == null) {
            return profiles;
        }
        for (JsonElement element : profilesArray) {
            JsonObject profile = element.getAsJsonObject();
            String username = profile.has("username") ? profile.get("username").getAsString() : "";
            profiles.add(new PublicCosmeticProfile(username, this.jsonArrayToList(profile.getAsJsonArray("equipped"))));
        }
        return profiles;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() != null && client.getUser().getName() != null) {
            return client.getUser().getName();
        }
        return null;
    }

    public CompletableFuture<BootstrapResult> bootstrap() {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.bootstrap(name);
    }

    public CompletableFuture<ApiResult> claimDaily() {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.claimDaily(name);
    }

    public CompletableFuture<ApiResult> purchase(String itemId) {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.purchase(name, itemId);
    }

    public CompletableFuture<ApiResult> equip(String itemId) {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.equip(name, itemId);
    }

    public CompletableFuture<ApiResult> unequip(String itemId) {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.unequip(name, itemId);
    }

    public CompletableFuture<ApiResult> removeAll() {
        String name = this.getPlayerName();
        if (name == null) {
            return CompletableFuture.failedFuture(new MarketplaceException("Player name not available"));
        }
        return this.removeAll(name);
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    @FunctionalInterface
    private static interface ThrowingSupplier<T> {
        public T get() throws Exception;
    }

    public static class MarketplaceException
    extends RuntimeException {
        public MarketplaceException(String message) {
            super(message);
        }

        public MarketplaceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record UserState(int coinBalance, List<String> ownedItemIds, List<String> equippedItemIds, long lastCoinClaimEpochDay) {
    }

    public record ApiResult(UserState userState, String message) {
    }

    public record PublicCosmeticProfile(String username, List<String> equippedItemIds) {
    }

    public record BootstrapResult(List<StoreCosmetic> catalog, UserState userState, int dailyCoinClaimAmount) {
    }
}

