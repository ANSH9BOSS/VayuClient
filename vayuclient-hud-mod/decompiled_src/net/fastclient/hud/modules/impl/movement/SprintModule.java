/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.impl.movement;

import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;

public class SprintModule
extends Module {
    public SprintModule() {
        super("Sprint", "Auto sprint when moving forward", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (!this.isInGame()) {
            return;
        }
        if (SprintModule.mc.player.input.hasForwardImpulse() && !SprintModule.mc.player.isCrouching()) {
            SprintModule.mc.player.setSprinting(true);
        }
    }
}

