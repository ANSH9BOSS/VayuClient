/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.resources.Identifier
 */
package com.vayuclient.hud.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.modules.Module;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class VayuHUDUI {
    public static final int OVERLAY = -1207959552;
    public static final int PANEL = -234156528;
    public static final int PANEL_SOFT = -435219433;
    public static final int CARD = -653586413;
    public static final int CARD_HOVER = -434955745;
    public static final int BUTTON = -435153640;
    public static final int BUTTON_HOVER = -266722777;
    public static final int PRIMARY = -16723201; // 0xFF00D2FF (Vayu Cyan)
    public static final int PRIMARY_HOVER = -13058312; // 0xFF38BDF8
    public static final int PRIMARY_DARK = -16612153; // 0xFF0284C7
    public static final int CYAN = -16723201;
    public static final int CYAN_HOVER = -13058312;
    public static final int RED = -1096636; // 0xFFEF4444
    public static final int RED_HOVER = -2349530; // 0xFFDC2626
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

    private VayuHUDUI() {
    }

    private static void put(String moduleName, String fileName) {
        ICONS.put(moduleName, fileName);
    }

    public static Identifier icon(Module module) {
        String file = ICONS.getOrDefault(module.getName().toLowerCase(Locale.ROOT), "ui-scaling.png");
        return DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)"vayuclient-hud", (String)("textures/gui/icons/" + file)));
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
        VayuHUDUI.roundedRect(graphics, x, y, w, h, radius, borderColor);
        if (w > 2 && h > 2) {
            VayuHUDUI.roundedRect(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fillColor);
        }
    }

    public static void hudPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        VayuHUDUI.hudPanel(graphics, x, y, w, h, 0);
    }

    public static void hudPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int opacity) {
        if (opacity <= 0) {
            return;
        }
        int safeW = Math.max(1, w);
        int safeH = Math.max(1, h);
        int clampedOpacity = Math.max(0, Math.min(255, opacity));
        graphics.fill(x, y, x + safeW, y + safeH, VayuHUDUI.withAlpha(-1978658025, clampedOpacity));
        VayuHUDUI.outline(graphics, x, y, safeW, safeH, VayuHUDUI.withAlpha(1154997472, Math.min(96, clampedOpacity)));
    }

    public static void hudPanelStrong(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        // Transparent default for clean modern aesthetics
    }

    public static void hudText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        VayuHUDUI.hudText(graphics, font, text, x, y, color, shadow, 0);
    }

    public static void hudText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow, int opacity) {
        if (opacity > 0) {
            int cleanWidth = font.width(text);
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, cleanWidth + 10, 17, opacity);
        }
        graphics.text(font, text, x, y, color, shadow);
    }

    public static void hudTwoLine(GuiGraphicsExtractor graphics, Font font, String primary, String secondary, int x, int y, int primaryColor, int secondaryColor) {
        VayuHUDUI.hudTwoLine(graphics, font, primary, secondary, x, y, primaryColor, secondaryColor, 0);
    }

    public static void hudTwoLine(GuiGraphicsExtractor graphics, Font font, String primary, String secondary, int x, int y, int primaryColor, int secondaryColor, int opacity) {
        if (opacity > 0) {
            int width = Math.max(font.width(primary), font.width(secondary));
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, width + 10, 28, opacity);
        }
        graphics.text(font, primary, x, y, primaryColor, true);
        graphics.text(font, secondary, x, y + 11, secondaryColor, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // PURE VECTOR GEOMETRIC ICONS (Crisp, High-DPI, 0 Broken Glyphs)
    // ═══════════════════════════════════════════════════════════════

    public static void drawPlayVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        int left = cx - half / 2;
        int right = cx + half;
        int top = cy - half;
        int bottom = cy + half;
        for (int y = top; y <= bottom; ++y) {
            float t = (float) (y - top) / (float) (bottom - top);
            int curRight = left + (int) ((right - left) * (t <= 0.5f ? t * 2.0f : (1.0f - t) * 2.0f));
            graphics.fill(left, y, Math.max(left + 1, curRight), y + 1, color);
        }
    }

    public static void drawMultiplayerVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int r = size / 3;
        // Two overlapping player silhouettes / globe nodes
        roundedRect(graphics, cx - r - 2, cy - r, r * 2, r * 2, r, color);
        roundedRect(graphics, cx - r + 4, cy - r + 3, r * 2, r * 2, r, withAlpha(color, 180));
    }

    public static void drawSettingsVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        int inner = size / 4;
        roundedOutline(graphics, cx - half, cy - half, size, size, 2, color);
        rect(graphics, cx - inner, cy - inner, inner * 2, inner * 2, color);
        // Cross teeth
        rect(graphics, cx - 1, cy - half - 2, 2, 2, color);
        rect(graphics, cx - 1, cy + half, 2, 2, color);
        rect(graphics, cx - half - 2, cy - 1, 2, 2, color);
        rect(graphics, cx + half, cy - 1, 2, 2, color);
    }

    public static void drawModsVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        int tooth = size / 4;
        roundedRect(graphics, cx - half, cy - half, size, size, 3, color);
        // Puzzle cutout
        rect(graphics, cx - tooth / 2, cy - half - 2, tooth, 2, withAlpha(color, 255));
        rect(graphics, cx + half, cy - tooth / 2, 2, tooth, withAlpha(color, 255));
    }

    public static void drawCloseVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int d = -half; d <= half; ++d) {
            rect(graphics, cx + d - 1, cy + d - 1, 2, 2, color);
            rect(graphics, cx - d - 1, cy + d - 1, 2, 2, color);
        }
    }

    public static void drawCheckVector(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int i = 0; i <= half / 2; ++i) {
            rect(graphics, cx - half + i, cy + i, 2, 2, color);
        }
        for (int i = 0; i <= half; ++i) {
            rect(graphics, cx - half / 2 + i, cy + half / 2 - i, 2, 2, color);
        }
    }

    static {
        VayuHUDUI.put("armorhud", "armor-status.png");
        VayuHUDUI.put("arraylist", "tablist.png");
        VayuHUDUI.put("autogg", "toast-cpmtrpl.png");
        VayuHUDUI.put("autotext", "autotext.png");
        VayuHUDUI.put("biome", "horses.png");
        VayuHUDUI.put("block overlay", "block-overlay-.png");
        VayuHUDUI.put("chattimestamps", "custom-chat.png");
        VayuHUDUI.put("clock", "stopwatch.png");
        VayuHUDUI.put("combocounter", "combo-display.png");
        VayuHUDUI.put("coordinates", "coordinates.png");
        VayuHUDUI.put("cpscounter", "cps.png");
        VayuHUDUI.put("crosshair", "crosshair.png");
        VayuHUDUI.put("damageindicator", "damage-indicator.png");
        VayuHUDUI.put("daycounter", "playtime.png");
        VayuHUDUI.put("directionhud", "direction-.png");
        VayuHUDUI.put("fps", "fps.png");
        VayuHUDUI.put("fullbright", "brightness.png");
        VayuHUDUI.put("guisettings", "ui-scaling.png");
        VayuHUDUI.put("hitbox", "hitbox.png");
        VayuHUDUI.put("keystrokes", "keystokes.png");
        VayuHUDUI.put("memory", "system-resources.png");
        VayuHUDUI.put("motion blur", "motion-blur.png");
        VayuHUDUI.put("nametagicon", "playermodel.png");
        VayuHUDUI.put("nobossbar", "hearts.png");
        VayuHUDUI.put("nohurtcam", "hit-indicator.png");
        VayuHUDUI.put("notifications", "toast-cpmtrpl.png");
        VayuHUDUI.put("packdisplay", "pack-display.png");
        VayuHUDUI.put("particles", "color-saturarion.png");
        VayuHUDUI.put("pingdisplay", "ping.png");
        VayuHUDUI.put("pingoverlay", "ping.png");
        VayuHUDUI.put("potionhud", "potion.png");
        VayuHUDUI.put("saturation", "saturation.png");
        VayuHUDUI.put("scoreboardmod", "scoreboard.png");
        VayuHUDUI.put("serverinfo", "server-adress.png");
        VayuHUDUI.put("speedhud", "speed-meter.png");
        VayuHUDUI.put("sprint", "toggle-sprint.png");
        VayuHUDUI.put("timechanger", "nightmode.png");
        VayuHUDUI.put("togglesneak", "toggle-sprint.png");
        VayuHUDUI.put("toolwarning", "tooltips.png");
        VayuHUDUI.put("waypoints", "direction-.png");
        VayuHUDUI.put("zoom", "fov.png");
    }
}

