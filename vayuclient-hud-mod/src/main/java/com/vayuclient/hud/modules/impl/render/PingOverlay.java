/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;

public class PingOverlay
extends Module {
    private final BooleanSetting colorCoded = this.register(new BooleanSetting("color_coded", "Color code by ping quality", true));
    private final BooleanSetting showMs = this.register(new BooleanSetting("show_ms", "Show 'ms' suffix", true));

    public PingOverlay() {
        super("PingOverlay", "Show numeric ping on the player list", Category.RENDER);
    }

    public String formatPing(int latency) {
        if (latency <= 0) {
            return "?";
        }
        return this.showMs.isEnabled() ? latency + "ms" : String.valueOf(latency);
    }

    public int getPingColor(int latency) {
        if (!this.colorCoded.isEnabled()) {
            return -1;
        }
        if (latency <= 0) {
            return -5592406;
        }
        if (latency < 75) {
            return -16711936;
        }
        if (latency < 150) {
            return -8323328;
        }
        if (latency < 300) {
            return -256;
        }
        if (latency < 600) {
            return Short.MIN_VALUE;
        }
        return -49088;
    }
}

