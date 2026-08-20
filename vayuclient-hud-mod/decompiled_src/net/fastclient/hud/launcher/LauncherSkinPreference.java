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
package net.fastclient.hud.launcher;

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
import net.fastclient.hud.FastClientHUDClient;

public final class LauncherSkinPreference {
    private static final String CONFIG_KEY = "fastClientSkinEnabled";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fast-client-hud").resolve("launcher.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;
    private static boolean fastClientSkinEnabled;

    private LauncherSkinPreference() {
    }

    public static synchronized boolean isFastClientSkinEnabled() {
        LauncherSkinPreference.load();
        return fastClientSkinEnabled;
    }

    public static synchronized boolean toggle() {
        LauncherSkinPreference.load();
        fastClientSkinEnabled = !fastClientSkinEnabled;
        LauncherSkinPreference.save();
        return fastClientSkinEnabled;
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
            JsonObject root = JsonParser.parseString((String)Files.readString(CONFIG_PATH)).getAsJsonObject();
            if (root.has(CONFIG_KEY) && root.get(CONFIG_KEY).isJsonPrimitive()) {
                fastClientSkinEnabled = root.get(CONFIG_KEY).getAsBoolean();
            }
        }
        catch (Exception e) {
            FastClientHUDClient.LOGGER.warn("Failed to load launcher skin preference; using FastClient skin", (Throwable)e);
            fastClientSkinEnabled = true;
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty(CONFIG_KEY, Boolean.valueOf(fastClientSkinEnabled));
        try {
            Files.createDirectories(CONFIG_PATH.getParent(), new FileAttribute[0]);
            Files.writeString(CONFIG_PATH, (CharSequence)GSON.toJson((JsonElement)root), new OpenOption[0]);
        }
        catch (Exception e) {
            FastClientHUDClient.LOGGER.warn("Failed to save launcher skin preference", (Throwable)e);
        }
    }

    static {
        fastClientSkinEnabled = true;
    }
}

