/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package net.fastclient.hud.launcher;

import net.fastclient.hud.launcher.LauncherTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LauncherIcons {
    private LauncherIcons() {
    }

    private static void hLine(GuiGraphicsExtractor g, int x1, int y, int x2, int color) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2), y + 1, color);
    }

    private static void vLine(GuiGraphicsExtractor g, int x, int y1, int y2, int color) {
        g.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2), color);
    }

    public static void singleplayer(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int headTop = y + 2;
        int headH = size * 2 / 5;
        g.fill(cx - headH / 2, headTop, cx + headH / 2, headTop + headH, color);
        g.fill(cx - size / 2 + 2, headTop + headH + 1, cx + size / 2 - 1, headTop + headH + 3, color);
        g.fill(cx - 2, headTop + headH + 2, cx + 2, y + size - 2, color);
    }

    public static void multiplayer(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int gap = size / 5;
        int half = (size - gap) / 2;
        LauncherIcons.singleplayer(g, x, y, half, color);
        LauncherIcons.singleplayer(g, x + half + gap, y, half, color);
    }

    public static void star(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;
        int d = r * 2 / 3;
        g.fill(cx - d, cy - r / 2, cx + d, cy + r / 2, color);
        g.fill(cx - r / 2, cy - d, cx + r / 2, cy + d, color);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, LauncherTheme.withAlpha(color, 200));
    }

    public static void tshirt(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cx = x + size / 2;
        g.fill(x + 1, y + 3, x + size - 1, y + 5, color);
        g.fill(cx - 2, y + 2, cx + 2, y + 6, 0);
        g.fill(cx - 4, y + 5, cx + 4, y + size - 2, color);
        g.fill(cx - 5, y + size - 3, cx + 5, y + size - 1, color);
    }

    public static void cart(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x + 3, y + 4, x + size - 3, y + size - 4, color);
        g.fill(x + 3, y + 4, x + size - 3, y + 6, 0);
        LauncherIcons.hLine(g, x + 2, y + 4, x + size - 2, color);
        g.fill(x + 3, y + size - 6, x + 7, y + size - 2, color);
        g.fill(x + size - 8, y + size - 6, x + size - 3, y + size - 2, color);
        int hx = x + 1;
        LauncherIcons.vLine(g, hx, y + 1, y + size - 4, color);
        LauncherIcons.hLine(g, hx, y + 1, x + 4, color);
    }

    public static void grid(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int pad = size / 6;
        int cell = (size - pad * 3) / 2;
        int r0 = x + pad;
        int r1 = r0 + cell + pad;
        int c0 = y + pad;
        int c1 = c0 + cell + pad;
        g.fill(r0, c0, r0 + cell, c0 + cell, color);
        g.fill(r1, c0, r1 + cell, c0 + cell, color);
        g.fill(r0, c1, r0 + cell, c1 + cell, color);
        g.fill(r1, c1, r1 + cell, c1 + cell, color);
    }

    public static void chat(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x + 1, y + 1, x + size - 4, y + size - 5, color);
        g.fill(x + size - 5, y + size - 6, x + size - 2, y + size - 2, color);
        g.fill(x + size - 6, y + size - 5, x + size - 3, y + size - 1, color);
        g.fill(x + 3, y + 3, x + size - 6, y + size - 7, 0);
        int lw = size / 2;
        LauncherIcons.hLine(g, x + 5, y + 6, x + 5 + lw, color);
        LauncherIcons.hLine(g, x + 5, y + 10, x + 5 + lw - 1, color);
    }

    public static void globe(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int sliceW;
        int i;
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;
        for (i = -r; i <= r; ++i) {
            sliceW = (int)Math.sqrt(r * r - i * i);
            g.fill(cx - sliceW, cy + i, cx + sliceW, cy + i + 1, color);
        }
        for (i = -r + 2; i <= r - 2; ++i) {
            sliceW = (int)Math.sqrt(r * r - i * i) - 2;
            g.fill(cx - sliceW, cy + i, cx + sliceW, cy + i + 1, 0);
        }
        LauncherIcons.hLine(g, cx - r + 1, cy, cx + r - 1, color);
        LauncherIcons.vLine(g, cx, cy - r + 1, cy + r - 1, color);
    }

    public static void diamond(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int w;
        int row;
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;
        for (row = 0; row <= r; ++row) {
            w = row * 2 + 1;
            g.fill(cx - row, cy - r + row, cx - row + w, cy - r + row + 1, color);
        }
        for (row = 1; row <= r; ++row) {
            w = (r - row) * 2 + 1;
            g.fill(cx - (r - row), cy + row, cx - (r - row) + w, cy + row + 1, color);
        }
        LauncherIcons.hLine(g, cx - r, cy, cx + r, color);
        g.fill(cx - r / 3, cy - r / 2, cx + r / 3, cy - r / 2 + 1, LauncherTheme.withAlpha(color, 120));
    }

    public static void clipboard(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int w = size - 4;
        int h = size - 2;
        g.fill(x + 2, y + 3, x + 2 + w, y + 3 + h, color);
        g.fill(x + 4, y + 5, x + w, y + h + 1, 0);
        g.fill(x + w / 3, y, x + w * 2 / 3, y + 5, color);
        int ty = y + 7;
        for (int i = 0; i < 4; ++i) {
            LauncherIcons.hLine(g, x + 5, ty + i * 4, x + w - 2, color);
        }
    }
}

