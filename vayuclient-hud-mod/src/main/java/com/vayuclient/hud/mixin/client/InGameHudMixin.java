/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.Hud
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.Crosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Hud.class})
public class InGameHudMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method={"extractCrosshair"}, at={@At(value="HEAD")}, cancellable=true)
    private void onRenderCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Crosshair crosshairModule;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (crosshairModule = mm.getModule(Crosshair.class)) != null && crosshairModule.isEnabled()) {
            ci.cancel();
        }
    }
}

