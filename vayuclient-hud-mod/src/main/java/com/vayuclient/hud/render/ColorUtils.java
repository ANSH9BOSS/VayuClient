/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.render;

import java.awt.Color;

public class ColorUtils {
    public static int getRed(int color) {
        return color >> 16 & 0xFF;
    }

    public static int getGreen(int color) {
        return color >> 8 & 0xFF;
    }

    public static int getBlue(int color) {
        return color & 0xFF;
    }

    public static int getAlpha(int color) {
        return color >> 24 & 0xFF;
    }

    public static int getColor(int red, int green, int blue, int alpha) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static int getColor(int red, int green, int blue) {
        return ColorUtils.getColor(red, green, blue, 255);
    }

    public static int getRainbow(float offset, float saturation, float brightness) {
        float hue = (float)(System.currentTimeMillis() % 2000L) / 2000.0f + offset;
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    public static int getRainbow(float offset) {
        return ColorUtils.getRainbow(offset, 0.7f, 1.0f);
    }

    public static int withAlpha(int color, int alpha) {
        return color & 0xFFFFFF | alpha << 24;
    }
}

