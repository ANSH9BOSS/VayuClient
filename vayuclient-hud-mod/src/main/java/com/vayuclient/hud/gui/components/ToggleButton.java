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

public class ToggleButton
extends UIComponent {
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
        if (this.hoverProgress > 0.01f) {
            int bgAlpha = (int)(15.0f * this.hoverProgress);
            VayuHUDUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 4, VayuHUDUI.withAlpha(-266722777, bgAlpha));
        }
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(-7303024, -723724, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);
        int toggleWidth = 36;
        int toggleHeight = 18;
        int toggleX = this.x + this.width - toggleWidth - 12;
        int toggleY = this.y + (this.height - toggleHeight) / 2;
        int trackColor = VayuHUDUI.blend(-435153640, -15511009, this.toggleProgress);
        VayuHUDUI.roundedRect(graphics, toggleX, toggleY, toggleWidth, toggleHeight, 9, trackColor);
        int handlePadding = 3;
        int handleWidth = 12;
        int handleX = toggleX + handlePadding + (int)((float)(toggleWidth - handleWidth - handlePadding * 2) * this.toggleProgress);
        int handleY = toggleY + handlePadding;
        int handleHeight = toggleHeight - handlePadding * 2;
        int handleColor = this.enabled ? -723724 : -7303024;
        VayuHUDUI.roundedRect(graphics, handleX, handleY, handleWidth, handleHeight, 6, handleColor);
        String status = this.enabled ? "ON" : "OFF";
        int statusColor = this.enabled ? -15243738 : -9934744;
        int statusX = toggleX - this.uiTextWidth(mc, status) - 8;
        this.drawUiText(graphics, mc, status, statusX, this.centeredTextY(mc, this.y, this.height), statusColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl) && event.button() == 0) {
            boolean bl2 = this.enabled = !this.enabled;
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

