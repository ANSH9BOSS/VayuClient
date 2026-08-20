package com.vayuclient.hud.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public class MinecraftAdapter {
    private static final Minecraft client = Minecraft.getInstance();

    public static Minecraft getClient() {
        return client;
    }

    public static LocalPlayer getPlayer() {
        return client.player;
    }

    public static ClientLevel getLevel() {
        return client.level;
    }

    public static Font getFont() {
        return client.font;
    }

    public static int getWindowWidth() {
        return client.getWindow().getGuiScaledWidth();
    }

    public static int getWindowHeight() {
        return client.getWindow().getGuiScaledHeight();
    }

    public static double getGuiScale() {
        return client.getWindow().getGuiScale();
    }

    public static boolean isInGame() {
        return client.player != null && client.level != null;
    }

    public static String getGameVersion() {
        try {
            return client.getLaunchedVersion();
        } catch (Throwable t) {
            return "1.21+";
        }
    }
}
