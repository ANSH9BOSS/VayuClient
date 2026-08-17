package com.vayuclient.ui.adapter;

import com.vayuclient.ui.core.IClientUIAdapter;
import com.vayuclient.ui.core.VayuCapability;
import com.vayuclient.ui.gui.VayuTitleScreen;
import com.vayuclient.ui.gui.VayuPauseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public class MinecraftUIAdapter_Generic implements IClientUIAdapter {

    @Override
    public String getAdapterId() {
        return "Generic-Fallback-Adapter";
    }

    @Override
    public String getSupportedVersion() {
        return "26.x-Compatible";
    }

    @Override
    public boolean supportsCapability(VayuCapability capability) {
        return true;
    }

    @Override
    public void onInitialize(Minecraft client) {
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
        // Deep obsidian futuristic backdrop with radial dark gradient approximation
        graphics.fill(0, 0, width, height, 0xFF080C14);
        
        // Top electric accent glow bar
        graphics.fill(0, 0, width, 2, 0xFF00D2FF);
        
        // Ambient deep blue corner gradient bands
        graphics.fill(0, 0, width / 2, height / 3, 0x150A84FF);
        graphics.fill(width / 2, height / 2, width, height, 0x1500D2FF);
    }

    @Override
    public void renderGlassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor, int borderColor) {
        // Outer translucent fill
        graphics.fill(x, y, x + width, y + height, bgColor);
        
        // 1px sleek illuminated border
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
        
        // Subtle top gloss highlight
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFFFFF);
    }
}
