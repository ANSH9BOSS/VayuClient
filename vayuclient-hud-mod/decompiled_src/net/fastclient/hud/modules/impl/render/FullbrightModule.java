/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.NumberSetting;

public class FullbrightModule
extends Module {
    private final NumberSetting strength = this.register(new NumberSetting("strength", "Brightness level (10 = maximum)", 10.0, 1.0, 10.0, 1.0));

    public FullbrightModule() {
        super("Fullbright", "See in the dark like it's daytime", Category.RENDER);
        this.setKeyBinding(66);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    public float getStrength() {
        return this.strength.getFloatValue();
    }
}

