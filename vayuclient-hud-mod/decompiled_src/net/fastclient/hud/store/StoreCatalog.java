/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fastclient.hud.store.CosmeticCategory;
import net.fastclient.hud.store.StoreCosmetic;
import net.fastclient.hud.store.StoreState;

final class StoreCatalog {
    private volatile Snapshot snapshot = new Snapshot(List.of(), Map.of());

    StoreCatalog() {
    }

    void add(StoreCosmetic cosmetic) {
        ArrayList<StoreCosmetic> items = new ArrayList<StoreCosmetic>(this.snapshot.items());
        items.add(cosmetic);
        this.replaceAll(items);
    }

    void replaceAll(List<StoreCosmetic> cosmetics) {
        LinkedHashMap<String, StoreCosmetic> byId = new LinkedHashMap<String, StoreCosmetic>();
        for (StoreCosmetic cosmetic : cosmetics) {
            byId.put(cosmetic.getId(), cosmetic);
        }
        this.snapshot = new Snapshot(List.copyOf(byId.values()), Map.copyOf(byId));
    }

    List<StoreCosmetic> all() {
        return this.snapshot.items();
    }

    StoreCosmetic find(String id) {
        return this.snapshot.byId().get(id);
    }

    List<StoreCosmetic> owned(StoreState state) {
        return this.snapshot.items().stream().filter(c -> state.owns(c.getId())).toList();
    }

    List<StoreCosmetic> equipped(StoreState state) {
        return this.snapshot.items().stream().filter(c -> state.isEquipped(c.getId())).toList();
    }

    List<StoreCosmetic> byCategory(CosmeticCategory category) {
        return this.snapshot.items().stream().filter(c -> c.getCategory() == category).toList();
    }

    List<StoreCosmetic> search(String query, CosmeticCategory category, boolean ownedOnly, StoreState state) {
        String lower = query.toLowerCase(Locale.ROOT);
        boolean forceOwned = ownedOnly || category == CosmeticCategory.OWNED;
        return this.snapshot.items().stream().filter(c -> category == null || category == CosmeticCategory.OWNED || c.getCategory() == category).filter(c -> !forceOwned || state.owns(c.getId())).filter(c -> lower.isEmpty() || this.matches((StoreCosmetic)c, lower)).sorted(Comparator.comparingInt(c -> -c.getRarity().ordinal())).toList();
    }

    private boolean matches(StoreCosmetic cosmetic, String query) {
        return cosmetic.getDisplayName().toLowerCase(Locale.ROOT).contains(query) || cosmetic.getDescription().toLowerCase(Locale.ROOT).contains(query) || cosmetic.getRarity().getDisplayName().toLowerCase(Locale.ROOT).contains(query) || cosmetic.getCategory().getDisplayName().toLowerCase(Locale.ROOT).contains(query);
    }

    private record Snapshot(List<StoreCosmetic> items, Map<String, StoreCosmetic> byId) {
    }
}

