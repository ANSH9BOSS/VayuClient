/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import net.fastclient.hud.modules.settings.Setting;

public class ModeSetting
extends Setting<String> {
    private final String[] modes;

    public ModeSetting(String name, String description, String defaultValue, String[] modes) {
        super(name, description, defaultValue);
        this.modes = modes;
    }

    public String[] getModes() {
        return this.modes;
    }

    public boolean is(String mode) {
        return ((String)this.getValue()).equalsIgnoreCase(mode);
    }

    public void cycle() {
        for (int i = 0; i < this.modes.length; ++i) {
            if (!this.modes[i].equalsIgnoreCase((String)this.getValue())) continue;
            this.setValue(this.modes[(i + 1) % this.modes.length]);
            return;
        }
    }
}

