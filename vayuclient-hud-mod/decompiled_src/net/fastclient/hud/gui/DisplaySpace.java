/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.Window
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.resources.Identifier
 */
package net.fastclient.hud.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DisplaySpace {
    private DisplaySpace() {
    }

    public static int width() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, Math.round((float)window.getWidth() / DisplaySpace.renderScale()));
    }

    public static int height() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, Math.round((float)window.getHeight() / DisplaySpace.renderScale()));
    }

    public static int mouseX(double guiMouseX) {
        return (int)Math.round(guiMouseX * (double)DisplaySpace.guiScale() / (double)DisplaySpace.renderScale());
    }

    public static int mouseY(double guiMouseY) {
        return (int)Math.round(guiMouseY * (double)DisplaySpace.guiScale() / (double)DisplaySpace.renderScale());
    }

    public static int mouseDelta(double guiDelta) {
        return (int)Math.round(guiDelta * (double)DisplaySpace.guiScale() / (double)DisplaySpace.renderScale());
    }

    public static void push(GuiGraphicsExtractor graphics) {
        float invScale = DisplaySpace.renderScale() / DisplaySpace.guiScale();
        graphics.pose().pushMatrix();
        graphics.pose().scale(invScale, invScale);
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    public static float nativeTextCompensationScale() {
        return DisplaySpace.guiScale() / DisplaySpace.renderScale();
    }

    public static void enableScissor(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }

    public static Identifier texture(Identifier highDensityTexture) {
        if (DisplaySpace.renderScale() > 1.0f) {
            return highDensityTexture;
        }
        String path = highDensityTexture.getPath();
        int extension = path.lastIndexOf(46);
        String standardDensityPath = extension >= 0 ? path.substring(0, extension) + "_1x" + path.substring(extension) : path + "_1x";
        return Identifier.fromNamespaceAndPath((String)highDensityTexture.getNamespace(), (String)standardDensityPath);
    }

    private static float guiScale() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1.0f, (float)window.getGuiScale());
    }

    private static float renderScale() {
        Window window = Minecraft.getInstance().getWindow();
        float scaleX = (float)Math.max(1, window.getWidth()) / 1920.0f;
        float scaleY = (float)Math.max(1, window.getHeight()) / 1080.0f;
        float continuousScale = Math.min(scaleX, scaleY);
        return Math.max(1.0f, (float)Math.round(continuousScale));
    }
}

