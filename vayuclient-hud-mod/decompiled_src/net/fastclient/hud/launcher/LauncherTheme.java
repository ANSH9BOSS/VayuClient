/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.launcher;

public final class LauncherTheme {
    public static final int OVERLAY = 1376389644;
    public static final int TEXT = -1;
    public static final int TEXT_SECONDARY = -7829368;
    public static final int BUTTON_BG = -652666594;
    public static final int BUTTON_HOVER = -450024137;
    public static final int STORE_GRADIENT_TOP = -45747;
    public static final int STORE_GRADIENT_BOT = -3073494;
    public static final int QUIT_BG = 1721111572;
    public static final int QUIT_HOVER = -2134368738;
    public static final int ICON_BG = -16118768;
    public static final int ICON_HOVER = -15526373;
    public static final int SHADOW = 0x40000000;
    public static final int BADGE_RED = -1096636;

    private LauncherTheme() {
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
}

