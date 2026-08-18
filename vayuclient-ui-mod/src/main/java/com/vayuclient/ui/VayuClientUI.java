package com.vayuclient.ui;

import com.vayuclient.ui.gui.VayuTitleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

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
            ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
                if (enabled && screen != null) {
                    String className = screen.getClass().getName();
                    if (className.contains("TitleScreen") || className.contains("class_442")) {
                        if (!(screen instanceof VayuTitleScreen)) {
                            if (client != null) {
                                client.setScreenAndShow(new VayuTitleScreen());
                            }
                        }
                    }
                }
            });
            System.out.println("[VayuClient UI] ScreenEvents Hook Registered Successfully!");
        } catch (Throwable t) {
            System.out.println("[VayuClient UI] ScreenEvents note: " + t.getMessage());
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
