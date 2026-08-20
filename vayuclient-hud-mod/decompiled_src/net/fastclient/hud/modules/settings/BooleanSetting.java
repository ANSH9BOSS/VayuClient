/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import net.fastclient.hud.modules.settings.Setting;

public class BooleanSetting
extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isEnabled() {
        return (Boolean)this.getValue();
    }

    public void toggle() {
        this.setValue((Boolean)this.getValue() == false);
    }
}

