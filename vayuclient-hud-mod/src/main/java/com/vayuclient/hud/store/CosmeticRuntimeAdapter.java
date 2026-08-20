/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.store;

import java.util.List;
import com.vayuclient.hud.store.StoreCosmetic;

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

