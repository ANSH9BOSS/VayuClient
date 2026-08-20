/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.BossHealthOverlay
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.NoBossBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BossHealthOverlay.class})
public class BossBarMixin {
    @Inject(method={"extractRenderState"}, at={@At(value="HEAD")}, cancellable=true)
    private void onRender(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        NoBossBar noBossBar;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (noBossBar = mm.getModule(NoBossBar.class)) != null && noBossBar.isEnabled()) {
            ci.cancel();
        }
    }
}

