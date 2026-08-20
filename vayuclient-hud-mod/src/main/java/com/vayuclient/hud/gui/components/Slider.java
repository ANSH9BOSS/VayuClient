/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 */
package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class Slider
extends UIComponent {
    private final String label;
    private double value;
    private final double min;
    private final double max;
    private final double step;
    private final Consumer<Double> onChange;
    private boolean dragging = false;
    private float hoverProgress;
    private float dragProgress;
    private long lastUpdate = System.currentTimeMillis();

    public Slider(int x, int y, int width, int height, String label, double value, double min, double max, double step, Consumer<Double> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChange = onChange;
        this.value = Math.max(min, Math.min(max, value));
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered || this.dragging ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.dragProgress = AnimationUtils.smoothDelta(this.dragProgress, this.dragging ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        if (this.dragging) {
            this.updateValue(mouseX);
        }
        Minecraft mc = Minecraft.getInstance();
        if (this.hoverProgress > 0.01f) {
            int bgAlpha = (int)(15.0f * this.hoverProgress);
            VayuHUDUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 4, VayuHUDUI.withAlpha(-266722777, bgAlpha));
        }
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(-7303024, -723724, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.y + 4, labelColor);
        String valueStr = String.format("%.1f", this.value);
        int valueX = this.x + this.width - this.uiTextWidth(mc, valueStr) - 12;
        int valueColor = this.dragging ? -16723201 : -7303024;
        this.drawUiText(graphics, mc, valueStr, valueX, this.y + 4, valueColor);
        int trackY = this.y + 20;
        int trackHeight = 6;
        int trackPadding = 12;
        int trackWidth = this.width - trackPadding * 2;
        VayuHUDUI.roundedRect(graphics, this.x + trackPadding, trackY, trackWidth, trackHeight, 3, -435153640);
        double percent = (this.value - this.min) / (this.max - this.min);
        int fillWidth = (int)((double)trackWidth * percent);
        VayuHUDUI.roundedRect(graphics, this.x + trackPadding, trackY, fillWidth, trackHeight, 3, -16723201);
        int handleBaseSize = 10;
        int handleSize = handleBaseSize;
        int handleX = this.x + trackPadding + fillWidth;
        int handleCenterY = trackY + trackHeight / 2;
        int handleColor = this.dragging ? -13058312 : -723724;
        VayuHUDUI.roundedRect(graphics, handleX - handleSize / 2, handleCenterY - handleSize / 2, handleSize, handleSize, handleSize / 2, handleColor);
        String minStr = String.format("%.0f", this.min);
        String maxStr = String.format("%.0f", this.max);
        this.drawUiText(graphics, mc, minStr, this.x + trackPadding, trackY + trackHeight + 4, -9934744);
        this.drawUiText(graphics, mc, maxStr, this.x + this.width - trackPadding - this.uiTextWidth(mc, maxStr), trackY + trackHeight + 4, -9934744);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl) && event.button() == 0) {
            this.dragging = true;
            this.updateValue(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragging) {
            this.updateValue(event.x());
            return true;
        }
        return false;
    }

    private void updateValue(double mouseX) {
        int trackPadding = 12;
        int trackWidth = this.width - trackPadding * 2;
        double percent = Math.max(0.0, Math.min(1.0, (mouseX - (double)this.x - (double)trackPadding) / (double)trackWidth));
        double newValue = this.min + (this.max - this.min) * percent;
        newValue = (double)Math.round(newValue / this.step) * this.step;
        this.value = Math.max(this.min, Math.min(this.max, newValue));
        if (this.onChange != null) {
            this.onChange.accept(this.value);
        }
    }

    public double getValue() {
        return this.value;
    }

    public void setValue(double value) {
        this.value = Math.max(this.min, Math.min(this.max, value));
    }
}

