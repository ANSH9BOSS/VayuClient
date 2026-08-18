package com.vayuclient.ui.adapter;

import com.vayuclient.ui.core.IClientUIAdapter;
import com.vayuclient.ui.core.VayuBackgroundProvider;
import com.vayuclient.ui.core.VayuCapability;
import com.vayuclient.ui.gui.VayuPauseScreen;
import com.vayuclient.ui.gui.VayuTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public class MinecraftUIAdapter_Generic implements IClientUIAdapter {

    @Override
    public String getAdapterId() {
        return "Minecraft_Generic_Fallback";
    }

    @Override
    public String getSupportedVersion() {
        return "Universal Fallback";
    }

    @Override
    public boolean supportsCapability(VayuCapability capability) {
        return capability == VayuCapability.GLASS_PANELS || capability == VayuCapability.CUSTOM_TITLE_SCREEN;
    }

    @Override
    public void onInitialize(Minecraft client) {
        System.out.println("[VayuClient UI] Initialized Generic Universal UI Adapter");
    }

    @Override
    public Screen createTitleScreen() {
        return new VayuTitleScreen();
    }

    @Override
    public Screen createPauseScreen(Screen parent) {
        return new VayuPauseScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor graphics, int width, int height, float delta) {
        VayuBackgroundProvider.getInstance().renderBackgroundWithBlur(graphics, width, height, delta);
    }

    @Override
    public void renderGlassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor, int borderColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y + 1, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    @Override
    public void renderLoadingScreen(GuiGraphicsExtractor graphics, int width, int height, float progress, String statusMessage, float delta) {
        renderBackground(graphics, width, height, delta);

        int centerX = width / 2;
        int centerY = height / 2;

        int cardWidth = 300;
        int cardHeight = 110;
        int cardX = centerX - cardWidth / 2;
        int cardY = centerY - cardHeight / 2;

        renderGlassPanel(graphics, cardX, cardY, cardWidth, cardHeight, 0xD0080D1A, 0x4400D2FF);

        Font font = Minecraft.getInstance().font;
        if (font != null) {
            String title = "VAYUCLIENT";
            int titleWidth = font.width(title);
            graphics.text(font, title, centerX - titleWidth / 2, cardY + 18, 0x00E5FF);

            String status = statusMessage != null && !statusMessage.isEmpty() ? statusMessage : "Starting VayuClient...";
            int statusWidth = font.width(status);
            graphics.text(font, status, centerX - statusWidth / 2, cardY + 40, 0x94A3B8);
        }

        int barWidth = 220;
        int barHeight = 4;
        int barX = centerX - barWidth / 2;
        int barY = cardY + 68;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF1E293B);
        int filled = (int) (barWidth * Math.max(0.0f, Math.min(1.0f, progress)));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, 0xFF00D2FF);
        }
    }
}
