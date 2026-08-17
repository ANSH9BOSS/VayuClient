package com.vayuclient.ui.core;

import com.vayuclient.ui.adapter.MinecraftUIAdapter_26_1;
import com.vayuclient.ui.adapter.MinecraftUIAdapter_26_2;
import com.vayuclient.ui.adapter.MinecraftUIAdapter_Generic;

public final class VayuUIAdapterFactory {
    private static IClientUIAdapter activeAdapter;

    public static synchronized IClientUIAdapter getActiveAdapter() {
        if (activeAdapter != null) return activeAdapter;

        String mcVersion = EnvironmentResolver.getMinecraftVersion();
        System.out.println("[VayuClient UI] Resolving UI Adapter for Minecraft: " + mcVersion);

        try {
            if ("26.2".equalsIgnoreCase(mcVersion)) {
                activeAdapter = new MinecraftUIAdapter_26_2();
            } else if ("26.1".equalsIgnoreCase(mcVersion) || "26.1.2".equalsIgnoreCase(mcVersion)) {
                activeAdapter = new MinecraftUIAdapter_26_1();
            } else {
                activeAdapter = new MinecraftUIAdapter_Generic();
            }
        } catch (Throwable t) {
            System.err.println("[VayuClient UI] WARNING: Adapter resolution threw exception, using generic fallback: " + t.getMessage());
            activeAdapter = new MinecraftUIAdapter_Generic();
        }

        System.out.println("[VayuClient UI] Selected Adapter: " + activeAdapter.getAdapterId());
        return activeAdapter;
    }
}
