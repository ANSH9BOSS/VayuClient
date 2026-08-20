/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.fabricmc.loader.api.FabricLoader
 */
package com.vayuclient.hud.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.loader.api.FabricLoader;
import com.vayuclient.hud.VayuHUDClient;

public final class LauncherSkinPreference {
    private static final String CONFIG_KEY = "vayuClientSkinEnabled";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud").resolve("launcher.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;
    private static boolean vayuClientSkinEnabled;

    private LauncherSkinPreference() {
    }

    public static synchronized boolean isVayuClientSkinEnabled() {
        LauncherSkinPreference.load();
        return vayuClientSkinEnabled;
    }

    public static synchronized boolean toggle() {
        LauncherSkinPreference.load();
        vayuClientSkinEnabled = !vayuClientSkinEnabled;
        LauncherSkinPreference.save();
        return vayuClientSkinEnabled;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(CONFIG_PATH, new LinkOption[0])) {
            return;
        }
        try {
            JsonObject root = new Gson().fromJson((String)Files.readString(CONFIG_PATH), JsonObject.class);
            if (root.has(CONFIG_KEY) && root.get(CONFIG_KEY).isJsonPrimitive()) {
                vayuClientSkinEnabled = root.get(CONFIG_KEY).getAsBoolean();
            }
        }
        catch (Exception e) {
            VayuHUDClient.LOGGER.warn("Failed to load launcher skin preference; using VayuClient skin", (Throwable)e);
            vayuClientSkinEnabled = true;
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty(CONFIG_KEY, Boolean.valueOf(vayuClientSkinEnabled));
        try {
            Files.createDirectories(CONFIG_PATH.getParent(), new FileAttribute[0]);
            Files.writeString(CONFIG_PATH, (CharSequence)GSON.toJson((JsonElement)root), new OpenOption[0]);
        }
        catch (Exception e) {
            VayuHUDClient.LOGGER.warn("Failed to save launcher skin preference", (Throwable)e);
        }
    }

    static {
        vayuClientSkinEnabled = true;
    }
}

