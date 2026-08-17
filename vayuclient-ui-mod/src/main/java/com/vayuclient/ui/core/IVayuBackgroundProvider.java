package com.vayuclient.ui.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public interface IVayuBackgroundProvider {
    Identifier resolveBackground(String mcVersion, String theme);
    void renderBackgroundWithBlur(GuiGraphicsExtractor graphics, int width, int height, float delta);
}
