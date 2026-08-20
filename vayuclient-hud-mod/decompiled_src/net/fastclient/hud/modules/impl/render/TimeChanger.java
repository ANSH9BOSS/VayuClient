/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;

public class TimeChanger
extends Module {
    private final ModeSetting mode = this.register(new ModeSetting("mode", "Time preset", "custom", new String[]{"day", "night", "sunset", "custom"}));
    private final NumberSetting customTime = this.register(new NumberSetting("custom_time", "Custom time value", 6000.0, 0.0, 24000.0, 100.0));

    public TimeChanger() {
        super("TimeChanger", "Changes the visual time of day", Category.RENDER);
        this.customTime.visibleWhen(() -> this.mode.is("custom"));
    }

    public long getCustomTime() {
        if (this.mode.is("day")) {
            return 6000L;
        }
        if (this.mode.is("night")) {
            return 18000L;
        }
        if (this.mode.is("sunset")) {
            return 12000L;
        }
        return ((Double)this.customTime.getValue()).longValue();
    }
}

