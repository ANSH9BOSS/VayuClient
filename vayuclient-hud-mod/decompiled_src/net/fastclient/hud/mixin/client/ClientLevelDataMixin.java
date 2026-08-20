/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.Level
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.TimeChanger;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Level.class})
public class ClientLevelDataMixin {
    @Inject(method={"getOverworldClockTime"}, at={@At(value="HEAD")}, cancellable=true)
    private void onGetDayTime(CallbackInfoReturnable<Long> cir) {
        TimeChanger timeChanger;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (timeChanger = mm.getModule(TimeChanger.class)) != null && timeChanger.isEnabled()) {
            cir.setReturnValue((Object)timeChanger.getCustomTime());
        }
    }
}

