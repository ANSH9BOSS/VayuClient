/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.render;

import java.util.Locale;

public final class Theme {
    public static final int PRIMARY = -38091;
    public static final int PRIMARY_LIGHT = -28836;
    public static final int PRIMARY_DARK = -2862818;
    public static final int PRIMARY_GLOW = 1090480949;
    public static final int ACCENT = -22016;
    public static final int ACCENT_CYAN = -16721409;
    public static final int BACKGROUND = -1609231072;
    public static final int BACKGROUND_LIGHT = -1608639699;
    public static final int BACKGROUND_DARK = -1341388268;
    public static final int BACKGROUND_PANEL = -1608902870;
    public static final int SURFACE = -14738133;
    public static final int SURFACE_LIGHT = -14343370;
    public static final int SURFACE_HOVER = -14014403;
    public static final int TEXT = -1;
    public static final int TEXT_SECONDARY = -3356452;
    public static final int TEXT_TERTIARY = -6646352;
    public static final int TEXT_DISABLED = -10857099;
    public static final int TEXT_HIGHLIGHT = -38091;
    public static final int TEXT_SUCCESS = -11870592;
    public static final int TEXT_ERROR = -38037;
    public static final int SUCCESS = -11870592;
    public static final int WARNING = -17613;
    public static final int ERROR = -38037;
    public static final int CARD_BACKGROUND = -1608902870;
    public static final int CARD_BACKGROUND_HOVER = -1339743434;
    public static final int BUTTON_NORMAL = -1608178890;
    public static final int BUTTON_HOVER = -1339085502;
    public static final int BUTTON_PRESSED = -1877338072;
    public static final int TOGGLE_OFF = -12764590;
    public static final int TOGGLE_OFF_HANDLE = -8751728;
    public static final int TOGGLE_ON = -38091;
    public static final int TOGGLE_ON_HANDLE = -1;
    public static final int SLIDER_TRACK = -13817282;
    public static final int SLIDER_FILL = -38091;
    public static final int SLIDER_HANDLE = -1;
    public static final int BORDER = -13817282;
    public static final int BORDER_LIGHT = -12764590;
    public static final int SCROLLBAR = -12764590;
    public static final int SCROLLBAR_THUMB = -10856592;
    public static final int SCROLLBAR_THUMB_HOVER = -38091;
    public static final int CATEGORY_HUD = -38091;
    public static final int CATEGORY_RENDER = -6596170;
    public static final int CATEGORY_UTILITY = -1671646;

    private Theme() {
    }

    public static int withAlpha(int color, int alpha) {
        return color & 0xFFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    public static int blend(int color1, int color2, float ratio) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        int a1 = color1 >> 24 & 0xFF;
        int r1 = color1 >> 16 & 0xFF;
        int g1 = color1 >> 8 & 0xFF;
        int b1 = color1 & 0xFF;
        int a2 = color2 >> 24 & 0xFF;
        int r2 = color2 >> 16 & 0xFF;
        int g2 = color2 >> 8 & 0xFF;
        int b2 = color2 & 0xFF;
        int a = (int)((float)a1 + (float)(a2 - a1) * ratio);
        int r = (int)((float)r1 + (float)(r2 - r1) * ratio);
        int g = (int)((float)g1 + (float)(g2 - g1) * ratio);
        int b = (int)((float)b1 + (float)(b2 - b1) * ratio);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int lighten(int color, float amount) {
        return Theme.blend(color, -1, amount);
    }

    public static int darken(int color, float amount) {
        return Theme.blend(color, -16777216, amount);
    }

    public static int getCategoryColor(String categoryId) {
        return switch (categoryId.toLowerCase(Locale.ROOT)) {
            case "hud" -> -38091;
            case "render" -> -6596170;
            case "utility" -> -1671646;
            default -> -38091;
        };
    }

    public static String formatSettingName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String result = name.replace("_", " ");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < result.length(); ++i) {
            char c = result.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isSpaceChar(result.charAt(i - 1))) {
                formatted.append(' ');
            }
            formatted.append(c);
        }
        String[] words = formatted.toString().trim().split("\\s+");
        StringBuilder titleCase = new StringBuilder();
        for (int i = 0; i < words.length; ++i) {
            String word;
            if (i > 0) {
                titleCase.append(" ");
            }
            if ((word = words[i].toLowerCase(Locale.ROOT)).isEmpty()) continue;
            titleCase.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() <= 1) continue;
            titleCase.append(word.substring(1));
        }
        return titleCase.toString();
    }
}

