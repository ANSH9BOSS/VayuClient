package com.vayuclient.ui.core;

import net.minecraft.client.Minecraft;

public final class EnvironmentResolver {
    private static String detectedMinecraftVersion = "";
    private static String detectedLoader = "Unknown";
    private static String detectedRenderer = "Vanilla";
    private static boolean initialized = false;

    public static void resolveEnvironment(Minecraft client) {
        if (initialized) return;

        // 1. Resolve Minecraft Version Dynamically
        try {
            if (client != null && client.getLaunchedVersion() != null && !client.getLaunchedVersion().isEmpty()) {
                detectedMinecraftVersion = client.getLaunchedVersion();
            } else {
                detectedMinecraftVersion = "26.2";
            }
        } catch (Throwable t) {
            detectedMinecraftVersion = "26.2";
        }

        // 2. Resolve Mod Loader
        try {
            if (classExists("net.fabricmc.loader.api.FabricLoader")) {
                detectedLoader = "Fabric";
            } else if (classExists("net.neoforged.fml.common.Mod")) {
                detectedLoader = "NeoForge";
            } else if (classExists("net.minecraftforge.fml.common.Mod")) {
                detectedLoader = "Forge";
            } else if (classExists("org.quiltmc.loader.api.QuiltLoader")) {
                detectedLoader = "Quilt";
            } else {
                detectedLoader = "Vanilla";
            }
        } catch (Throwable t) {
            detectedLoader = "Fabric";
        }

        // 3. Resolve Renderer / Optimization Mods
        try {
            if (classExists("net.irisshaders.iris.Iris")) {
                detectedRenderer = "Iris + Sodium";
            } else if (classExists("me.jellysquid.mods.sodium.client.SodiumClientMod") || classExists("net.caffeinemc.mods.sodium.client.SodiumClientMod")) {
                detectedRenderer = "Sodium Engine";
            } else if (classExists("net.raphimc.immediatelyfast.ImmediatelyFast")) {
                detectedRenderer = "ImmediatelyFast";
            } else {
                detectedRenderer = "Vanilla GL/Vulkan";
            }
        } catch (Throwable t) {
            detectedRenderer = "Standard";
        }

        initialized = true;
        System.out.println("[VayuClient UI] Environment Resolved:");
        System.out.println("  • Minecraft Version: " + detectedMinecraftVersion);
        System.out.println("  • Loader:            " + detectedLoader);
        System.out.println("  • Renderer:          " + detectedRenderer);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, EnvironmentResolver.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getMinecraftVersion() {
        if (!initialized) resolveEnvironment(Minecraft.getInstance());
        return detectedMinecraftVersion;
    }

    public static String getLoader() {
        if (!initialized) resolveEnvironment(Minecraft.getInstance());
        return detectedLoader;
    }

    public static String getRenderer() {
        if (!initialized) resolveEnvironment(Minecraft.getInstance());
        return detectedRenderer;
    }
}
