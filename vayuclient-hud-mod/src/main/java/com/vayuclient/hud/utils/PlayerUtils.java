/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 */
package com.vayuclient.hud.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class PlayerUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static LocalPlayer getPlayer() {
        return PlayerUtils.mc.player;
    }

    public static boolean isInGame() {
        return PlayerUtils.mc.player != null && PlayerUtils.mc.level != null;
    }

    public static Vec3 getPosition() {
        if (PlayerUtils.mc.player == null) {
            return Vec3.ZERO;
        }
        return PlayerUtils.mc.player.position();
    }

    public static double getX() {
        if (PlayerUtils.mc.player == null) {
            return 0.0;
        }
        return PlayerUtils.mc.player.getX();
    }

    public static double getY() {
        if (PlayerUtils.mc.player == null) {
            return 0.0;
        }
        return PlayerUtils.mc.player.getY();
    }

    public static double getZ() {
        if (PlayerUtils.mc.player == null) {
            return 0.0;
        }
        return PlayerUtils.mc.player.getZ();
    }

    public static float getYaw() {
        if (PlayerUtils.mc.player == null) {
            return 0.0f;
        }
        return PlayerUtils.mc.player.getYRot();
    }

    public static float getPitch() {
        if (PlayerUtils.mc.player == null) {
            return 0.0f;
        }
        return PlayerUtils.mc.player.getXRot();
    }

    public static String getFacingDirection() {
        if (PlayerUtils.mc.player == null) {
            return "N/A";
        }
        float yaw = PlayerUtils.mc.player.getYRot();
        if ((yaw = (yaw % 360.0f + 360.0f) % 360.0f) >= 315.0f || yaw < 45.0f) {
            return "South";
        }
        if (yaw >= 45.0f && yaw < 135.0f) {
            return "West";
        }
        if (yaw >= 135.0f && yaw < 225.0f) {
            return "North";
        }
        if (yaw >= 225.0f && yaw < 315.0f) {
            return "East";
        }
        return "N/A";
    }

    public static double getSpeed() {
        if (PlayerUtils.mc.player == null) {
            return 0.0;
        }
        Vec3 velocity = PlayerUtils.mc.player.getDeltaMovement();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
    }

    public static double distanceTo(Entity entity) {
        if (PlayerUtils.mc.player == null || entity == null) {
            return 0.0;
        }
        return PlayerUtils.mc.player.distanceTo(entity);
    }

    public static String getProfileName(Object profileObj) {
        if (profileObj == null) return "Unknown";
        try {
            return (String) profileObj.getClass().getMethod("name").invoke(profileObj);
        } catch (Throwable t) {
            try {
                return (String) profileObj.getClass().getMethod("getName").invoke(profileObj);
            } catch (Throwable t2) {
                return profileObj.toString();
            }
        }
    }
}

