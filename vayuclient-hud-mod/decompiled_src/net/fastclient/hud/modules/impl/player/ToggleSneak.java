/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants
 *  com.mojang.blaze3d.platform.Window
 */
package net.fastclient.hud.modules.impl.player;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;

public class ToggleSneak
extends Module {
    private boolean sneakToggled = false;
    private boolean wasKeyPressed = false;

    public ToggleSneak() {
        super("ToggleSneak", "Press sneak once to toggle sneaking on/off - great for building!", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        this.sneakToggled = false;
        this.wasKeyPressed = false;
    }

    @Override
    public void onTick() {
        if (!this.isInGame() || ToggleSneak.mc.player == null) {
            return;
        }
        int sneakKey = ToggleSneak.mc.options.keyShift.getDefaultKey().getValue();
        boolean isKeyPressed = InputConstants.isKeyDown((Window)mc.getWindow(), (int)sneakKey);
        if (isKeyPressed && !this.wasKeyPressed) {
            this.sneakToggled = !this.sneakToggled;
        }
        this.wasKeyPressed = isKeyPressed;
        if (this.sneakToggled) {
            ToggleSneak.mc.options.keyShift.setDown(true);
        }
    }

    @Override
    protected void onDisable() {
        this.sneakToggled = false;
        if (ToggleSneak.mc.options != null) {
            ToggleSneak.mc.options.keyShift.setDown(false);
        }
    }

    public boolean isSneakToggled() {
        return this.sneakToggled;
    }
}

