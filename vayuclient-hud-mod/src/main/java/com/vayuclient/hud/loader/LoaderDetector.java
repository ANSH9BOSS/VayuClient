package com.vayuclient.hud.loader;

public class LoaderDetector {
    private static ModLoader activeLoader = null;

    public static ModLoader getActiveLoader() {
        if (activeLoader != null) {
            return activeLoader;
        }

        try {
            Class.forName("org.quiltmc.loader.api.QuiltLoader");
            activeLoader = ModLoader.QUILT;
            return activeLoader;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            activeLoader = ModLoader.NEOFORGE;
            return activeLoader;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            activeLoader = ModLoader.FORGE;
            return activeLoader;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            activeLoader = ModLoader.FABRIC;
            return activeLoader;
        } catch (ClassNotFoundException ignored) {}

        activeLoader = ModLoader.VANILLA;
        return activeLoader;
    }

    public static boolean isFabricOrQuilt() {
        ModLoader loader = getActiveLoader();
        return loader == ModLoader.FABRIC || loader == ModLoader.QUILT;
    }

    public static boolean isNeoForge() {
        return getActiveLoader() == ModLoader.NEOFORGE;
    }
}
