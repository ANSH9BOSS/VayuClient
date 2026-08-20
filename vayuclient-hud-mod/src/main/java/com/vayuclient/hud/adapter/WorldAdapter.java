package com.vayuclient.hud.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class WorldAdapter {
    public static long getWorldTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null ? level.getOverworldClockTime() : 0L;
    }

    public static long getDayCount() {
        return getWorldTime() / 24000L;
    }

    public static boolean isRaining() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.isRaining();
    }
}
