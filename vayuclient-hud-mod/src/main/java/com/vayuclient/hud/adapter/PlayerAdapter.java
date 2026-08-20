package com.vayuclient.hud.adapter;

import net.minecraft.world.entity.player.Player;

public class PlayerAdapter {
    public static final String VAYU_BADGE = "\ue000 ";

    public static float resolveHealth(Player player) {
        if (player == null) return 20.0f;
        float directHealth = player.getHealth();
        if (directHealth > 0.0f) {
            return directHealth;
        }
        return 20.0f;
    }

    public static float resolveAbsorption(Player player) {
        if (player == null) return 0.0f;
        return player.getAbsorptionAmount();
    }

    public static String formatHealthTag(Player player) {
        float rawHealth = resolveHealth(player);
        float rawAbs = resolveAbsorption(player);

        int healthHearts = (int) Math.ceil(rawHealth / 2.0f);
        if (healthHearts < 0) healthHearts = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("§c❤ §f").append(healthHearts);

        if (rawAbs > 0.01f) {
            int absHearts = (int) Math.ceil(rawAbs / 2.0f);
            sb.append(" §7| §e💛 §e").append(absHearts);
        }

        return sb.toString();
    }
}
