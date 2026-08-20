package com.vayuclient.hud.adapter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class RenderingAdapter {
    public static void fill(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color) {
        if (graphics == null) return;
        graphics.fill(minX, minY, maxX, maxY, color);
    }

    public static void drawString(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        if (graphics == null || font == null || text == null) return;
        graphics.text(font, text, x, y, color, shadow);
    }

    public static void drawComponent(GuiGraphicsExtractor graphics, Font font, Component component, int x, int y, int color, boolean shadow) {
        if (graphics == null || font == null || component == null) return;
        graphics.text(font, component, x, y, color, shadow);
    }

    public static int getStringWidth(Font font, String text) {
        if (font == null || text == null) return 0;
        return font.width(text);
    }

    public static int getComponentWidth(Font font, FormattedText text) {
        if (font == null || text == null) return 0;
        return font.width(text);
    }

    public static int getFontHeight(Font font) {
        if (font == null) return 9;
        return font.lineHeight;
    }

    public static void enableScissor(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (graphics == null) return;
        graphics.enableScissor(x, y, x + width, y + height);
    }

    public static void disableScissor(GuiGraphicsExtractor graphics) {
        if (graphics == null) return;
        graphics.disableScissor();
    }
}
