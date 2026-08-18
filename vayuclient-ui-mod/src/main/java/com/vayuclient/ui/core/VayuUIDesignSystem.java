package com.vayuclient.ui.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class VayuUIDesignSystem {

    // ─── COLOR PALETTE & TOKENS ───────────────────────────────────────────────
    public static final int COLOR_BG_OVERLAY      = 0x88000000;
    public static final int COLOR_GLASS_PANEL     = 0xCC090D16;
    public static final int COLOR_GLASS_PANEL_2   = 0xDD0D131F;
    public static final int COLOR_GLASS_CARD      = 0xEE0F172A;
    public static final int COLOR_GLASS_BTN       = 0xAA131C2E;
    public static final int COLOR_GLASS_BTN_HOVER = 0xEE1E2B45;
    public static final int COLOR_GLASS_BTN_PRESS = 0xFF2A3C5E;
    public static final int COLOR_GLASS_GREEN     = 0xDD064E3B;
    public static final int COLOR_GLASS_GREEN_HOV = 0xFF047857;

    public static final int COLOR_BORDER_SUBTLE   = 0x22FFFFFF;
    public static final int COLOR_BORDER_MEDIUM   = 0x44FFFFFF;
    public static final int COLOR_BORDER_ACCENT   = 0x8800F0FF;
    public static final int COLOR_BORDER_GREEN    = 0x8810B981;

    public static final int COLOR_ACCENT_CYAN     = 0xFF00F0FF;
    public static final int COLOR_ACCENT_BLUE     = 0xFF38BDF8;
    public static final int COLOR_ACCENT_EMERALD  = 0xFF10B981;
    public static final int COLOR_ACCENT_GOLD     = 0xFFF59E0B;

    public static final int COLOR_TEXT_PRIMARY    = 0xFFFFFFFF;
    public static final int COLOR_TEXT_SECONDARY  = 0xFF94A3B8;
    public static final int COLOR_TEXT_MUTED      = 0xFF64748B;

    private VayuUIDesignSystem() {}

    // ─── EASING FUNCTIONS ─────────────────────────────────────────────────────
    public static float easeOutCubic(float t) {
        float f = 1.0f - t;
        return 1.0f - (f * f * f);
    }

    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 2) / 2.0f;
    }

    // ─── DRAWING PRIMITIVES ───────────────────────────────────────────────────
    public static void drawPill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor, int borderColor) {
        // Main body
        graphics.fill(x + 2, y, x + width - 2, y + height, bgColor);
        graphics.fill(x, y + 2, x + 2, y + height - 2, bgColor);
        graphics.fill(x + width - 2, y + 2, x + width, y + height - 2, bgColor);

        // Top & bottom border
        graphics.fill(x + 2, y, x + width - 2, y + 1, borderColor);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, borderColor);

        // Left & right border
        graphics.fill(x, y + 2, x + 1, y + height - 2, borderColor);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, borderColor);

        // Corner pixels for smooth pill rounding
        graphics.fill(x + 1, y + 1, x + 2, y + 2, borderColor);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor);
    }

    public static void drawGlassCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor, int borderColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y + 1, x + 1, y + height - 1, borderColor);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, borderColor);
    }

    public static void drawVayuEmblem(GuiGraphicsExtractor graphics, int centerX, int centerY, float pulse) {
        int size = 26;
        int glowColor = ((int) (0x44 * pulse) << 24) | 0x00F0FF;

        // Outer Hexagonal Glow Pill
        drawPill(graphics, centerX - size - 12, centerY - size - 6, (size * 2) + 24, (size * 2) + 12, 0x990A101C, glowColor);

        // Neon Cyan Core Emblem Geometry
        int topY = centerY - 14;
        int botY = centerY + 14;

        // Diamond / Wind Chevron lines
        graphics.fill(centerX - 2, topY, centerX + 2, topY + 6, COLOR_ACCENT_CYAN);
        graphics.fill(centerX - 12, centerY - 2, centerX - 6, centerY + 2, COLOR_ACCENT_CYAN);
        graphics.fill(centerX + 6, centerY - 2, centerX + 12, centerY + 2, COLOR_ACCENT_CYAN);
        graphics.fill(centerX - 2, botY - 6, centerX + 2, botY, COLOR_ACCENT_CYAN);

        // Center glowing nucleus
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, COLOR_ACCENT_CYAN);
        graphics.fill(centerX - 5, centerY - 1, centerX + 5, centerY + 1, COLOR_ACCENT_BLUE);
        graphics.fill(centerX - 1, centerY - 5, centerX + 1, centerY + 5, COLOR_ACCENT_BLUE);
    }
}
