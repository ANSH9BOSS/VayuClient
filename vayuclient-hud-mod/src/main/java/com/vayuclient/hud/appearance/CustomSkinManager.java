package com.vayuclient.hud.appearance;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomSkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VayuSkinManager");
    private static final CustomSkinManager INSTANCE = new CustomSkinManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean customSkinEnabled = false;
    private String skinSourceType = "vanilla"; // "vanilla", "premium", "file"
    private String premiumUsername = "";
    private boolean slimModel = false;
    private Path skinFile;
    private Path configFile;

    private ClientAsset.Texture cachedSkinAsset;

    public static CustomSkinManager getInstance() {
        return INSTANCE;
    }

    private CustomSkinManager() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            this.skinFile = dir.resolve("custom_skin.png");
            this.configFile = dir.resolve("skin_config.json");
            this.loadConfig();
        } catch (Throwable t) {
            LOGGER.warn("Failed initializing CustomSkinManager: {}", t.getMessage());
        }
    }

    public void loadConfig() {
        if (this.configFile == null || !Files.exists(this.configFile)) return;
        try {
            String jsonStr = Files.readString(this.configFile);
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (obj.has("enabled")) this.customSkinEnabled = obj.get("enabled").getAsBoolean();
            if (obj.has("sourceType")) this.skinSourceType = obj.get("sourceType").getAsString();
            if (obj.has("premiumUsername")) this.premiumUsername = obj.get("premiumUsername").getAsString();
            if (obj.has("slimModel")) this.slimModel = obj.get("slimModel").getAsBoolean();

            if (this.customSkinEnabled && Files.exists(this.skinFile)) {
                this.updateSkinAsset();
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed loading skin config: {}", t.getMessage());
        }
    }

    public void saveConfig() {
        if (this.configFile == null) return;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", this.customSkinEnabled);
            obj.addProperty("sourceType", this.skinSourceType);
            obj.addProperty("premiumUsername", this.premiumUsername);
            obj.addProperty("slimModel", this.slimModel);
            Files.writeString(this.configFile, GSON.toJson(obj));
        } catch (Throwable t) {
            LOGGER.warn("Failed saving skin config: {}", t.getMessage());
        }
    }

    public void updateSkinAsset() {
        if (!Files.exists(this.skinFile)) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Identifier id = Identifier.fromNamespaceAndPath("vayuclient-hud", "appearance/custom_skin_" + System.currentTimeMillis());
            SkinTextureDownloader downloader = new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), (Executor)mc);
            downloader.downloadAndRegisterSkin(id, this.skinFile, "", true).thenAccept(asset -> {
                this.cachedSkinAsset = asset;
            });
        } catch (Throwable t) {
            LOGGER.warn("Failed registering skin texture asset: {}", t.getMessage());
        }
    }

    public boolean applyPremiumSkin(String username, boolean slim) {
        try {
            String urlStr = "https://minotar.net/skin/" + username.trim();
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "VayuClient/2.1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, this.skinFile, StandardCopyOption.REPLACE_EXISTING);
                }
                this.customSkinEnabled = true;
                this.skinSourceType = "premium";
                this.premiumUsername = username.trim();
                this.slimModel = slim;
                this.updateSkinAsset();
                this.saveConfig();
                LOGGER.info("Applied premium skin for '{}' (slim={})", username, slim);
                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed downloading skin for {}: {}", username, t.getMessage());
        }
        return false;
    }

    public boolean applyCustomFileSkin(File file, boolean slim) {
        try {
            if (file == null || !file.exists()) return false;
            Files.copy(file.toPath(), this.skinFile, StandardCopyOption.REPLACE_EXISTING);
            this.customSkinEnabled = true;
            this.skinSourceType = "file";
            this.slimModel = slim;
            this.updateSkinAsset();
            this.saveConfig();
            LOGGER.info("Applied custom file skin from '{}' (slim={})", file.getName(), slim);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Failed applying skin file: {}", t.getMessage());
        }
        return false;
    }

    public void resetSkin() {
        this.customSkinEnabled = false;
        this.skinSourceType = "vanilla";
        this.cachedSkinAsset = null;
        try {
            if (this.skinFile != null && Files.exists(this.skinFile)) {
                Files.delete(this.skinFile);
            }
        } catch (Throwable ignored) {}
        this.saveConfig();
    }

    public PlayerSkin resolveSkin(PlayerSkin vanilla, String playerName) {
        if (!this.customSkinEnabled) return vanilla;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && mc.getUser().getName() != null) {
            String currentName = mc.getUser().getName();
            if (currentName.equalsIgnoreCase(playerName)) {
                if (this.cachedSkinAsset == null) {
                    this.updateSkinAsset();
                }
                ClientAsset.Texture skinAsset = this.cachedSkinAsset != null ? this.cachedSkinAsset : vanilla.body();
                PlayerModelType model = this.slimModel ? PlayerModelType.SLIM : PlayerModelType.WIDE;
                return new PlayerSkin(skinAsset, vanilla.cape(), vanilla.elytra(), model, vanilla.secure());
            }
        }
        return vanilla;
    }

    public boolean isCustomSkinEnabled() { return this.customSkinEnabled; }
    public String getSkinSourceType() { return this.skinSourceType; }
    public String getPremiumUsername() { return this.premiumUsername; }
    public boolean isSlimModel() { return this.slimModel; }
    public void setSlimModel(boolean slim) {
        this.slimModel = slim;
        this.saveConfig();
    }
}
