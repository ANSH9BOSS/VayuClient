/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.resources.Identifier
 */
package net.fastclient.hud.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.modules.Module;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class FastClientUI {
    public static final int OVERLAY = -1207959552;
    public static final int PANEL = -234156528;
    public static final int PANEL_SOFT = -435219433;
    public static final int CARD = -653586413;
    public static final int CARD_HOVER = -434955745;
    public static final int BUTTON = -435153640;
    public static final int BUTTON_HOVER = -266722777;
    public static final int RED = -39373;
    public static final int RED_HOVER = -34227;
    public static final int GREEN = -15511009;
    public static final int GREEN_HOVER = -15243738;
    public static final int GREEN_TEXT = -8658034;
    public static final int BORDER = 1143616571;
    public static final int TEXT = -723724;
    public static final int TEXT_SOFT = -4671304;
    public static final int TEXT_MUTED = -7303024;
    public static final int TEXT_DIM = -9934744;
    public static final int WHITE_ICON = -1;
    public static final int HUD_SURFACE = -1978658025;
    public static final int HUD_SURFACE_STRONG = -1475275496;
    public static final int HUD_BORDER = 1725422816;
    public static final int HUD_BORDER_SOFT = 1154997472;
    public static final int HUD_TEXT = -1;
    public static final int HUD_TEXT_MUTED = -3025448;
    private static final Map<String, String> ICONS = new HashMap<String, String>();

    private FastClientUI() {
    }

    private static void put(String moduleName, String fileName) {
        ICONS.put(moduleName, fileName);
    }

    public static Identifier icon(Module module) {
        String file = ICONS.getOrDefault(module.getName().toLowerCase(Locale.ROOT), "ui-scaling.png");
        return DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)("textures/gui/icons/" + file)));
    }

    public static int withAlpha(int color, int alpha) {
        return color & 0xFFFFFF | (alpha & 0xFF) << 24;
    }

    public static int blend(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int aa = a >> 24 & 0xFF;
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int ba = b >> 24 & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        return (int)((float)aa + (float)(ba - aa) * t) << 24 | (int)((float)ar + (float)(br - ar) * t) << 16 | (int)((float)ag + (float)(bg - ag) * t) << 8 | (int)((float)ab + (float)(bb - ab) * t);
    }

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        graphics.fill(x + r, y, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; ++i) {
            int dx = r - i;
            graphics.fill(x + dx, y + i, x + w - dx, y + i + 1, color);
            graphics.fill(x + dx, y + h - i - 1, x + w - dx, y + h - i, color);
        }
    }

    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        graphics.fill(x + r, y, x + w - r, y + 1, color);
        graphics.fill(x + r, y + h - 1, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + 1, y + h - r, color);
        graphics.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; ++i) {
            int dx = r - i;
            graphics.fill(x + dx, y + i, x + dx + 1, y + i + 1, color);
            graphics.fill(x + w - dx - 1, y + i, x + w - dx, y + i + 1, color);
            graphics.fill(x + dx, y + h - i - 1, x + dx + 1, y + h - i, color);
            graphics.fill(x + w - dx - 1, y + h - i - 1, x + w - dx, y + h - i, color);
        }
    }

    public static void borderedRoundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int fillColor, int borderColor) {
        FastClientUI.roundedRect(graphics, x, y, w, h, radius, borderColor);
        if (w > 2 && h > 2) {
            FastClientUI.roundedRect(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fillColor);
        }
    }

    public static void hudPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        FastClientUI.hudPanel(graphics, x, y, w, h, 138);
    }

    public static void hudPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int opacity) {
        int safeW = Math.max(1, w);
        int safeH = Math.max(1, h);
        int clampedOpacity = Math.max(0, Math.min(255, opacity));
        graphics.fill(x, y, x + safeW, y + safeH, FastClientUI.withAlpha(-1978658025, clampedOpacity));
        FastClientUI.outline(graphics, x, y, safeW, safeH, FastClientUI.withAlpha(1154997472, Math.min(96, clampedOpacity)));
    }

    public static void hudPanelStrong(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int safeW = Math.max(1, w);
        int safeH = Math.max(1, h);
        graphics.fill(x, y, x + safeW, y + safeH, -1475275496);
        FastClientUI.outline(graphics, x, y, safeW, safeH, 1725422816);
    }

    public static void hudText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        FastClientUI.hudText(graphics, font, text, x, y, color, shadow, 138);
    }

    public static void hudText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow, int opacity) {
        int cleanWidth = font.width(text.replaceAll("\u00a7.", ""));
        FastClientUI.hudPanel(graphics, x - 5, y - 4, cleanWidth + 10, 17, opacity);
        graphics.text(font, text, x, y, color, shadow);
    }

    public static void hudTwoLine(GuiGraphicsExtractor graphics, Font font, String primary, String secondary, int x, int y, int primaryColor, int secondaryColor) {
        FastClientUI.hudTwoLine(graphics, font, primary, secondary, x, y, primaryColor, secondaryColor, 138);
    }

    public static void hudTwoLine(GuiGraphicsExtractor graphics, Font font, String primary, String secondary, int x, int y, int primaryColor, int secondaryColor, int opacity) {
        int width = Math.max(font.width(primary), font.width(secondary));
        FastClientUI.hudPanel(graphics, x - 5, y - 4, width + 10, 28, opacity);
        graphics.text(font, primary, x, y, primaryColor, true);
        graphics.text(font, secondary, x, y + 11, secondaryColor, true);
    }

    static {
        FastClientUI.put("armorhud", "armor-status.png");
        FastClientUI.put("arraylist", "tablist.png");
        FastClientUI.put("autogg", "toast-cpmtrpl.png");
        FastClientUI.put("autotext", "autotext.png");
        FastClientUI.put("biome", "horses.png");
        FastClientUI.put("block overlay", "block-overlay-.png");
        FastClientUI.put("chattimestamps", "custom-chat.png");
        FastClientUI.put("clock", "stopwatch.png");
        FastClientUI.put("combocounter", "combo-display.png");
        FastClientUI.put("coordinates", "coordinates.png");
        FastClientUI.put("cpscounter", "cps.png");
        FastClientUI.put("crosshair", "crosshair.png");
        FastClientUI.put("damageindicator", "damage-indicator.png");
        FastClientUI.put("daycounter", "playtime.png");
        FastClientUI.put("directionhud", "direction-.png");
        FastClientUI.put("fps", "fps.png");
        FastClientUI.put("fullbright", "brightness.png");
        FastClientUI.put("guisettings", "ui-scaling.png");
        FastClientUI.put("hitbox", "hitbox.png");
        FastClientUI.put("keystrokes", "keystokes.png");
        FastClientUI.put("memory", "system-resources.png");
        FastClientUI.put("motion blur", "motion-blur.png");
        FastClientUI.put("nametagicon", "playermodel.png");
        FastClientUI.put("nobossbar", "hearts.png");
        FastClientUI.put("nohurtcam", "hit-indicator.png");
        FastClientUI.put("notifications", "toast-cpmtrpl.png");
        FastClientUI.put("packdisplay", "pack-display.png");
        FastClientUI.put("particles", "color-saturarion.png");
        FastClientUI.put("pingdisplay", "ping.png");
        FastClientUI.put("pingoverlay", "ping.png");
        FastClientUI.put("potionhud", "potion.png");
        FastClientUI.put("saturation", "saturation.png");
        FastClientUI.put("scoreboardmod", "scoreboard.png");
        FastClientUI.put("serverinfo", "server-adress.png");
        FastClientUI.put("speedhud", "speed-meter.png");
        FastClientUI.put("sprint", "toggle-sprint.png");
        FastClientUI.put("timechanger", "nightmode.png");
        FastClientUI.put("togglesneak", "toggle-sprint.png");
        FastClientUI.put("toolwarning", "tooltips.png");
        FastClientUI.put("waypoints", "direction-.png");
        FastClientUI.put("zoom", "fov.png");
    }
}

