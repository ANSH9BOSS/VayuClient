package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ToggleButton extends UIComponent {
    private final String label;
    private boolean enabled;
    private final Consumer<Boolean> onChange;
    private float toggleProgress;
    private float hoverProgress;
    private long lastUpdate = System.currentTimeMillis();

    public ToggleButton(int x, int y, int width, int height, String label, boolean enabled, Consumer<Boolean> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.enabled = enabled;
        this.onChange = onChange;
        this.toggleProgress = enabled ? 1.0f : 0.0f;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.toggleProgress = AnimationUtils.smoothDelta(this.toggleProgress, this.enabled ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();

        // 1. Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);

        // 2. Switch dimensions
        int toggleWidth = 40;
        int toggleHeight = 20;
        int toggleX = this.x + this.width - toggleWidth - 12;
        int toggleY = this.y + (this.height - toggleHeight) / 2;

        // 3. Switch Track (Dark Slate to Electric Cyan)
        int trackOff = 0xFF1E293B;
        int trackOn = 0xFF0284C7;
        int trackColor = VayuHUDUI.blend(trackOff, trackOn, this.toggleProgress);
        int trackBorder = VayuHUDUI.blend(0x4438BDF8, 0xFF38BDF8, this.toggleProgress);

        VayuHUDUI.roundedRect(graphics, toggleX, toggleY, toggleWidth, toggleHeight, 10, trackColor);
        VayuHUDUI.roundedOutline(graphics, toggleX, toggleY, toggleWidth, toggleHeight, 10, trackBorder);

        // 4. Switch Knob (Smooth sliding pill)
        int handlePadding = 3;
        int handleWidth = 14;
        int handleHeight = toggleHeight - handlePadding * 2;
        int handleX = toggleX + handlePadding + (int)((float)(toggleWidth - handleWidth - handlePadding * 2) * this.toggleProgress);
        int handleY = toggleY + handlePadding;
        int handleColor = this.enabled ? 0xFFFFFFFF : 0xFF94A3B8;

        VayuHUDUI.roundedRect(graphics, handleX, handleY, handleWidth, handleHeight, 7, handleColor);

        // 5. ON / OFF Text Indicator
        String status = this.enabled ? "ON" : "OFF";
        int statusColor = this.enabled ? 0xFF38BDF8 : 0xFF64748B;
        int statusX = toggleX - this.uiTextWidth(mc, status) - 8;
        this.drawUiText(graphics, mc, status, statusX, this.centeredTextY(mc, this.y, this.height), statusColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl) && event.button() == 0) {
            this.enabled = !this.enabled;
            if (this.onChange != null) {
                this.onChange.accept(this.enabled);
            }
            return true;
        }
        return false;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
