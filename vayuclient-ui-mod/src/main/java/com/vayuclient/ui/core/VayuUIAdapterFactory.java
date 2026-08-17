package com.vayuclient.ui.core;

import com.vayuclient.ui.adapter.MinecraftUIAdapter_1_21;
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
            if ("26.2".equalsIgnoreCase(mcVersion) || mcVersion.startsWith("26.2")) {
                activeAdapter = new MinecraftUIAdapter_26_2();
            } else if ("26.1".equalsIgnoreCase(mcVersion) || mcVersion.startsWith("26.1")) {
                activeAdapter = new MinecraftUIAdapter_26_1();
            } else if (mcVersion.startsWith("1.21") || mcVersion.startsWith("1.20")) {
                activeAdapter = new MinecraftUIAdapter_1_21();
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
