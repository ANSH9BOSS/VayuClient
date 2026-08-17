package com.vayuclient.ui.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public interface IClientUIAdapter {
    String getAdapterId();
    String getSupportedVersion();
    boolean supportsCapability(VayuCapability capability);
    
    void onInitialize(Minecraft client);
    Screen createTitleScreen();
    Screen createPauseScreen(Screen parent);
    void renderBackground(GuiGraphicsExtractor graphics, int width, int height, float delta);
    void renderGlassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor, int borderColor);
}
