/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.network.chat.Component
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.fastclient.hud.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Module {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"FastClientHUD");
    protected static final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String displayName;
    private final String description;
    private final Category category;
    private boolean enabled;
    private int keyBinding;
    private int keyModifiers;
    private boolean keyHeld;
    private int hudX = 5;
    private int hudY = 5;
    private float hudScale = 2.0f;
    private final List<Setting<?>> settings = new ArrayList();
    public static final float MIN_SCALE = 0.5f;
    public static final float DEFAULT_SCALE = 2.0f;
    public static final float MAX_SCALE = 10.0f;

    public Module(String name, String description, Category category) {
        this(name, Module.readableName(name), description, category);
    }

    public Module(String name, String displayName, String description, Category category) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.enabled = false;
        this.keyBinding = 0;
        this.keyModifiers = 0;
    }

    public void toggle() {
        this.setEnabled(!this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnable();
                LOGGER.info("[{}] enabled", (Object)this.name);
            } else {
                this.onDisable();
                LOGGER.info("[{}] disabled", (Object)this.name);
            }
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected <T extends Setting<?>> T register(T setting) {
        this.settings.add(setting);
        return setting;
    }

    public void onTick() {
    }

    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
    }

    protected void sendMessage(String message) {
        if (Module.mc.player != null) {
            Module.mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a77[\u00a7bFastClientHUD\u00a77] \u00a7f" + message)));
        }
    }

    protected boolean isInGame() {
        return Module.mc.player != null && Module.mc.level != null;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getKeyBinding() {
        return this.keyBinding;
    }

    public int getKeyModifiers() {
        return this.keyModifiers;
    }

    public boolean isKeyHeld() {
        return this.keyHeld;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(this.settings);
    }

    public int getHudX() {
        return this.hudX;
    }

    public int getHudY() {
        return this.hudY;
    }

    public float getHudScale() {
        return this.hudScale;
    }

    public int getHudWidth() {
        return 80;
    }

    public int getHudHeight() {
        return 12;
    }

    public boolean isHudVisible() {
        return this.category == Category.HUD;
    }

    public boolean isHotkeyOnly() {
        return false;
    }

    public void setKeyBinding(int keyBinding) {
        this.keyBinding = keyBinding;
    }

    public void setKeyBinding(int keyBinding, int modifiers) {
        this.keyBinding = keyBinding;
        this.keyModifiers = modifiers;
    }

    public void setKeyHeld(boolean keyHeld) {
        this.keyHeld = keyHeld;
    }

    public void setHudX(int x) {
        this.hudX = x;
    }

    public void setHudY(int y) {
        this.hudY = y;
    }

    public void setHudPosition(int x, int y) {
        this.hudX = x;
        this.hudY = y;
    }

    public void setHudScale(float scale) {
        this.hudScale = Math.max(0.5f, Math.min(10.0f, scale));
    }

    private static String readableName(String name) {
        return switch (name) {
            case "ArmorHUD" -> "Armor Status";
            case "ArrayList" -> "Active Modules";
            case "CPSCounter" -> "CPS Counter";
            case "ChatTimestamps" -> "Chat Timestamps";
            case "ComboCounter" -> "Combo Counter";
            case "DirectionHUD" -> "Direction";
            case "Memory" -> "Memory Usage";
            case "PackDisplay" -> "Resource Packs";
            case "PingDisplay" -> "Ping";
            case "PotionHUD" -> "Potion Effects";
            case "ServerInfo" -> "Server Info";
            case "SpeedHUD" -> "Speed";
            case "ToggleSneak" -> "Toggle Sneak";
            case "Crosshair" -> "Custom Crosshair";
            case "DamageIndicator" -> "Damage Indicator";
            case "Fullbright" -> "Full Bright";
            case "Hitbox" -> "Hitboxes";
            case "NametagIcon" -> "FastClient Nametag";
            case "NoBossBar" -> "Hide Boss Bar";
            case "NoHurtCam" -> "Remove Hurt Shake";
            case "Particles" -> "Particle Effects";
            case "PingOverlay" -> "Tab Ping";
            case "ScoreboardMod" -> "Custom Scoreboard";
            case "TimeChanger" -> "Time Changer";
            case "Sprint" -> "Auto Sprint";
            case "AutoGG" -> "Auto GG";
            case "AutoText" -> "Quick Messages";
            case "GUISettings" -> "Interface Settings";
            case "ToolWarning" -> "Durability Warning";
            default -> Module.splitCamelCase(name);
        };
    }

    private static String splitCamelCase(String name) {
        String spaced = name.replace('_', ' ').replace('-', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return spaced.trim().isEmpty() ? name : spaced.trim();
    }

    public <T extends Setting<?>> T getSetting(String name) {
        for (Setting<?> setting : this.settings) {
            if (!setting.getName().equalsIgnoreCase(name)) continue;
            return (T)setting;
        }
        return null;
    }
}

