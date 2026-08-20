package com.vayuclient.hud.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

public class NetworkAdapter {
    public static int getPing() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return 0;
        }
        try {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static String getServerAddress() {
        Minecraft client = Minecraft.getInstance();
        ServerData serverData = client.getCurrentServer();
        if (serverData != null) {
            return serverData.ip;
        }
        return "Singleplayer";
    }

    public static boolean isMultiplayer() {
        return Minecraft.getInstance().getCurrentServer() != null;
    }
}
