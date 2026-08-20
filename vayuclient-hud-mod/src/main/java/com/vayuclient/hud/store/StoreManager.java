/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.Minecraft
 */
package com.vayuclient.hud.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.store.CosmeticCategory;
import com.vayuclient.hud.store.CosmeticRuntimeAdapter;
import com.vayuclient.hud.store.MarketplaceClient;
import com.vayuclient.hud.store.RemoteCosmeticResolver;
import com.vayuclient.hud.store.StoreCatalog;
import com.vayuclient.hud.store.StoreCosmetic;
import com.vayuclient.hud.store.StoreState;
import net.minecraft.client.Minecraft;

public class StoreManager {
    public static final int MAX_CHAT_TAGS = 3;
    private static final int DEFAULT_DAILY_COIN_CLAIM = 100;
    private static final long REMOTE_COSMETIC_REFRESH_INTERVAL_MS = 5000L;
    private static final int LOCAL_COSMETIC_REFRESH_TICKS = 80;
    private static final String CHAT_TAG_SLOT = "nametag";
    private static StoreManager instance;
    private final StoreCatalog catalog;
    private final StoreState state;
    private final CosmeticRuntimeAdapter runtimeAdapter;
    private final MarketplaceClient marketplaceClient;
    private final RemoteCosmeticResolver remoteCosmeticResolver;
    private final Path configPath;
    private final Gson gson;
    private volatile boolean catalogLoaded;
    private volatile boolean bootstrapAttempted;
    private volatile int dailyCoinClaimAmount = 100;
    private long nextRemoteCosmeticRefreshMillis;
    private int localCosmeticRefreshTicks;

    public StoreManager() {
        instance = this;
        this.catalog = new StoreCatalog();
        this.state = new StoreState();
        this.runtimeAdapter = new CosmeticRuntimeAdapter(){
            @Override
            public void applyCosmetics(List<StoreCosmetic> equippedCosmetics) {
            }

            @Override
            public void clearAllCosmetics() {
            }
        };
        this.marketplaceClient = MarketplaceClient.getInstance();
        this.remoteCosmeticResolver = new RemoteCosmeticResolver(this.marketplaceClient, this.catalog, this.runtimeAdapter);
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud-store.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.populateFallbackCatalog();
        this.loadState();
        this.applyRuntime();
        VayuHUDClient.LOGGER.info("[StoreManager] Initialized store cache: catalog={} owned={} equipped={} api={}", new Object[]{this.catalog.all().size(), this.state.getOwnedItemIds().size(), this.state.getEquippedItemIds().size(), this.marketplaceClient.getBaseUrl()});
    }

    public static StoreManager getInstance() {
        if (instance == null) {
            instance = new StoreManager();
        }
        return instance;
    }

    public CosmeticRuntimeAdapter getRuntimeAdapter() {
        return this.runtimeAdapter;
    }

    public MarketplaceClient getMarketplaceClient() {
        return this.marketplaceClient;
    }

    public List<StoreCosmetic> getCatalog() {
        return this.catalog.all();
    }

    public boolean isCatalogLoaded() {
        return this.catalogLoaded;
    }

    public StoreCosmetic getCosmetic(String id) {
        return this.catalog.find(id);
    }

    public List<StoreCosmetic> getOwnedCosmetics() {
        return this.catalog.owned(this.state);
    }

    public List<StoreCosmetic> getEquippedCosmetics() {
        return this.catalog.equipped(this.state);
    }

    public List<StoreCosmetic> getEquippedChatTags() {
        ArrayList<StoreCosmetic> tags = new ArrayList<StoreCosmetic>();
        for (String id : this.state.getEquippedItemIds()) {
            StoreCosmetic cosmetic = this.getCosmetic(id);
            if (!StoreManager.isChatTag(cosmetic)) continue;
            tags.add(cosmetic);
            if (tags.size() < 3) continue;
            break;
        }
        return List.copyOf(tags);
    }

    public List<StoreCosmetic> getCosmeticsByCategory(CosmeticCategory category) {
        return this.catalog.byCategory(category);
    }

    public List<StoreCosmetic> searchCosmetics(String query, CosmeticCategory category, boolean ownedOnly) {
        return this.catalog.search(query, category, ownedOnly, this.state);
    }

    public StoreState getState() {
        return this.state;
    }

    public int getDailyCoinClaimAmount() {
        return this.dailyCoinClaimAmount;
    }

    public void bootstrapFromBackend() {
        if (this.bootstrapAttempted) {
            return;
        }
        this.bootstrapAttempted = true;
        String username = this.getClientUsername();
        if (username == null) {
            VayuHUDClient.LOGGER.warn("[StoreManager] No username, skipping bootstrap");
            return;
        }
        VayuHUDClient.LOGGER.info("[StoreManager] Bootstrapping marketplace state for {}", (Object)username);
        this.marketplaceClient.bootstrap(username).thenAccept(result -> this.runOnClientThread(() -> {
            try {
                if (result.catalog() != null && !result.catalog().isEmpty()) {
                    this.catalog.replaceAll(result.catalog());
                    this.ensureLocalCategoryFillers();
                    VayuHUDClient.LOGGER.info("[StoreManager] Catalog replaced from backend: {} items", (Object)result.catalog().size());
                }
                this.catalogLoaded = true;
                this.dailyCoinClaimAmount = result.dailyCoinClaimAmount();
                MarketplaceClient.UserState userState = result.userState();
                this.state.setCoinBalance(userState.coinBalance());
                this.state.setLastCoinClaimEpochDay(userState.lastCoinClaimEpochDay());
                this.syncOwnedAndEquipped(userState.ownedItemIds(), userState.equippedItemIds());
                this.saveState();
                this.applyRuntime();
                VayuHUDClient.LOGGER.info("[StoreManager] Bootstrap complete \u2014 {} items, {} coins", (Object)result.catalog().size(), (Object)userState.coinBalance());
            }
            catch (Exception e) {
                VayuHUDClient.LOGGER.warn("[StoreManager] Bootstrap apply failed: {}", (Object)e.getMessage());
            }
        })).exceptionally(ex -> {
            this.runOnClientThread(() -> {
                VayuHUDClient.LOGGER.warn("[StoreManager] Bootstrap failed, using local state: {}", (Object)(ex != null ? ex.getMessage() : "unknown"));
                this.catalogLoaded = true;
            });
            return null;
        });
    }

    public boolean purchase(String id) {
        StoreCosmetic cosmetic = this.getCosmetic(id);
        if (cosmetic == null) {
            VayuHUDClient.LOGGER.warn("[StoreManager] Purchase ignored for unknown item {}", (Object)id);
            return false;
        }
        if (this.state.owns(id)) {
            VayuHUDClient.LOGGER.info("[StoreManager] Purchase skipped for already-owned item {}", (Object)id);
            return false;
        }
        String username = this.getClientUsername();
        if (username == null) {
            VayuHUDClient.LOGGER.info("[StoreManager] Offline purchase fallback for item {}", (Object)id);
            return this.purchaseLocal(id, cosmetic);
        }
        if (!this.state.spendCoins(cosmetic.getPrice())) {
            VayuHUDClient.LOGGER.info("[StoreManager] Purchase rejected locally for {}: price={} balance={}", new Object[]{id, cosmetic.getPrice(), this.state.getCoinBalance()});
            return false;
        }
        this.state.addOwned(id);
        this.saveState();
        VayuHUDClient.LOGGER.info("[StoreManager] Purchase queued: user={} item={} price={}", new Object[]{username, id, cosmetic.getPrice()});
        this.marketplaceClient.purchase(username, id).thenAccept(result -> this.runOnClientThread(() -> {
            if (result.userState() != null) {
                this.syncUserState(result.userState());
                this.saveState();
                this.applyRuntime();
                VayuHUDClient.LOGGER.info("[StoreManager] Purchase reconciled: item={} balance={} owned={}", new Object[]{id, this.state.getCoinBalance(), this.state.getOwnedItemIds().size()});
            }
        })).exceptionally(ex -> {
            this.runOnClientThread(() -> VayuHUDClient.LOGGER.warn("[StoreManager] Backend purchase failed: {}", (Object)(ex != null ? ex.getMessage() : "unknown")));
            return null;
        });
        return true;
    }

    public void equip(String id) {
        StoreCosmetic cosmetic = this.getCosmetic(id);
        if (cosmetic == null || !this.state.owns(id)) {
            VayuHUDClient.LOGGER.info("[StoreManager] Equip ignored for {}: known={} owned={}", new Object[]{id, cosmetic != null, this.state.owns(id)});
            return;
        }
        List<StoreCosmetic> equipped = this.getEquippedCosmetics();
        for (StoreCosmetic equippedCosmetic : equipped) {
            if (equippedCosmetic.getId().equals(id)) {
                VayuHUDClient.LOGGER.info("[StoreManager] Equip skipped for already-equipped item {}", (Object)id);
                this.applyRuntime();
                return;
            }
            if (!cosmetic.conflictsWith(equippedCosmetic)) continue;
            this.state.unequip(equippedCosmetic.getId());
        }
        this.trimChatTagsForNextEquip(cosmetic);
        this.state.equip(id);
        this.saveState();
        this.applyRuntime();
        String username = this.getClientUsername();
        VayuHUDClient.LOGGER.info("[StoreManager] Equipped locally: user={} item={} slot={} equipped={}", new Object[]{username == null ? "offline" : username, id, cosmetic.getSlot(), this.state.getEquippedItemIds().size()});
        if (username != null) {
            this.marketplaceClient.equip(username, id).thenAccept(result -> this.runOnClientThread(() -> {
                if (result.userState() != null) {
                    this.syncEquipped(result.userState().equippedItemIds());
                    this.saveState();
                    this.applyRuntime();
                    VayuHUDClient.LOGGER.info("[StoreManager] Equip reconciled: item={} equipped={}", (Object)id, (Object)this.state.getEquippedItemIds().size());
                }
            })).exceptionally(ex -> {
                this.runOnClientThread(() -> VayuHUDClient.LOGGER.warn("[StoreManager] Backend equip failed: {}", (Object)(ex != null ? ex.getMessage() : "unknown")));
                return null;
            });
        }
    }

    public void unequip(String id) {
        this.state.unequip(id);
        this.saveState();
        this.applyRuntime();
        String username = this.getClientUsername();
        VayuHUDClient.LOGGER.info("[StoreManager] Unequipped locally: user={} item={}", (Object)(username == null ? "offline" : username), (Object)id);
        if (username != null) {
            this.marketplaceClient.unequip(username, id).thenAccept(result -> this.runOnClientThread(() -> {
                if (result.userState() != null) {
                    this.syncEquipped(result.userState().equippedItemIds());
                    this.saveState();
                    this.applyRuntime();
                    VayuHUDClient.LOGGER.info("[StoreManager] Unequip reconciled: item={} equipped={}", (Object)id, (Object)this.state.getEquippedItemIds().size());
                }
            })).exceptionally(ex -> {
                this.runOnClientThread(() -> VayuHUDClient.LOGGER.warn("[StoreManager] Backend unequip failed: {}", (Object)(ex != null ? ex.getMessage() : "unknown")));
                return null;
            });
        }
    }

    public void removeAll() {
        if (this.state.getEquippedItemIds().isEmpty()) {
            return;
        }
        int removed = this.state.getEquippedItemIds().size();
        this.state.removeAllEquipped();
        this.saveState();
        this.applyRuntime();
        String username = this.getClientUsername();
        VayuHUDClient.LOGGER.info("[StoreManager] Removed all equipped locally: user={} removed={}", (Object)(username == null ? "offline" : username), (Object)removed);
        if (username != null) {
            this.marketplaceClient.removeAll(username).thenAccept(result -> this.runOnClientThread(() -> {
                if (result.userState() != null) {
                    this.syncEquipped(result.userState().equippedItemIds());
                    this.saveState();
                    this.applyRuntime();
                    VayuHUDClient.LOGGER.info("[StoreManager] Remove-all reconciled: equipped={}", (Object)this.state.getEquippedItemIds().size());
                }
            })).exceptionally(ex -> {
                this.runOnClientThread(() -> VayuHUDClient.LOGGER.warn("[StoreManager] Backend removeAll failed: {}", (Object)(ex != null ? ex.getMessage() : "unknown")));
                return null;
            });
        }
    }

    public boolean claimDailyCoins() {
        if (!this.state.canClaimDailyCoins()) {
            return false;
        }
        this.state.addCoins(this.dailyCoinClaimAmount);
        this.state.markDailyCoinsClaimed();
        this.saveState();
        String username = this.getClientUsername();
        VayuHUDClient.LOGGER.info("[StoreManager] Daily claim queued: user={} amount={}", (Object)(username == null ? "offline" : username), (Object)this.dailyCoinClaimAmount);
        if (username != null) {
            this.marketplaceClient.claimDaily(username).thenAccept(result -> this.runOnClientThread(() -> {
                if (result.userState() != null) {
                    this.state.setCoinBalance(result.userState().coinBalance());
                    this.state.setLastCoinClaimEpochDay(result.userState().lastCoinClaimEpochDay());
                    this.saveState();
                    VayuHUDClient.LOGGER.info("[StoreManager] Daily claim reconciled: balance={}", (Object)this.state.getCoinBalance());
                }
            })).exceptionally(ex -> {
                this.runOnClientThread(() -> VayuHUDClient.LOGGER.warn("[StoreManager] Backend claim failed: {}", (Object)(ex != null ? ex.getMessage() : "unknown")));
                return null;
            });
        }
        return true;
    }

    public void applyRuntime() {
        List<StoreCosmetic> equipped = this.getEquippedCosmetics();
        if (equipped.isEmpty()) {
            this.runtimeAdapter.clearAllCosmetics();
        } else {
            this.runtimeAdapter.applyCosmetics(equipped);
        }
        this.scheduleLocalCosmeticRefresh();
    }

    public void saveState() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("coinBalance", (Number)this.state.getCoinBalance());
            root.addProperty("lastCategory", this.state.getLastSelectedCategory());
            root.addProperty("scrollOffset", (Number)this.state.getScrollOffset());
            root.addProperty("lastCoinClaimEpochDay", (Number)this.state.getLastCoinClaimEpochDay());
            JsonArray owned = new JsonArray();
            for (String id : this.state.getOwnedItemIds()) {
                owned.add(new JsonPrimitive(id));
            }
            root.add("owned", (JsonElement)owned);
            JsonArray equipped = new JsonArray();
            for (String id : this.state.getEquippedItemIds()) {
                equipped.add(new JsonPrimitive(id));
            }
            root.add("equipped", (JsonElement)equipped);
            Files.createDirectories(this.configPath.getParent(), new FileAttribute[0]);
            Files.writeString(this.configPath, (CharSequence)this.gson.toJson((JsonElement)root), new OpenOption[0]);
        }
        catch (Exception e) {
            VayuHUDClient.LOGGER.error("[StoreManager] Failed to save state: {}", (Object)e.getMessage());
        }
    }

    private void loadState() {
        if (!Files.exists(this.configPath, new LinkOption[0])) {
            return;
        }
        try {
            String id;
            int i;
            String json = Files.readString(this.configPath);
            JsonObject root = (JsonObject)this.gson.fromJson(json, JsonObject.class);
            if (root.has("coinBalance")) {
                this.state.setCoinBalance(root.get("coinBalance").getAsInt());
            }
            if (root.has("lastCategory")) {
                this.state.setLastSelectedCategory(root.get("lastCategory").getAsString());
            }
            if (root.has("scrollOffset")) {
                this.state.setScrollOffset(root.get("scrollOffset").getAsDouble());
            }
            if (root.has("lastCoinClaimEpochDay")) {
                this.state.setLastCoinClaimEpochDay(root.get("lastCoinClaimEpochDay").getAsLong());
            }
            if (root.has("owned")) {
                JsonArray owned = root.getAsJsonArray("owned");
                for (i = 0; i < owned.size(); ++i) {
                    id = owned.get(i).getAsString();
                    if (this.getCosmetic(id) == null) continue;
                    this.state.addOwned(id);
                }
            }
            if (root.has("equipped")) {
                JsonArray equipped = root.getAsJsonArray("equipped");
                for (i = 0; i < equipped.size(); ++i) {
                    id = equipped.get(i).getAsString();
                    if (this.getCosmetic(id) == null || !this.state.owns(id)) continue;
                    this.equipLoaded(id);
                }
            }
            VayuHUDClient.LOGGER.info("[StoreManager] Store state loaded");
        }
        catch (Exception e) {
            VayuHUDClient.LOGGER.error("[StoreManager] Failed to load state: {}", (Object)e.getMessage());
        }
    }

    private void equipLoaded(String id) {
        StoreCosmetic cosmetic = this.getCosmetic(id);
        if (cosmetic == null) {
            return;
        }
        List<StoreCosmetic> equipped = this.getEquippedCosmetics();
        for (StoreCosmetic equippedCosmetic : equipped) {
            if (!cosmetic.conflictsWith(equippedCosmetic)) continue;
            this.state.unequip(equippedCosmetic.getId());
        }
        this.trimChatTagsForNextEquip(cosmetic);
        this.state.equip(id);
    }

    private void syncUserState(MarketplaceClient.UserState userState) {
        this.state.setCoinBalance(userState.coinBalance());
        this.state.setLastCoinClaimEpochDay(userState.lastCoinClaimEpochDay());
        this.syncOwnedAndEquipped(userState.ownedItemIds(), userState.equippedItemIds());
    }

    private void syncOwnedAndEquipped(List<String> ownedIds, List<String> equippedIds) {
        ArrayList<String> owned = new ArrayList<String>();
        for (String id : ownedIds) {
            if (this.getCosmetic(id) == null) continue;
            owned.add(id);
        }
        this.state.replaceOwned(owned);
        ArrayList<String> equipped = new ArrayList<String>();
        for (String id : equippedIds) {
            if (this.getCosmetic(id) == null || !this.state.owns(id)) continue;
            equipped.add(id);
        }
        this.syncEquipped(equipped);
    }

    private void syncEquipped(List<String> equippedIds) {
        this.state.replaceEquipped(List.of());
        for (String id : equippedIds) {
            if (this.getCosmetic(id) == null || !this.state.owns(id)) continue;
            this.equipLoaded(id);
        }
    }

    private void trimChatTagsForNextEquip(StoreCosmetic nextCosmetic) {
        StoreCosmetic equipped;
        if (!StoreManager.isChatTag(nextCosmetic)) {
            return;
        }
        int activeTags = 0;
        ArrayList<String> equippedIds = new ArrayList<String>(this.state.getEquippedItemIds());
        for (String equippedId : equippedIds) {
            equipped = this.getCosmetic(equippedId);
            if (!StoreManager.isChatTag(equipped) || equippedId.equals(nextCosmetic.getId())) continue;
            ++activeTags;
        }
        for (String equippedId : equippedIds) {
            if (activeTags < 3) break;
            equipped = this.getCosmetic(equippedId);
            if (!StoreManager.isChatTag(equipped) || equippedId.equals(nextCosmetic.getId())) continue;
            this.state.unequip(equippedId);
            --activeTags;
        }
    }

    private static boolean isChatTag(StoreCosmetic cosmetic) {
        return cosmetic != null && CHAT_TAG_SLOT.equals(cosmetic.getSlot());
    }

    private boolean purchaseLocal(String id, StoreCosmetic cosmetic) {
        if (!this.state.spendCoins(cosmetic.getPrice())) {
            return false;
        }
        this.state.addOwned(id);
        this.saveState();
        return true;
    }

    private String getClientUsername() {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() != null && client.getUser().getName() != null) {
            return client.getUser().getName();
        }
        return null;
    }

    public void onJoin() {
        this.bootstrapAttempted = false;
        this.catalogLoaded = false;
        this.nextRemoteCosmeticRefreshMillis = 0L;
        this.scheduleLocalCosmeticRefresh();
        VayuHUDClient.LOGGER.info("[StoreManager] Join detected, queued store bootstrap/runtime refresh");
    }

    public void onClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (!this.bootstrapAttempted) {
            VayuHUDClient.LOGGER.info("[StoreManager] Player ready, starting marketplace bootstrap");
            this.bootstrapFromBackend();
            this.applyRuntime();
        }
        this.refreshLocalCosmeticsIfQueued();
        this.refreshVisiblePlayerCosmetics(client);
    }

    private void refreshVisiblePlayerCosmetics(Minecraft client) {
        if (!this.catalogLoaded) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.nextRemoteCosmeticRefreshMillis) {
            return;
        }
        this.nextRemoteCosmeticRefreshMillis = now + 5000L;
        this.remoteCosmeticResolver.refreshVisiblePlayers(client);
    }

    public void onDisconnect() {
        this.bootstrapAttempted = false;
        this.catalogLoaded = false;
        this.nextRemoteCosmeticRefreshMillis = 0L;
        this.remoteCosmeticResolver.clear();
        VayuHUDClient.LOGGER.info("[StoreManager] Disconnected, cleared remote cosmetics and kept local state");
    }

    private void runOnClientThread(Runnable task) {
        Minecraft.getInstance().schedule(task);
    }

    private void scheduleLocalCosmeticRefresh() {
        this.localCosmeticRefreshTicks = Math.max(this.localCosmeticRefreshTicks, 80);
    }

    private void refreshLocalCosmeticsIfQueued() {
        if (this.localCosmeticRefreshTicks <= 0) {
            return;
        }
        --this.localCosmeticRefreshTicks;
        this.runtimeAdapter.refreshLocalCosmetics();
    }

    private void addCosmetic(String id, String displayName, CosmeticCategory category, StoreCosmetic.Rarity rarity, int price, String description, String assetName, String payload, boolean supportsMultipleEquip) {
        this.catalog.add(new StoreCosmetic(id, displayName, category, rarity, price, description, assetName, payload, supportsMultipleEquip));
    }

    private void addCosmeticIfMissing(String id, String displayName, CosmeticCategory category, StoreCosmetic.Rarity rarity, int price, String description, String assetName, String payload, boolean supportsMultipleEquip) {
        if (this.catalog.find(id) == null) {
            this.addCosmetic(id, displayName, category, rarity, price, description, assetName, payload, supportsMultipleEquip);
        }
    }

    private void ensureLocalCategoryFillers() {
        this.addCosmeticIfMissing("storm_step_boots", "Storm Step Boots", CosmeticCategory.BOOTS, StoreCosmetic.Rarity.RARE, 310, "Sharp storm-gray boot plates with electric trim.", "storm_runner", "leg:storm_step", false);
        this.addCosmeticIfMissing("glacier_boots", "Glacier Boots", CosmeticCategory.BOOTS, StoreCosmetic.Rarity.UNCOMMON, 220, "Clean icy boot cuffs with cool blue highlights.", "glacier_fold", "leg:glacier_boots", false);
        this.addCosmeticIfMissing("gold_trace_boots", "Gold Trace Boots", CosmeticCategory.BOOTS, StoreCosmetic.Rarity.RARE, 320, "Premium gold trace accents around both boots.", "golden_trace", "leg:gold_trace", false);
        this.addCosmeticIfMissing("frost_ring_boots", "Frost Ring Boots", CosmeticCategory.BOOTS, StoreCosmetic.Rarity.UNCOMMON, 230, "Compact frost bands built for survival loadouts.", "frost_ring", "leg:frost_ring", false);
        this.addCosmeticIfMissing("adventure_boots", "Adventure Boots", CosmeticCategory.BOOTS, StoreCosmetic.Rarity.COMMON, 180, "Utility boot wraps for a clean explorer profile.", "adventure_roll", "leg:adventure_boots", false);
        this.addCosmeticIfMissing("orbit_pet", "Orbit Pet", CosmeticCategory.PETS, StoreCosmetic.Rarity.RARE, 280, "A compact floating companion with a dark orbital shell.", "void_orbit", "pet:orbit", false);
        this.addCosmeticIfMissing("monitor_pet", "Monitor Pet", CosmeticCategory.PETS, StoreCosmetic.Rarity.UNCOMMON, 220, "A tiny screen companion for tech-heavy profiles.", "mini_monitor", "pet:monitor", false);
        this.addCosmeticIfMissing("serpent_pet", "Serpent Pet", CosmeticCategory.PETS, StoreCosmetic.Rarity.EPIC, 360, "A neon companion with sharp VayuClient color accents.", "neon_serpent", "pet:serpent", false);
        this.addCosmeticIfMissing("drone_pet", "Drone Pet", CosmeticCategory.PETS, StoreCosmetic.Rarity.EPIC, 390, "A small drone-style companion with clean panel lines.", "drone_pack", "pet:drone", false);
        this.addCosmeticIfMissing("crystal_pet", "Crystal Pet", CosmeticCategory.PETS, StoreCosmetic.Rarity.RARE, 300, "A faceted crystal companion with a soft glow.", "crystal_pack", "pet:crystal", false);
    }

    private void populateFallbackCatalog() {
        this.addCosmetic("featured_cyberpunk_city", "Cyberpunk City Cloak", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.LEGENDARY, 450, "A neon city cape with VayuClient orange accents.", "cyberpunk_cloak", "cloak:cyberpunk_city", false);
        this.addCosmetic("featured_solar_drift", "Solar Drift Elytra", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.LEGENDARY, 500, "Solar panels and hot orange flight trims.", "solar_drift_elytra", "elytra:solar_drift", false);
        this.addCosmetic("featured_founder_cape", "VayuClient Founder Cape", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.FOUNDER, 1000, "A premium founder cape with clean VayuClient branding.", "founder_cape", "cloak:founder", false);
        this.addCosmetic("featured_neon_halo", "Neon Halo", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.EPIC, 300, "A glowing crown ring that floats above the player.", "neon_halo", "hat:neon_halo", true);
        this.addCosmetic("featured_inferno_wings", "Inferno Wing Pack", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.LEGENDARY, 550, "Back-mounted wing plates with ember glow.", "inferno_wings", "back:inferno_wings", false);
        this.addCosmetic("featured_aurora_trail", "Aurora Aura", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.EPIC, 350, "A soft aurora shell around your player.", "aurora_trail", "aura:aurora", false);
        this.addCosmetic("featured_crystal_tag", "Crystal Nameplate", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.RARE, 300, "A crisp crystalline nametag prefix.", "crystal_nameplate", "nametag:crystal", false);
        this.addCosmetic("featured_void_matrix", "Void Matrix Cloak", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.LEGENDARY, 520, "A deep black cloak with luminous matrix lines.", "void_matrix", "cloak:void_matrix", false);
        this.addCosmetic("featured_headphones", "Studio Headphones", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.UNCOMMON, 180, "Minimal headset accessory for clean profiles.", "studio_headphones", "hat:studio_headphones", true);
        this.addCosmetic("featured_jetpack", "Compact Jetpack", CosmeticCategory.FEATURED, StoreCosmetic.Rarity.EPIC, 430, "A compact sci-fi pack with orange thrusters.", "compact_jetpack", "back:compact_jetpack", false);
        this.addCosmetic("cyberpunk_tokyo", "Cyberpunk Tokyo", CosmeticCategory.CAPES, StoreCosmetic.Rarity.EPIC, 400, "A rainy Tokyo skyline with violet neon.", "cyberpunk_tokyo", "cloak:cyberpunk_tokyo", false);
        this.addCosmetic("retrowave_sunset", "Retrowave Sunset", CosmeticCategory.CAPES, StoreCosmetic.Rarity.RARE, 350, "A clean sunset grid cloak.", "retrowave_sunset", "cloak:retrowave_sunset", false);
        this.addCosmetic("obsidian_circuit", "Obsidian Circuit", CosmeticCategory.CAPES, StoreCosmetic.Rarity.RARE, 350, "Dark obsidian panels with thin circuit traces.", "obsidian_circuit", "cloak:obsidian_circuit", false);
        this.addCosmetic("frostbyte", "Frostbyte", CosmeticCategory.CAPES, StoreCosmetic.Rarity.UNCOMMON, 250, "Blue ice gradients with white signal lines.", "frostbyte", "cloak:frostbyte", false);
        this.addCosmetic("ember_core", "Ember Core", CosmeticCategory.CAPES, StoreCosmetic.Rarity.RARE, 350, "A hot ember center with smoke-dark edges.", "ember_core", "cloak:ember_core", false);
        this.addCosmetic("void_matrix", "Void Matrix", CosmeticCategory.CAPES, StoreCosmetic.Rarity.LEGENDARY, 500, "Black matrix glass and dimensional glow.", "void_matrix", "cloak:void_matrix", false);
        this.addCosmetic("moonlit_shrine", "Moonlit Shrine", CosmeticCategory.CAPES, StoreCosmetic.Rarity.EPIC, 410, "Silver moonlight over a quiet shrine silhouette.", "moonlit_shrine", "cloak:moonlit_shrine", false);
        this.addCosmetic("neon_serpent", "Neon Serpent", CosmeticCategory.CAPES, StoreCosmetic.Rarity.EPIC, 420, "A sharp serpent motif in green and orange.", "neon_serpent", "cloak:neon_serpent", false);
        this.addCosmetic("pixel_storm", "Pixel Storm", CosmeticCategory.CAPES, StoreCosmetic.Rarity.UNCOMMON, 240, "Pixel rain and electric grid fragments.", "pixel_storm", "cloak:pixel_storm", false);
        this.addCosmetic("royal_carbon", "Royal Carbon", CosmeticCategory.CAPES, StoreCosmetic.Rarity.RARE, 360, "Carbon weave with restrained gold trim.", "royal_carbon", "cloak:royal_carbon", false);
        this.addCosmetic("dragonfire_elytra", "Dragonfire Elytra", CosmeticCategory.WINGS, StoreCosmetic.Rarity.LEGENDARY, 500, "Dragon-scale wing plates with fire trim.", "dragonfire_elytra", "elytra:dragonfire", false);
        this.addCosmetic("prism_flight", "Prism Flight", CosmeticCategory.WINGS, StoreCosmetic.Rarity.EPIC, 400, "Prismatic wing panels that feel light and premium.", "prism_flight", "elytra:prism_flight", false);
        this.addCosmetic("nightfall", "Nightfall", CosmeticCategory.WINGS, StoreCosmetic.Rarity.RARE, 350, "Midnight blue wings with tiny star points.", "nightfall", "elytra:nightfall", false);
        this.addCosmetic("ocean_pulse", "Ocean Pulse", CosmeticCategory.WINGS, StoreCosmetic.Rarity.UNCOMMON, 250, "Aqua pulse lines over deep ocean blues.", "ocean_pulse", "elytra:ocean_pulse", false);
        this.addCosmetic("solar_drift", "Solar Drift", CosmeticCategory.WINGS, StoreCosmetic.Rarity.LEGENDARY, 520, "Warm solar wing gradients built for screenshots.", "solar_drift", "elytra:solar_drift", false);
        this.addCosmetic("chrome_wings", "Chrome Wings", CosmeticCategory.WINGS, StoreCosmetic.Rarity.RARE, 360, "Cool chrome panels with orange edge lighting.", "chrome_wings", "elytra:chrome_wings", false);
        this.addCosmetic("nebula_glide", "Nebula Glide", CosmeticCategory.WINGS, StoreCosmetic.Rarity.EPIC, 430, "A purple-blue nebula glide profile.", "nebula_glide", "elytra:nebula_glide", false);
        this.addCosmetic("storm_runner", "Storm Runner", CosmeticCategory.WINGS, StoreCosmetic.Rarity.RARE, 360, "Sharp storm-gray wings with electric accents.", "storm_runner", "elytra:storm_runner", false);
        this.addCosmetic("magma_vector", "Magma Vector", CosmeticCategory.WINGS, StoreCosmetic.Rarity.EPIC, 440, "Magma vector panels with hard diagonal lines.", "magma_vector", "elytra:magma_vector", false);
        this.addCosmetic("glacier_fold", "Glacier Fold", CosmeticCategory.WINGS, StoreCosmetic.Rarity.UNCOMMON, 260, "Clean glacier wing folds with subtle icy light.", "glacier_fold", "elytra:glacier_fold", false);
        this.addCosmetic("fox_ears", "Fox Ears", CosmeticCategory.HEAD, StoreCosmetic.Rarity.UNCOMMON, 200, "A clean fox-ear silhouette for casual loadouts.", "fox_ears", "hat:fox_ears", true);
        this.addCosmetic("crown_of_sparks", "Crown of Sparks", CosmeticCategory.HATS, StoreCosmetic.Rarity.EPIC, 350, "A compact crown with bright spark geometry.", "crown_of_sparks", "hat:crown_of_sparks", true);
        this.addCosmetic("headphones", "Headphones", CosmeticCategory.HEAD, StoreCosmetic.Rarity.COMMON, 150, "Simple headphones with a dark studio finish.", "headphones", "hat:headphones", true);
        this.addCosmetic("mini_monitor", "Mini Monitor", CosmeticCategory.HEAD, StoreCosmetic.Rarity.RARE, 280, "A tiny CRT monitor profile accessory.", "mini_monitor", "hat:mini_monitor", true);
        this.addCosmetic("wizard_brim", "Wizard Brim", CosmeticCategory.HATS, StoreCosmetic.Rarity.RARE, 300, "A sharp brim hat with subtle arcane trim.", "wizard_brim", "hat:wizard_brim", true);
        this.addCosmetic("cat_ears", "Cat Ears", CosmeticCategory.HEAD, StoreCosmetic.Rarity.UNCOMMON, 210, "Minimal cat ears with soft neon tips.", "cat_ears", "hat:cat_ears", true);
        this.addCosmetic("halo_zero", "Halo Zero", CosmeticCategory.HEAD, StoreCosmetic.Rarity.EPIC, 360, "A clean floating halo with orange glow.", "halo_zero", "hat:halo_zero", true);
        this.addCosmetic("snow_goggles", "Snow Goggles", CosmeticCategory.HEAD, StoreCosmetic.Rarity.COMMON, 160, "Compact goggles for survival and winter fits.", "snow_goggles", "hat:snow_goggles", true);
        this.addCosmetic("samurai_band", "Samurai Band", CosmeticCategory.HEAD, StoreCosmetic.Rarity.RARE, 290, "A slim headband with dark red trim.", "samurai_band", "hat:samurai_band", true);
        this.addCosmetic("glitch_crown", "Glitch Crown", CosmeticCategory.HATS, StoreCosmetic.Rarity.LEGENDARY, 470, "A crown silhouette with fragmented pixel edges.", "glitch_crown", "hat:glitch_crown", true);
        this.addCosmetic("crystal_pack", "Crystal Pack", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.RARE, 300, "A glowing crystal pack with faceted highlights.", "crystal_pack", "back:crystal_pack", false);
        this.addCosmetic("jetpack", "Jetpack", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.EPIC, 400, "A sci-fi jetpack with warm thruster panels.", "jetpack", "back:jetpack", false);
        this.addCosmetic("inferno_wings", "Inferno Wings", CosmeticCategory.WINGS, StoreCosmetic.Rarity.LEGENDARY, 560, "Compact wing plates with ember lighting.", "inferno_wings", "back:inferno_wings", false);
        this.addCosmetic("holo_sword", "Holo Sword", CosmeticCategory.HANDS, StoreCosmetic.Rarity.EPIC, 420, "A holographic hand accessory with a sharp sci-fi profile.", "holo_sword", "arm:holo_sword", false);
        this.addCosmetic("satellite_pack", "Satellite Pack", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.RARE, 330, "A compact antenna pack for tech-heavy skins.", "satellite_pack", "back:satellite_pack", false);
        this.addCosmetic("arcane_tome", "Arcane Tome", CosmeticCategory.HANDS, StoreCosmetic.Rarity.RARE, 330, "A compact arcane hand charm with bright glyphs.", "arcane_tome", "arm:arcane_tome", false);
        this.addCosmetic("neon_quiver", "Neon Bracer", CosmeticCategory.HANDS, StoreCosmetic.Rarity.UNCOMMON, 260, "A sleek wrist rig with neon strap highlights.", "neon_quiver", "arm:neon_quiver", false);
        this.addCosmetic("reactor_core", "Reactor Core", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.LEGENDARY, 540, "A compact reactor backpack with bright center light.", "reactor_core", "back:reactor_core", false);
        this.addCosmetic("drone_pack", "Drone Pack", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.EPIC, 410, "A technical drone dock backpack.", "drone_pack", "back:drone_pack", false);
        this.addCosmetic("adventure_roll", "Adventure Roll", CosmeticCategory.BACKPACKS, StoreCosmetic.Rarity.COMMON, 190, "A clean bedroll pack for survival profiles.", "adventure_roll", "back:adventure_roll", false);
        this.addCosmetic("orange_aura", "Orange Aura", CosmeticCategory.AURAS, StoreCosmetic.Rarity.UNCOMMON, 200, "A VayuClient orange body aura.", "orange_aura", "aura:orange", false);
        this.addCosmetic("pixel_sparks", "Pixel Sparks", CosmeticCategory.AURAS, StoreCosmetic.Rarity.RARE, 280, "A pixelated spark shell for PvP profiles.", "pixel_sparks", "aura:pixel_sparks", false);
        this.addCosmetic("aurora_ring", "Aurora Ring", CosmeticCategory.AURAS, StoreCosmetic.Rarity.EPIC, 360, "A soft ring of aurora color around the player.", "aurora_ring", "aura:aurora_ring", false);
        this.addCosmetic("void_orbit", "Void Orbit", CosmeticCategory.AURAS, StoreCosmetic.Rarity.LEGENDARY, 520, "A dark orbital frame with bright corner sparks.", "void_orbit", "aura:void_orbit", false);
        this.addCosmetic("ember_shell", "Ember Shell", CosmeticCategory.AURAS, StoreCosmetic.Rarity.RARE, 300, "A warm ember shell with dark smoky edges.", "ember_shell", "aura:ember_shell", false);
        this.addCosmetic("frost_ring", "Frost Ring", CosmeticCategory.AURAS, StoreCosmetic.Rarity.UNCOMMON, 220, "A crisp icy ring around the player.", "frost_ring", "aura:frost_ring", false);
        this.addCosmetic("prism_field", "Prism Field", CosmeticCategory.AURAS, StoreCosmetic.Rarity.EPIC, 380, "A multicolor prism field with clean geometry.", "prism_field", "aura:prism_field", false);
        this.addCosmetic("shadow_pulse", "Shadow Pulse", CosmeticCategory.AURAS, StoreCosmetic.Rarity.RARE, 310, "A dark pulse shell with subtle edge glow.", "shadow_pulse", "aura:shadow_pulse", false);
        this.addCosmetic("matrix_scan", "Matrix Scan", CosmeticCategory.AURAS, StoreCosmetic.Rarity.LEGENDARY, 530, "A scanning matrix frame with grid lines.", "matrix_scan", "aura:matrix_scan", false);
        this.addCosmetic("golden_trace", "Golden Trace", CosmeticCategory.AURAS, StoreCosmetic.Rarity.RARE, 320, "A premium gold trace around the player.", "golden_trace", "aura:golden_trace", false);
        this.ensureLocalCategoryFillers();
        this.addCosmetic("founder_tag", "Founder Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.FOUNDER, 0, "A founder nametag prefix.", "founder_tag", "nametag:founder", false);
        this.addCosmetic("vip_glow", "VIP Glow", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.RARE, 300, "A clean VIP nametag prefix.", "vip_glow", "nametag:vip_glow", false);
        this.addCosmetic("champion_tag", "Champion Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.EPIC, 380, "A competitive champion nametag prefix.", "champion_tag", "nametag:champion", false);
        this.addCosmetic("builder_tag", "Builder Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.UNCOMMON, 180, "A builder-focused nametag prefix.", "builder_tag", "nametag:builder", false);
        this.addCosmetic("creator_tag", "Creator Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.EPIC, 360, "A creator nametag prefix for content profiles.", "creator_tag", "nametag:creator", false);
        this.addCosmetic("speedrunner_tag", "Speedrunner Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.RARE, 300, "A speedrunner nametag prefix.", "speedrunner_tag", "nametag:speedrunner", false);
        this.addCosmetic("duelist_tag", "Duelist Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.RARE, 290, "A clean duelist nametag prefix.", "duelist_tag", "nametag:duelist", false);
        this.addCosmetic("explorer_tag", "Explorer Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.UNCOMMON, 190, "A survival explorer nametag prefix.", "explorer_tag", "nametag:explorer", false);
        this.addCosmetic("mythic_tag", "Mythic Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.LEGENDARY, 520, "A mythic nametag prefix with premium presence.", "mythic_tag", "nametag:mythic", false);
        this.addCosmetic("minimal_tag", "Minimal Tag", CosmeticCategory.NAMETAGS, StoreCosmetic.Rarity.COMMON, 120, "A restrained nametag prefix for clean UI.", "minimal_tag", "nametag:minimal", false);
    }
}

