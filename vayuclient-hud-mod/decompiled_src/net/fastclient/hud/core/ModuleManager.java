/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.fastclient.hud.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.impl.hud.ArmorHUD;
import net.fastclient.hud.modules.impl.hud.ArrayListModule;
import net.fastclient.hud.modules.impl.hud.BiomeHUD;
import net.fastclient.hud.modules.impl.hud.CPSCounter;
import net.fastclient.hud.modules.impl.hud.ChatTimestamps;
import net.fastclient.hud.modules.impl.hud.ClockHUD;
import net.fastclient.hud.modules.impl.hud.ComboCounter;
import net.fastclient.hud.modules.impl.hud.CoordinatesModule;
import net.fastclient.hud.modules.impl.hud.DayCounter;
import net.fastclient.hud.modules.impl.hud.DirectionHUD;
import net.fastclient.hud.modules.impl.hud.FPSModule;
import net.fastclient.hud.modules.impl.hud.Keystrokes;
import net.fastclient.hud.modules.impl.hud.MemoryHUD;
import net.fastclient.hud.modules.impl.hud.PackDisplay;
import net.fastclient.hud.modules.impl.hud.PingDisplay;
import net.fastclient.hud.modules.impl.hud.PotionHUD;
import net.fastclient.hud.modules.impl.hud.SaturationHUD;
import net.fastclient.hud.modules.impl.hud.ServerInfoHUD;
import net.fastclient.hud.modules.impl.hud.SpeedHUD;
import net.fastclient.hud.modules.impl.movement.SprintModule;
import net.fastclient.hud.modules.impl.player.ToggleSneak;
import net.fastclient.hud.modules.impl.render.BlockOverlayModule;
import net.fastclient.hud.modules.impl.render.Crosshair;
import net.fastclient.hud.modules.impl.render.DamageIndicator;
import net.fastclient.hud.modules.impl.render.FastClientSkinsModule;
import net.fastclient.hud.modules.impl.render.FullbrightModule;
import net.fastclient.hud.modules.impl.render.HitboxModule;
import net.fastclient.hud.modules.impl.render.MotionBlurModule;
import net.fastclient.hud.modules.impl.render.NametagIconModule;
import net.fastclient.hud.modules.impl.render.NoBossBar;
import net.fastclient.hud.modules.impl.render.NoHurtCam;
import net.fastclient.hud.modules.impl.render.Particles;
import net.fastclient.hud.modules.impl.render.PingOverlay;
import net.fastclient.hud.modules.impl.render.ScoreboardMod;
import net.fastclient.hud.modules.impl.render.TimeChanger;
import net.fastclient.hud.modules.impl.render.WaypointsModule;
import net.fastclient.hud.modules.impl.utility.AutoGG;
import net.fastclient.hud.modules.impl.utility.AutoText;
import net.fastclient.hud.modules.impl.utility.Notifications;
import net.fastclient.hud.modules.impl.utility.ToolWarning;
import net.fastclient.hud.modules.impl.utility.ZoomModule;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.fastclient.hud.modules.settings.KeybindSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.fastclient.hud.modules.settings.Setting;
import net.fastclient.hud.modules.settings.TextSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"FastClientHUD");
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<Module>();
    private final Map<String, Module> modulesByName = new HashMap<String, Module>();
    private final Map<Category, List<Module>> modulesByCategory = new HashMap<Category, List<Module>>();
    private final Map<String, ModuleSnapshot> defaultSnapshots = new HashMap<String, ModuleSnapshot>();
    private final Path configPath;
    private final Gson gson;
    private int hudModuleCount = 0;

    public ModuleManager() {
        instance = this;
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("fast-client-hud").resolve("modules.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        for (Category category : Category.values()) {
            this.modulesByCategory.put(category, new ArrayList());
        }
        LOGGER.info("[ModuleManager] Initialized");
    }

    public static ModuleManager getInstance() {
        return instance;
    }

    public void register(Module module) {
        this.modules.add(module);
        this.modulesByName.put(module.getName().toLowerCase(Locale.ROOT), module);
        this.modulesByCategory.get((Object)module.getCategory()).add(module);
        if (module.getCategory() == Category.HUD) {
            int col = this.hudModuleCount % 4;
            int row = this.hudModuleCount / 4;
            int defaultX = 48 + col * 360;
            int defaultY = 120 + row * 180;
            module.setHudPosition(defaultX, defaultY);
            ++this.hudModuleCount;
        }
        LOGGER.debug("[ModuleManager] Registered: {}", (Object)module.getName());
    }

    public void registerModules() {
        LOGGER.info("[ModuleManager] Registering modules...");
        this.register(new FPSModule());
        this.register(new CoordinatesModule());
        this.register(new DirectionHUD());
        this.register(new ClockHUD());
        this.register(new MemoryHUD());
        this.register(new BiomeHUD());
        this.register(new PackDisplay());
        this.register(new ServerInfoHUD());
        this.register(new PingDisplay());
        this.register(new DayCounter());
        this.register(new SpeedHUD());
        this.register(new SaturationHUD());
        this.register(new ArmorHUD());
        this.register(new PotionHUD());
        this.register(new Keystrokes());
        this.register(new CPSCounter());
        this.register(new ArrayListModule());
        this.register(new ComboCounter());
        this.register(new ChatTimestamps());
        this.register(new FullbrightModule());
        this.register(new BlockOverlayModule());
        this.register(new HitboxModule());
        this.register(new NoHurtCam());
        this.register(new NoBossBar());
        this.register(new TimeChanger());
        this.register(new Crosshair());
        this.register(new ScoreboardMod());
        this.register(new DamageIndicator());
        this.register(new Particles());
        this.register(new MotionBlurModule());
        this.register(new NametagIconModule());
        this.register(new FastClientSkinsModule());
        this.register(new PingOverlay());
        this.register(new WaypointsModule());
        this.register(new SprintModule());
        this.register(new ToggleSneak());
        this.register(new ZoomModule());
        this.register(new AutoGG());
        this.register(new AutoText());
        this.register(new Notifications());
        this.register(new ToolWarning());
        this.captureDefaults();
        LOGGER.info("[ModuleManager] {} modules registered", (Object)this.modules.size());
        this.loadConfig();
    }

    public void onTick() {
        for (Module module : this.modules) {
            if (!module.isEnabled()) continue;
            try {
                module.onTick();
            }
            catch (Exception e) {
                LOGGER.error("[{}] Error in tick: {}", (Object)module.getName(), (Object)e.getMessage());
            }
        }
    }

    public Module getModule(String name) {
        return this.modulesByName.get(name.toLowerCase(Locale.ROOT));
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : this.modules) {
            if (!clazz.isInstance(module)) continue;
            return (T)module;
        }
        return null;
    }

    public List<Module> getModulesByCategory(Category category) {
        return this.modulesByCategory.getOrDefault((Object)category, Collections.emptyList());
    }

    public List<Module> getEnabledModules() {
        return this.modules.stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public List<Module> getModules() {
        return this.modules;
    }

    public void toggleModule(Module module) {
        if (module == null) {
            return;
        }
        if (module.isEnabled()) {
            module.setEnabled(false);
            this.notifyModuleToggle(module, false);
            return;
        }
        module.setEnabled(true);
        if (module.isHudVisible()) {
            this.ensureSmartHudPlacement(module);
        }
        this.notifyModuleToggle(module, true);
    }

    private void notifyModuleToggle(Module module, boolean enabled) {
        Notifications notifications = this.getModule(Notifications.class);
        if (notifications != null) {
            notifications.onModuleToggle(module.getDisplayName(), enabled);
        }
    }

    public void ensureSmartHudPlacement(Module module) {
        boolean safelyOnScreen;
        if (module == null || !module.isHudVisible()) {
            return;
        }
        module.setHudScale(Math.max(2.0f, module.getHudScale()));
        int screenWidth = Math.max(1, DisplaySpace.width());
        int screenHeight = Math.max(1, DisplaySpace.height());
        int width = Math.max(24, Math.round((float)module.getHudWidth() * module.getHudScale()));
        int height = Math.max(18, Math.round((float)module.getHudHeight() * module.getHudScale()));
        int margin = Math.max(24, Math.min(screenWidth, screenHeight) / 32);
        int contentTop = Math.max(84, margin * 3);
        int maxX = Math.max(margin, screenWidth - margin - width);
        int maxY = Math.max(contentTop, screenHeight - margin - height);
        ArrayList<HudRect> occupied = new ArrayList<HudRect>();
        for (Module other : this.modules) {
            if (other == module || !other.isEnabled() || !other.isHudVisible()) continue;
            occupied.add(HudRect.of(other).inflate(12));
        }
        HudRect current = new HudRect(module.getHudX(), module.getHudY(), width, height);
        boolean bl = safelyOnScreen = current.x >= margin && current.y >= contentTop && current.right() <= screenWidth - margin && current.bottom() <= screenHeight - margin;
        if (safelyOnScreen) {
            if (occupied.stream().noneMatch(current::intersects)) {
                return;
            }
        }
        int scanStep = 16;
        HudRect best = null;
        long bestOverlap = Long.MAX_VALUE;
        for (int y = contentTop; y <= maxY; y += scanStep) {
            for (int x = margin; x <= maxX; x += scanStep) {
                HudRect candidate = new HudRect(x, y, width, height);
                long overlap = occupied.stream().mapToLong(candidate::overlapArea).sum();
                if (overlap == 0L) {
                    module.setHudPosition(x, y);
                    return;
                }
                if (overlap >= bestOverlap) continue;
                bestOverlap = overlap;
                best = candidate;
            }
        }
        if (best != null) {
            module.setHudPosition(best.x, best.y);
        } else {
            module.setHudPosition(Math.min(margin, maxX), Math.min(contentTop, maxY));
        }
    }

    public List<HudPreset> getHudPresets() {
        return List.of(new HudPreset("pvp", "PvP Focus", "Large inputs, CPS, combo, armor, potions, ping and speed."), new HudPreset("stream", "Stream Clean", "Readable essentials arranged away from chat and crosshair."), new HudPreset("survival", "Survival Scout", "Detailed navigation, biome, day, armor, saturation and tools."), new HudPreset("performance", "Performance", "FPS, memory, ping and low-noise render helpers."), new HudPreset("builder", "Builder Utility", "Coordinates, direction, block overlay, tool alerts and clock."), new HudPreset("minimal", "Minimal Vanilla", "Small clean essentials with no competitive clutter."));
    }

    public void resetToDefaults() {
        for (Module module : this.modules) {
            ModuleSnapshot snapshot = this.defaultSnapshots.get(module.getName());
            if (snapshot == null) continue;
            this.applySnapshot(module, snapshot);
        }
        this.saveConfig();
    }

    public void resetAllRemoved() {
        for (Module module : this.modules) {
            module.setEnabled(false);
            ModuleSnapshot snapshot = this.defaultSnapshots.get(module.getName());
            if (snapshot == null) continue;
            module.setKeyBinding(snapshot.keyBinding, snapshot.keyModifiers);
            module.setHudPosition(snapshot.hudX, snapshot.hudY);
            module.setHudScale(snapshot.hudScale);
            this.applySettingSnapshot(module, snapshot.settings);
        }
        this.saveConfig();
    }

    public void applyHudPreset(String presetId) {
        this.resetAllRemoved();
        int screenW = Math.max(1, DisplaySpace.width());
        int screenH = Math.max(1, DisplaySpace.height());
        int left = 24;
        int top = 22;
        int centerX = screenW / 2;
        int rightWide = Math.max(left, screenW - 360);
        int rightMedium = Math.max(left, screenW - 280);
        int lowHud = Math.max(140, screenH - 180);
        int lowerCluster = Math.max(180, screenH - 300);
        int bottomLeft = Math.max(180, screenH - 250);
        switch (presetId) {
            case "pvp": {
                this.enable("PingDisplay", left, top, 1.35f);
                this.setting("PingDisplay", "opacity", 95.0);
                this.enable("FPS", left, top + 30, 1.35f);
                this.setting("FPS", "show_average", false);
                this.setting("FPS", "show_min", false);
                this.enable("SpeedHUD", left, top + 60, 1.35f);
                this.enable("Keystrokes", left, lowerCluster, 1.75f);
                this.setting("Keystrokes", "key_size", 24.0);
                this.setting("Keystrokes", "spacing", 3.0);
                this.setting("Keystrokes", "pressed_color", new Color(255, 102, 51));
                this.setting("Keystrokes", "normal_color", new Color(16, 21, 24));
                this.enable("CPSCounter", left, lowerCluster + 166, 1.55f);
                this.enable("ArmorHUD", Math.max(left, centerX - 210), lowHud, 1.5f);
                this.setting("ArmorHUD", "horizontal", true);
                this.setting("ArmorHUD", "display_mode", "percentage");
                this.enable("ComboCounter", Math.max(left, centerX - 82), Math.max(110, screenH - 330), 1.7f);
                this.setting("ComboCounter", "mid_color", new Color(255, 220, 80));
                this.setting("ComboCounter", "high_color", new Color(255, 102, 51));
                this.enable("PotionHUD", rightWide, top + 44, 1.45f);
                this.setting("PotionHUD", "opacity", 95.0);
                this.enable("DamageIndicator", 0, 0, 1.0f);
                this.enable("Crosshair", 0, 0, 1.0f);
                this.setting("Crosshair", "style", "cross");
                this.setting("Crosshair", "size", 7.0);
                this.setting("Crosshair", "gap", 3.0);
                this.enable("Sprint", 0, 0, 1.0f);
                break;
            }
            case "stream": {
                this.enable("FPS", left, top, 1.25f);
                this.setting("FPS", "show_average", true);
                this.setting("FPS", "show_min", false);
                this.enable("Coordinates", left, top + 34, 1.25f);
                this.setting("Coordinates", "display_mode", "minimal");
                this.setting("Coordinates", "show_direction", false);
                this.setting("Coordinates", "show_dimension", false);
                this.setting("Coordinates", "color", new Color(245, 245, 245));
                this.enable("Clock", rightMedium, top, 1.25f);
                this.setting("Clock", "format", "12h");
                this.enable("ArmorHUD", Math.max(left, centerX - 190), lowHud, 1.35f);
                this.setting("ArmorHUD", "horizontal", true);
                this.enable("PotionHUD", rightWide, top + 42, 1.2f);
                this.enable("ChatTimestamps", 0, 0, 1.0f);
                this.enable("ScoreboardMod", 0, 0, 1.0f);
                this.setting("ScoreboardMod", "hide_numbers", true);
                this.setting("ScoreboardMod", "custom_background", true);
                this.setting("ScoreboardMod", "background_opacity", 46.0);
                this.enable("Notifications", 0, 0, 1.0f);
                this.setting("Notifications", "duration", 2.0);
                break;
            }
            case "survival": {
                this.enable("Coordinates", left, top, 1.55f);
                this.setting("Coordinates", "display_mode", "detailed");
                this.setting("Coordinates", "show_biome", true);
                this.setting("Coordinates", "color_mode", "axis");
                this.enable("Biome", left, top + 148, 1.35f);
                this.setting("Biome", "color", new Color(100, 220, 150));
                this.enable("DirectionHUD", Math.max(left, centerX - 86), top, 1.55f);
                this.enable("DayCounter", rightMedium, top, 1.35f);
                this.setting("DayCounter", "show_time", true);
                this.enable("Clock", rightMedium, top + 36, 1.25f);
                this.setting("Clock", "format", "24h");
                this.enable("PotionHUD", rightWide, top + 78, 1.35f);
                this.enable("ArmorHUD", left, bottomLeft, 1.35f);
                this.setting("ArmorHUD", "horizontal", false);
                this.enable("Saturation", left, Math.max(140, screenH - 76), 1.25f);
                this.enable("ToolWarning", rightWide, Math.max(140, screenH - 176), 1.25f);
                break;
            }
            case "performance": {
                this.enable("FPS", left, top, 1.45f);
                this.setting("FPS", "show_average", true);
                this.setting("FPS", "show_min", true);
                this.setting("FPS", "color_mode", "dynamic");
                this.enable("Memory", left, top + 40, 1.35f);
                this.setting("Memory", "display", "both");
                this.enable("PingDisplay", left, top + 76, 1.35f);
                this.enable("ServerInfo", rightWide, top, 1.25f);
                this.enable("PackDisplay", rightWide, top + 34, 1.1f);
                this.enable("Particles", 0, 0, 1.0f);
                this.setting("Particles", "multiplier", 0.5);
                this.setting("Particles", "weather", false);
                this.setting("Particles", "smoke", false);
                this.enable("Fullbright", 0, 0, 1.0f);
                this.enable("NoBossBar", 0, 0, 1.0f);
                this.enable("PingOverlay", 0, 0, 1.0f);
                break;
            }
            case "builder": {
                this.enable("Coordinates", left, top, 1.45f);
                this.setting("Coordinates", "display_mode", "detailed");
                this.setting("Coordinates", "color_mode", "axis");
                this.setting("Coordinates", "show_biome", false);
                this.enable("DirectionHUD", Math.max(left, centerX - 86), top, 1.45f);
                this.enable("Block Overlay", 0, 0, 1.0f);
                this.setting("Block Overlay", "show_hud", true);
                this.setting("Block Overlay", "show_coords", true);
                this.setting("Block Overlay", "hud_position", "Top Center");
                this.setting("Block Overlay", "hud_offset_y", 58.0);
                this.setting("Block Overlay", "hud_mod_color", new Color(255, 102, 51));
                this.setting("Block Overlay", "fill_transparency", 36.0);
                this.setting("Block Overlay", "outline_transparency", 220.0);
                this.enable("Clock", rightMedium, top, 1.25f);
                this.setting("Clock", "format", "24h");
                this.enable("ToolWarning", rightWide, Math.max(140, screenH - 160), 1.2f);
                this.enable("PackDisplay", left, Math.max(120, screenH - 82), 1.1f);
                this.enable("ToggleSneak", 0, 0, 1.0f);
                this.enable("Crosshair", 0, 0, 1.0f);
                this.setting("Crosshair", "style", "square");
                this.setting("Crosshair", "size", 5.0);
                break;
            }
            case "minimal": {
                this.enable("FPS", left, top, 1.1f);
                this.setting("FPS", "show_average", false);
                this.setting("FPS", "show_min", false);
                this.setting("FPS", "background", false);
                this.enable("Coordinates", left, top + 26, 1.1f);
                this.setting("Coordinates", "display_mode", "minimal");
                this.setting("Coordinates", "show_direction", false);
                this.setting("Coordinates", "show_dimension", false);
                this.setting("Coordinates", "background", false);
                this.enable("DirectionHUD", Math.max(left, centerX - 70), top, 1.1f);
                this.setting("DirectionHUD", "background", false);
                break;
            }
            default: {
                return;
            }
        }
        this.saveConfig();
    }

    public void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            for (Module module : this.modules) {
                JsonObject moduleObj = new JsonObject();
                moduleObj.addProperty("enabled", Boolean.valueOf(module.isEnabled()));
                moduleObj.addProperty("keyBinding", (Number)module.getKeyBinding());
                moduleObj.addProperty("keyModifiers", (Number)module.getKeyModifiers());
                moduleObj.addProperty("hudX", (Number)module.getHudX());
                moduleObj.addProperty("hudY", (Number)module.getHudY());
                moduleObj.addProperty("hudScale", (Number)Float.valueOf(module.getHudScale()));
                JsonObject settingsObj = new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    Object value = setting.getValue();
                    if (value instanceof Boolean) {
                        settingsObj.addProperty(setting.getName(), (Boolean)value);
                        continue;
                    }
                    if (value instanceof Number) {
                        settingsObj.addProperty(setting.getName(), (Number)value);
                        continue;
                    }
                    if (value instanceof String) {
                        settingsObj.addProperty(setting.getName(), (String)value);
                        continue;
                    }
                    if (!(value instanceof Color)) continue;
                    Color color = (Color)value;
                    JsonObject colorObj = new JsonObject();
                    colorObj.addProperty("r", (Number)color.getRed());
                    colorObj.addProperty("g", (Number)color.getGreen());
                    colorObj.addProperty("b", (Number)color.getBlue());
                    colorObj.addProperty("a", (Number)color.getAlpha());
                    settingsObj.add(setting.getName(), (JsonElement)colorObj);
                }
                moduleObj.add("settings", (JsonElement)settingsObj);
                root.add(module.getName(), (JsonElement)moduleObj);
            }
            Files.createDirectories(this.configPath.getParent(), new FileAttribute[0]);
            Files.writeString(this.configPath, (CharSequence)this.gson.toJson((JsonElement)root), new OpenOption[0]);
            LOGGER.info("[ModuleManager] Config saved");
        }
        catch (Exception e) {
            LOGGER.error("[ModuleManager] Failed to save config: {}", (Object)e.getMessage());
        }
    }

    public void loadConfig() {
        if (!Files.exists(this.configPath, new LinkOption[0])) {
            LOGGER.info("[ModuleManager] No config file found, using defaults");
            return;
        }
        try {
            String json = Files.readString(this.configPath);
            JsonObject root = (JsonObject)this.gson.fromJson(json, JsonObject.class);
            for (Module module : this.modules) {
                if (!root.has(module.getName())) continue;
                JsonObject moduleObj = root.getAsJsonObject(module.getName());
                if (moduleObj.has("enabled") && !module.isHotkeyOnly()) {
                    module.setEnabled(moduleObj.get("enabled").getAsBoolean());
                }
                if (moduleObj.has("keyBinding")) {
                    module.setKeyBinding(moduleObj.get("keyBinding").getAsInt());
                }
                if (moduleObj.has("keyModifiers")) {
                    module.setKeyBinding(module.getKeyBinding(), moduleObj.get("keyModifiers").getAsInt());
                }
                if (moduleObj.has("hudX")) {
                    module.setHudX(moduleObj.get("hudX").getAsInt());
                }
                if (moduleObj.has("hudY")) {
                    module.setHudY(moduleObj.get("hudY").getAsInt());
                }
                if (moduleObj.has("hudScale")) {
                    module.setHudScale(moduleObj.get("hudScale").getAsFloat());
                }
                if (!moduleObj.has("settings")) continue;
                JsonObject settingsObj = moduleObj.getAsJsonObject("settings");
                for (Setting<?> setting : module.getSettings()) {
                    if (!settingsObj.has(setting.getName())) continue;
                    if (setting instanceof BooleanSetting) {
                        BooleanSetting bs = (BooleanSetting)setting;
                        bs.setValue(settingsObj.get(setting.getName()).getAsBoolean());
                        continue;
                    }
                    if (setting instanceof NumberSetting) {
                        NumberSetting ns = (NumberSetting)setting;
                        ns.setValue(settingsObj.get(setting.getName()).getAsDouble());
                        continue;
                    }
                    if (setting instanceof ModeSetting) {
                        ModeSetting ms = (ModeSetting)setting;
                        ms.setValue(settingsObj.get(setting.getName()).getAsString());
                        continue;
                    }
                    if (setting instanceof ColorSetting) {
                        ColorSetting cs = (ColorSetting)setting;
                        JsonObject colorObj = settingsObj.getAsJsonObject(setting.getName());
                        cs.setRGBA(colorObj.get("r").getAsInt(), colorObj.get("g").getAsInt(), colorObj.get("b").getAsInt(), colorObj.get("a").getAsInt());
                        continue;
                    }
                    if (setting instanceof TextSetting) {
                        TextSetting ts = (TextSetting)setting;
                        ts.setValue(settingsObj.get(setting.getName()).getAsString());
                        continue;
                    }
                    if (!(setting instanceof KeybindSetting)) continue;
                    KeybindSetting ks = (KeybindSetting)setting;
                    ks.setKey(settingsObj.get(setting.getName()).getAsInt());
                }
            }
            LOGGER.info("[ModuleManager] Config loaded");
        }
        catch (Exception e) {
            LOGGER.error("[ModuleManager] Failed to load config: {}", (Object)e.getMessage());
        }
    }

    private void captureDefaults() {
        this.defaultSnapshots.clear();
        for (Module module : this.modules) {
            this.defaultSnapshots.put(module.getName(), ModuleSnapshot.from(module));
        }
    }

    private void applySnapshot(Module module, ModuleSnapshot snapshot) {
        module.setEnabled(snapshot.enabled && !module.isHotkeyOnly());
        module.setKeyBinding(snapshot.keyBinding, snapshot.keyModifiers);
        module.setHudPosition(snapshot.hudX, snapshot.hudY);
        module.setHudScale(snapshot.hudScale);
        this.applySettingSnapshot(module, snapshot.settings);
    }

    private void applySettingSnapshot(Module module, Map<String, Object> settings) {
        for (Setting<?> setting : module.getSettings()) {
            if (!settings.containsKey(setting.getName())) continue;
            this.setSettingValue(setting, settings.get(setting.getName()));
        }
    }

    private void enable(String moduleName, int x, int y, float scale) {
        Module module = this.getModule(moduleName);
        if (module == null) {
            return;
        }
        module.setEnabled(true);
        module.setHudPosition(x, y);
        module.setHudScale(scale);
    }

    private void setting(String moduleName, String settingName, Object value) {
        Module module = this.getModule(moduleName);
        if (module == null) {
            return;
        }
        Object setting = module.getSetting(settingName);
        if (setting == null) {
            return;
        }
        this.setSettingValue((Setting<?>)setting, value);
    }

    /*
     * Enabled aggressive block sorting
     */
    private void setSettingValue(Setting<?> setting, Object value) {
        if (setting instanceof BooleanSetting) {
            BooleanSetting bs = (BooleanSetting)setting;
            if (value instanceof Boolean) {
                Boolean bool = (Boolean)value;
                bs.setValue(bool);
                return;
            }
        }
        if (setting instanceof NumberSetting) {
            NumberSetting ns = (NumberSetting)setting;
            if (value instanceof Number) {
                Number number = (Number)value;
                ns.setValue(number.doubleValue());
                return;
            }
        }
        if (setting instanceof ModeSetting) {
            ModeSetting ms = (ModeSetting)setting;
            if (value instanceof String) {
                String str = (String)value;
                ms.setValue(str);
                return;
            }
        }
        if (setting instanceof TextSetting) {
            TextSetting ts = (TextSetting)setting;
            if (value instanceof String) {
                String str = (String)value;
                ts.setValue(str);
                return;
            }
        }
        if (setting instanceof KeybindSetting) {
            KeybindSetting ks = (KeybindSetting)setting;
            if (value instanceof Number) {
                Number number = (Number)value;
                ks.setValue(number.intValue());
                return;
            }
        }
        if (setting instanceof ColorSetting) {
            ColorSetting cs = (ColorSetting)setting;
            if (value instanceof Color) {
                Color color = (Color)value;
                cs.setRGBA(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                return;
            }
        }
        setting.setValue(value);
    }

    private record HudRect(int x, int y, int width, int height) {
        private static HudRect of(Module module) {
            return new HudRect(module.getHudX(), module.getHudY(), Math.max(24, Math.round((float)module.getHudWidth() * module.getHudScale())), Math.max(18, Math.round((float)module.getHudHeight() * module.getHudScale())));
        }

        private int right() {
            return this.x + this.width;
        }

        private int bottom() {
            return this.y + this.height;
        }

        private boolean intersects(HudRect other) {
            return this.x < other.right() && this.right() > other.x && this.y < other.bottom() && this.bottom() > other.y;
        }

        private long overlapArea(HudRect other) {
            int overlapWidth = Math.max(0, Math.min(this.right(), other.right()) - Math.max(this.x, other.x));
            int overlapHeight = Math.max(0, Math.min(this.bottom(), other.bottom()) - Math.max(this.y, other.y));
            return (long)overlapWidth * (long)overlapHeight;
        }

        private HudRect inflate(int amount) {
            return new HudRect(this.x - amount, this.y - amount, this.width + amount * 2, this.height + amount * 2);
        }
    }

    public static final class HudPreset {
        private final String id;
        private final String name;
        private final String description;

        public HudPreset(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public String id() {
            return this.id;
        }

        public String name() {
            return this.name;
        }

        public String description() {
            return this.description;
        }
    }

    private static final class ModuleSnapshot {
        private final boolean enabled;
        private final int keyBinding;
        private final int keyModifiers;
        private final int hudX;
        private final int hudY;
        private final float hudScale;
        private final Map<String, Object> settings;

        private ModuleSnapshot(boolean enabled, int keyBinding, int keyModifiers, int hudX, int hudY, float hudScale, Map<String, Object> settings) {
            this.enabled = enabled;
            this.keyBinding = keyBinding;
            this.keyModifiers = keyModifiers;
            this.hudX = hudX;
            this.hudY = hudY;
            this.hudScale = hudScale;
            this.settings = settings;
        }

        private static ModuleSnapshot from(Module module) {
            HashMap<String, Object> settings = new HashMap<String, Object>();
            for (Setting<?> setting : module.getSettings()) {
                Object value = setting.getValue();
                if (value instanceof Color) {
                    Color color = (Color)value;
                    value = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                }
                settings.put(setting.getName(), value);
            }
            return new ModuleSnapshot(module.isEnabled(), module.getKeyBinding(), module.getKeyModifiers(), module.getHudX(), module.getHudY(), module.getHudScale(), settings);
        }
    }
}

