package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class Slider extends UIComponent {
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

        // 1. Setting Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.y + 4, labelColor);

        // 2. Numeric Readout Pill (e.g. 100.0)
        String valueStr = (this.step >= 1.0 && this.min == (int)this.min && this.max == (int)this.max)
            ? String.format("%d", (int)Math.round(this.value))
            : String.format("%.1f", this.value);
        int valTextW = this.uiTextWidth(mc, valueStr);
        int pillPad = 6;
        int pillW = valTextW + pillPad * 2;
        int pillH = 15;
        int pillX = this.x + this.width - pillW - 12;
        int pillY = this.y + 2;

        VayuHUDUI.roundedRect(graphics, pillX, pillY, pillW, pillH, 4, this.dragging ? 0xFF0284C7 : 0xFF162235);
        VayuHUDUI.roundedOutline(graphics, pillX, pillY, pillW, pillH, 4, this.dragging ? 0xFF38BDF8 : 0x4438BDF8);
        this.drawUiText(graphics, mc, valueStr, pillX + pillPad, pillY + 3, this.dragging ? 0xFFFFFFFF : 0xFF38BDF8);

        // 3. Track Dimensions
        int trackY = this.y + 22;
        int trackHeight = 6;
        int trackPadding = 12;
        int trackWidth = this.width - trackPadding * 2;

        // Inactive Track
        VayuHUDUI.roundedRect(graphics, this.x + trackPadding, trackY, trackWidth, trackHeight, 3, 0xFF1E293B);
        VayuHUDUI.roundedOutline(graphics, this.x + trackPadding, trackY, trackWidth, trackHeight, 3, 0x3338BDF8);

        // Active Track
        double percent = Math.max(0.0, Math.min(1.0, (this.value - this.min) / (this.max - this.min)));
        int fillWidth = (int)((double)trackWidth * percent);
        if (fillWidth > 0) {
            VayuHUDUI.roundedRect(graphics, this.x + trackPadding, trackY, fillWidth, trackHeight, 3, 0xFF0284C7);
            graphics.fill(this.x + trackPadding, trackY, this.x + trackPadding + fillWidth, trackY + 1, 0xFF38BDF8);
        }

        // 4. Handle Thumb Knob
        int handleSize = this.dragging ? 14 : 12;
        int handleX = this.x + trackPadding + fillWidth;
        int handleCenterY = trackY + trackHeight / 2;
        int handleColor = this.dragging ? 0xFFFFFFFF : 0xFF38BDF8;

        VayuHUDUI.roundedRect(graphics, handleX - handleSize / 2, handleCenterY - handleSize / 2, handleSize, handleSize, handleSize / 2, handleColor);
        VayuHUDUI.roundedOutline(graphics, handleX - handleSize / 2, handleCenterY - handleSize / 2, handleSize, handleSize, handleSize / 2, 0xFF0284C7);

        // 5. Min / Max Labels
        String minStr = String.format("%.0f", this.min);
        String maxStr = String.format("%.0f", this.max);
        this.drawUiText(graphics, mc, minStr, this.x + trackPadding, trackY + trackHeight + 4, 0xFF64748B);
        this.drawUiText(graphics, mc, maxStr, this.x + this.width - trackPadding - this.uiTextWidth(mc, maxStr), trackY + trackHeight + 4, 0xFF64748B);
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
        double relativeX = mouseX - (double)(this.x + trackPadding);
        double percent = Math.max(0.0, Math.min(1.0, relativeX / (double)trackWidth));
        double rawValue = this.min + percent * (this.max - this.min);
        if (this.step > 0.0) {
            rawValue = Math.round(rawValue / this.step) * this.step;
        }
        this.value = Math.max(this.min, Math.min(this.max, rawValue));
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
