/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.OptionInstance
 */
package com.vayuclient.hud.modules.impl.utility;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.OptionInstance;

public class ZoomModule
extends Module {
    private final NumberSetting zoomLevel = this.register(new NumberSetting("zoom_level", "Zoom multiplier", 4.0, 1.5, 10.0, 0.5));
    private final BooleanSetting holdToZoom = this.register(new BooleanSetting("hold_to_zoom", "Hold key to zoom (vs toggle)", true));
    private final BooleanSetting smoothZoom = this.register(new BooleanSetting("smooth_zoom", "Smooth zoom transition", false));
    private int savedFov = -1;
    private double animatedFov = -1.0;

    public ZoomModule() {
        super("Zoom", "Zoom the camera", Category.UTILITY);
        this.updateHoldMode();
    }

    private void updateHoldMode() {
        this.setKeyHeld(this.holdToZoom.isEnabled());
    }

    @Override
    public boolean isHotkeyOnly() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && !this.isInGame()) {
            return;
        }
        super.setEnabled(enabled);
    }

    @Override
    protected void onEnable() {
        this.updateHoldMode();
        if (ZoomModule.mc.options != null) {
            this.savedFov = (Integer)ZoomModule.mc.options.fov().get();
            double target = (double)this.savedFov / (Double)this.zoomLevel.getValue();
            if (this.smoothZoom.isEnabled()) {
                this.animatedFov = this.savedFov;
            } else {
                this.setFov(target);
            }
        }
    }

    @Override
    protected void onDisable() {
        if (ZoomModule.mc.options != null && this.savedFov > 0) {
            this.setFov(this.savedFov);
        }
        this.savedFov = -1;
        this.animatedFov = -1.0;
    }

    @Override
    public void onTick() {
        if (this.isKeyHeld() != this.holdToZoom.isEnabled()) {
            this.setKeyHeld(this.holdToZoom.isEnabled());
        }
        if (ZoomModule.mc.options == null || this.savedFov <= 0) {
            return;
        }
        double target = (double)this.savedFov / (Double)this.zoomLevel.getValue();
        if (this.smoothZoom.isEnabled()) {
            double diff = target - this.animatedFov;
            this.animatedFov = Math.abs(diff) > 0.5 ? (this.animatedFov += diff * 0.3) : target;
            this.setFov(this.animatedFov);
        } else {
            this.setFov(target);
        }
    }

    private void setFov(double value) {
        try {
            OptionInstance fov = ZoomModule.mc.options.fov();
            fov.set((Object)((int)Math.max(30.0, Math.min(110.0, value))));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

