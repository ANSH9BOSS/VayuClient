package com.vayuclient.ui;

import com.vayuclient.ui.core.EnvironmentResolver;
import com.vayuclient.ui.core.VayuUIAdapterFactory;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public class VayuClientUI implements ClientModInitializer {
    public static final String MOD_ID = "vayuclient-ui";
    public static final String VERSION = "1.6.0";
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        System.out.println("==========================================================");
        System.out.println("   VAYUCLIENT MINECRAFT UI SYSTEM v" + VERSION);
        System.out.println("   Developer: ANSH9BOSS | Premium Client Experience");
        System.out.println("==========================================================");

        try {
            EnvironmentResolver.resolveEnvironment(Minecraft.getInstance());
            var adapter = VayuUIAdapterFactory.getActiveAdapter();
            if (adapter != null) {
                adapter.onInitialize(Minecraft.getInstance());
                System.out.println("[VayuClient UI] System Initialized Successfully with " + adapter.getAdapterId());
            }
        } catch (Throwable t) {
            System.out.println("[VayuClient UI] Initialization note: " + t.getMessage());
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
