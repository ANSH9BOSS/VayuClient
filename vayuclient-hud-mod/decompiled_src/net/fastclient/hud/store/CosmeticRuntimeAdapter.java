/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.store;

import java.util.List;
import net.fastclient.hud.store.StoreCosmetic;

public interface CosmeticRuntimeAdapter {
    public void applyCosmetics(List<StoreCosmetic> var1);

    public void clearAllCosmetics();

    default public void refreshLocalCosmetics() {
    }

    default public void applyPlayerCosmetics(String username, List<StoreCosmetic> equippedCosmetics) {
    }

    default public void clearPlayerCosmetics(String username) {
    }

    default public void clearRemoteCosmetics() {
    }
}

