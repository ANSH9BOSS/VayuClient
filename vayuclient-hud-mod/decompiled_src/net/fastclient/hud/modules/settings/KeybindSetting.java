/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import net.fastclient.hud.modules.settings.Setting;

public class KeybindSetting
extends Setting<Integer> {
    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public int getKey() {
        return (Integer)this.getValue();
    }

    public void setKey(int key) {
        this.setValue(key);
    }
}

