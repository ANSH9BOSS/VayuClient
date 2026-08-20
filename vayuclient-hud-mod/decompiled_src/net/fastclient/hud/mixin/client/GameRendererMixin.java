/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.renderer.GameRenderer
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.fastclient.hud.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class})
public class GameRendererMixin {
    @Inject(method={"bobHurt"}, at={@At(value="HEAD")}, cancellable=true)
    private void onBobHurt(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        NoHurtCam noHurtCam;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (noHurtCam = mm.getModule(NoHurtCam.class)) != null && noHurtCam.isEnabled()) {
            ci.cancel();
        }
    }
}

