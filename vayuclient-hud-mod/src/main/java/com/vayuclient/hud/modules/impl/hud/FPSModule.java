/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 */
package com.vayuclient.hud.modules.impl.hud;

import java.util.LinkedList;
import java.util.Queue;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class FPSModule
extends Module {
    private static final int MUTED_TEXT = 0xAAAAAA;
    private final BooleanSetting showAverage = this.register(new BooleanSetting("show_average", "Show average FPS", true));
    private final BooleanSetting showMin = this.register(new BooleanSetting("show_min", "Show minimum FPS", true));
    private final ModeSetting colorMode = this.register(new ModeSetting("color_mode", "Color mode", "dynamic", new String[]{"dynamic", "static"}));
    private final ColorSetting staticColor = this.register(new ColorSetting("color", "Static color", 255, 255, 255));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));
    private final int[] fpsRingBuffer = new int[100];
    private int ringIndex = 0;
    private int ringCount = 0;
    private long ringSum = 0L;
    private int minFps = Integer.MAX_VALUE;
    private int avgFps = 0;
    private long lastUpdate = 0L;

    public FPSModule() {
        super("FPS", "Advanced FPS counter", Category.HUD);
        this.staticColor.visibleWhen(() -> this.colorMode.is("static"));
        this.bgOpacity.visibleWhen(this.background::isEnabled);
    }

    @Override
    protected void onEnable() {
        this.ringIndex = 0;
        this.ringCount = 0;
        this.ringSum = 0L;
        this.minFps = Integer.MAX_VALUE;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - this.lastUpdate > 50L) {
            int currentFps = mc.getFps();
            if (currentFps > 0) {
                if (this.ringCount < 100) {
                    this.ringSum += currentFps;
                    this.fpsRingBuffer[this.ringIndex] = currentFps;
                    this.ringIndex = (this.ringIndex + 1) % 100;
                    this.ringCount++;
                } else {
                    this.ringSum -= this.fpsRingBuffer[this.ringIndex];
                    this.ringSum += currentFps;
                    this.fpsRingBuffer[this.ringIndex] = currentFps;
                    this.ringIndex = (this.ringIndex + 1) % 100;
                }
                if (currentFps < this.minFps) {
                    this.minFps = currentFps;
                }
                this.avgFps = this.ringCount > 0 ? (int)(this.ringSum / this.ringCount) : currentFps;
            }
            this.lastUpdate = now;
        }
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        int fps = mc.getFps();
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        int color = this.getDisplayColor(fps);
        Component displayText = this.buildDisplayText(fps, color);
        if (((Boolean)this.background.getValue()).booleanValue()) {
            int width = FPSModule.mc.font.width((FormattedText)displayText);
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, width + 10, 17, this.bgOpacity.getIntValue());
        }
        graphics.text(FPSModule.mc.font, displayText, x, y, -1, true);
        graphics.pose().popMatrix();
    }

    private Component buildDisplayText(int fps, int color) {
        MutableComponent text = Component.empty().append((Component)Component.literal((String)("FPS: " + fps)).withStyle(Style.EMPTY.withColor(color & 0xFFFFFF)));
        if (((Boolean)this.showAverage.getValue()).booleanValue()) {
            text.append((Component)Component.literal((String)(" (Avg: " + this.avgFps + ")")).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }
        if (((Boolean)this.showMin.getValue()).booleanValue() && this.minFps != Integer.MAX_VALUE) {
            text.append((Component)Component.literal((String)(" (Min: " + this.minFps + ")")).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }
        return text;
    }

    private int getDisplayColor(int fps) {
        if (this.colorMode.is("static")) {
            return this.staticColor.getRGB();
        }
        if (fps >= 60) {
            return -11141291;
        }
        if (fps >= 30) {
            return -171;
        }
        return -43691;
    }

    @Override
    public int getHudWidth() {
        int fps = mc.getFps();
        return FPSModule.mc.font.width((FormattedText)this.buildDisplayText(fps, this.getDisplayColor(fps))) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

