package com.vayuclient.ui.adapter;

import com.vayuclient.ui.core.VayuBackgroundProvider;
import com.vayuclient.ui.platform.IRenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModernRenderBackend implements IRenderBackend {
    private GuiGraphicsExtractor currentGraphics;

    @Override
    public void bindGraphics(GuiGraphicsExtractor graphics) {
        this.currentGraphics = graphics;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (currentGraphics != null) {
            currentGraphics.fill(x1, y1, x2, y2, color);
        }
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        if (currentGraphics != null) {
            currentGraphics.fillGradient(x1, y1, x2, y2, colorFrom, colorTo);
        }
    }

    @Override
    public void drawText(String text, int x, int y, int color) {
        if (currentGraphics != null && text != null) {
            Font font = Minecraft.getInstance().font;
            if (font != null) {
                currentGraphics.text(font, text, x, y, color);
            }
        }
    }

    @Override
    public int getTextWidth(String text) {
        if (text == null) return 0;
        Font font = Minecraft.getInstance().font;
        return font != null ? font.width(text) : text.length() * 6;
    }

    @Override
    public void renderGlassPanel(int x, int y, int width, int height, int bgColor, int borderColor) {
        if (currentGraphics == null) return;
        currentGraphics.fill(x, y, x + width, y + height, bgColor);
        currentGraphics.fill(x, y, x + width, y + 1, borderColor);
        currentGraphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        currentGraphics.fill(x, y + 1, y + height, borderColor);
        currentGraphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    @Override
    public void renderBackground(int width, int height, float delta) {
        if (currentGraphics == null) return;
        VayuBackgroundProvider.getInstance().renderBackgroundWithBlur(currentGraphics, width, height, delta);
    }

    @Override
    public void renderLoadingScreen(int width, int height, float progress, String statusMessage, float delta) {
        if (currentGraphics == null) return;
        renderBackground(width, height, delta);

        int centerX = width / 2;
        int centerY = height / 2;

        int cardWidth = 340;
        int cardHeight = 130;
        int cardX = centerX - cardWidth / 2;
        int cardY = centerY - cardHeight / 2;

        renderGlassPanel(cardX, cardY, cardWidth, cardHeight, 0xD8050914, 0x5500D2FF);

        String title = "VAYUCLIENT";
        int titleWidth = getTextWidth(title);
        drawText(title, centerX - titleWidth / 2, cardY + 22, 0x00E5FF);

        String status = statusMessage != null && !statusMessage.isEmpty() ? statusMessage : "Starting VayuClient...";
        int statusWidth = getTextWidth(status);
        drawText(status, centerX - statusWidth / 2, cardY + 46, 0x94A3B8);

        String verTag = "Next-Gen Minecraft Runtime";
        int verWidth = getTextWidth(verTag);
        drawText(verTag, centerX - verWidth / 2, cardY + 62, 0x475569);

        int barWidth = 260;
        int barHeight = 4;
        int barX = centerX - barWidth / 2;
        int barY = cardY + 88;

        fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF1E293B);
        int filled = (int) (barWidth * Math.max(0.0f, Math.min(1.0f, progress)));
        if (filled > 0) {
            fill(barX, barY, barX + filled, barY + barHeight, 0xFF00D2FF);
        }
    }
}
