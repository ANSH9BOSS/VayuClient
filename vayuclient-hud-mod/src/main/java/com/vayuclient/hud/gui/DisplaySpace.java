package com.vayuclient.hud.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DisplaySpace {
    private DisplaySpace() {
    }

    public static int width() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, window.getGuiScaledWidth());
    }

    public static int height() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, window.getGuiScaledHeight());
    }

    public static int mouseX(double guiMouseX) {
        return (int) Math.round(guiMouseX);
    }

    public static int mouseY(double guiMouseY) {
        return (int) Math.round(guiMouseY);
    }

    public static int mouseDelta(double guiDelta) {
        return (int) Math.round(guiDelta);
    }

    public static void push(GuiGraphicsExtractor graphics) {
        // Native Minecraft GUI coordinate space — zero matrix distortion or scaling overhead
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        // Native Minecraft GUI coordinate space
    }

    public static float nativeTextCompensationScale() {
        return 1.0f;
    }

    public static void enableScissor(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }

    public static Identifier texture(Identifier texture) {
        return texture;
    }
}
