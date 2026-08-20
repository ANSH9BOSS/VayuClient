/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.Lightmap
 *  net.minecraft.client.renderer.state.LightmapRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.FullbrightModule;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Lightmap.class})
public abstract class LightmapTextureManagerMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void onRender(LightmapRenderState state, CallbackInfo ci) {
        FullbrightModule fullbright;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (fullbright = mm.getModule(FullbrightModule.class)) != null && fullbright.isEnabled()) {
            state.brightness = fullbright.getStrength();
        }
    }
}

