package com.vayuclient.ui.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IRenderBackend {
    void bindGraphics(GuiGraphicsExtractor graphics);
    void fill(int x1, int y1, int x2, int y2, int color);
    void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo);
    void drawText(String text, int x, int y, int color);
    int getTextWidth(String text);
    void renderGlassPanel(int x, int y, int width, int height, int bgColor, int borderColor);
    void renderBackground(int width, int height, float delta);
    void renderLoadingScreen(int width, int height, float progress, String statusMessage, float delta);
}
