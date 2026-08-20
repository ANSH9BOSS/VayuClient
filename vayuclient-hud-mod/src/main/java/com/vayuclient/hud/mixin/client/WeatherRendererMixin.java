/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.WeatherEffectRenderer
 *  net.minecraft.client.renderer.state.level.WeatherRenderState
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.Particles;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={WeatherEffectRenderer.class})
public class WeatherRendererMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void onRenderWeather(Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        Particles module = mm.getModule(Particles.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (!module.shouldShowWeather()) {
            ci.cancel();
        }
    }
}

