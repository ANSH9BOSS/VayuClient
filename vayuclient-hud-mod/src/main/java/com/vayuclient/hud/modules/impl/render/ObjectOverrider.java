package com.vayuclient.hud.modules.impl.render;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;

public class ObjectOverrider extends Module {
    private static ObjectOverrider instance;

    private final NumberSetting mainHandScale = this.register(new NumberSetting("mainhand_scale", "Main-hand item scale", 1.0, 0.2, 2.5, 0.05));
    private final NumberSetting offHandScale = this.register(new NumberSetting("offhand_scale", "Off-hand item scale", 1.0, 0.2, 2.5, 0.05));
    private final BooleanSetting smallShield = this.register(new BooleanSetting("small_shield", "Low / compact shield preset", false));
    private final BooleanSetting smallTotem = this.register(new BooleanSetting("small_totem", "Compact totem preset", false));
    
    private final NumberSetting mainHandX = this.register(new NumberSetting("mainhand_x", "Main hand X offset", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting mainHandY = this.register(new NumberSetting("mainhand_y", "Main hand Y offset", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting mainHandZ = this.register(new NumberSetting("mainhand_z", "Main hand Z offset", 0.0, -1.0, 1.0, 0.05));

    private final NumberSetting offHandX = this.register(new NumberSetting("offhand_x", "Off hand X offset", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offHandY = this.register(new NumberSetting("offhand_y", "Off hand Y offset", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offHandZ = this.register(new NumberSetting("offhand_z", "Off hand Z offset", 0.0, -1.0, 1.0, 0.05));

    private final BooleanSetting enableCustomTextures = this.register(new BooleanSetting("custom_textures", "Enable PNG texture override folder", true));
    private final BooleanSetting openTextureFolder = this.register(new BooleanSetting("open_folder", "Click to open custom textures folder", false));
    private final BooleanSetting resetTextures = this.register(new BooleanSetting("reset_textures", "Reset all textures to default", false));

    private final Map<String, File> customTextureFiles = new HashMap<>();
    private Path customTextureDir;

    public ObjectOverrider() {
        super("ObjectOverrider", "Dynamically replace any PNG texture and customize item scale/position", Category.RENDER);
        instance = this;
        this.initFolder();
    }

    public static ObjectOverrider getInstance() {
        return instance;
    }

    private void initFolder() {
        try {
            this.customTextureDir = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud").resolve("custom_textures");
            if (!Files.exists(this.customTextureDir)) {
                Files.createDirectories(this.customTextureDir);
                Path readme = this.customTextureDir.resolve("README.txt");
                if (!Files.exists(readme)) {
                    Files.writeString(readme, 
                        "=== VAYUCLIENT CUSTOM TEXTURE OVERRIDE ===\n\n" +
                        "Drop any PNG texture file into this folder to instantly replace an in-game object/item texture!\n\n" +
                        "Examples:\n" +
                        " - diamond_sword.png\n" +
                        " - netherite_sword.png\n" +
                        " - mace.png\n" +
                        " - totem_of_undying.png\n" +
                        " - shield.png\n" +
                        " - ender_pearl.png\n" +
                        " - golden_apple.png\n" +
                        " - bow.png\n" +
                        " - crossbow.png\n" +
                        " - firework_rocket.png\n\n" +
                        "Textures are reloaded dynamically without restarting your game!\n"
                    );
                }
            }
            this.reloadCustomTextures();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) return;

        if (this.openTextureFolder.isEnabled()) {
            this.openTextureFolder.setValue(false);
            if (this.customTextureDir != null) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(this.customTextureDir.toFile());
                    } else {
                        Runtime.getRuntime().exec("explorer.exe \"" + this.customTextureDir.toAbsolutePath() + "\"");
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (this.resetTextures.isEnabled()) {
            this.resetTextures.setValue(false);
            this.customTextureFiles.clear();
            this.sendMessage("All custom texture overrides have been reset to default!");
        }
    }

    public void reloadCustomTextures() {
        this.customTextureFiles.clear();
        if (this.customTextureDir == null || !Files.exists(this.customTextureDir)) return;

        File[] files = this.customTextureDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File f : files) {
                String key = f.getName().toLowerCase().replace(".png", "");
                this.customTextureFiles.put(key, f);
            }
        }
    }

    public boolean hasCustomTexture(String itemName) {
        if (!this.isEnabled() || !this.enableCustomTextures.isEnabled()) return false;
        return this.customTextureFiles.containsKey(itemName.toLowerCase());
    }

    public File getCustomTextureFile(String itemName) {
        return this.customTextureFiles.get(itemName.toLowerCase());
    }

    public float getMainHandScale() {
        return this.isEnabled() ? this.mainHandScale.getValue().floatValue() : 1.0f;
    }

    public float getOffHandScale() {
        if (!this.isEnabled()) return 1.0f;
        if (this.smallTotem.isEnabled()) return 0.65f;
        if (this.smallShield.isEnabled()) return 0.70f;
        return this.offHandScale.getValue().floatValue();
    }

    public float getMainHandX() { return this.isEnabled() ? this.mainHandX.getValue().floatValue() : 0.0f; }
    public float getMainHandY() { return this.isEnabled() ? this.mainHandY.getValue().floatValue() : 0.0f; }
    public float getMainHandZ() { return this.isEnabled() ? this.mainHandZ.getValue().floatValue() : 0.0f; }

    public float getOffHandX() { return this.isEnabled() ? this.offHandX.getValue().floatValue() : 0.0f; }
    public float getOffHandY() {
        if (!this.isEnabled()) return 0.0f;
        if (this.smallShield.isEnabled()) return -0.35f; // Lower shield position
        return this.offHandY.getValue().floatValue();
    }
    public float getOffHandZ() { return this.isEnabled() ? this.offHandZ.getValue().floatValue() : 0.0f; }
}
