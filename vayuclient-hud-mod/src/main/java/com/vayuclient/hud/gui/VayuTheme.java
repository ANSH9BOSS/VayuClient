package com.vayuclient.hud.gui;

public final class VayuTheme {
    // Primary Brand Colors (Vayu Cyber)
    public static final int PRIMARY = 0xFF00D9FF;       // Bright Vayu Cyan
    public static final int SECONDARY = 0xFF38BDF8;     // Sky Cyan Accent
    public static final int DEEP_BLUE = 0xFF0284C7;     // Deep Ocean Blue
    public static final int DARK_CYAN = 0xFF0891B2;

    // Background & Surfaces (Obsidian Dark)
    public static final int BG_DARK = 0xFF050A10;       // Base Obsidian
    public static final int BG_VIGNETTE = 0xD803070C;
    public static final int GLASS_PANEL = 0xD00A111A;   // 82% Obsidian Glass
    public static final int GLASS_HOVER = 0xE60F1722;   // Hover Glass
    public static final int SURFACE_PANEL = 0xFF0F1722; // Solid Panel Surface
    public static final int SURFACE_CARD = 0xFF141E2D;  // Card Surface
    public static final int SURFACE_CARD_HOVER = 0xFF1B283C;

    // Borders & Outlines
    public static final int BORDER_SUBTLE = 0x2638BDF8; // 15% Cyan Border
    public static final int BORDER_HOVER = 0x6600D9FF;  // 40% Cyan Border
    public static final int BORDER_ACTIVE = 0xCC00D9FF; // 80% Cyan Glow Border
    public static final int BORDER_GLASS = 0x1AFFFFFF;  // 10% White Glass

    // Text Hierarchy
    public static final int TEXT_PRIMARY = 0xFFF1F5F9;  // Crisp White
    public static final int TEXT_SECONDARY = 0xFFCBD5E1;// Light Gray
    public static final int TEXT_MUTED = 0xFF94A3B8;    // Muted Gray
    public static final int TEXT_CYAN = 0xFF38BDF8;     // Accent Cyan Text

    // Functional State Colors
    public static final int DANGER = 0xFFEF4444;        // Crimson Red
    public static final int DANGER_HOVER = 0xFFDC2626;
    public static final int SUCCESS = 0xFF22C55E;       // Emerald Green
    public static final int WARNING = 0xFFF59E0B;       // Amber Yellow

    // Hero Button Gradients
    public static final int HERO_GRADIENT_START = 0xFF00D9FF;
    public static final int HERO_GRADIENT_END = 0xFF0284C7;
    public static final int HERO_HOVER_START = 0xFF38BDF8;
    public static final int HERO_HOVER_END = 0xFF0369A1;

    private VayuTheme() {}

    // Easing Interpolation Helpers
    public static float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float)Math.pow(-2.0f * t + 2.0f, 3.0) / 2.0f;
    }

    public static float easeOutQuad(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0.0f, Math.min(1.0f, t));
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
            if ((word = words[i].toLowerCase(java.util.Locale.ROOT)).isEmpty()) continue;
            titleCase.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() <= 1) continue;
            titleCase.append(word.substring(1));
        }
        return titleCase.toString();
    }
}
