/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.buffers.GpuBufferSlice
 *  com.mojang.blaze3d.resource.GraphicsResourceAllocator
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.client.renderer.state.level.LevelRenderState
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Vector4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vayuclient.hud.render.MotionBlurShaderManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LevelRenderer.class})
public class LevelRendererMixin {
    @Unique
    private final Matrix4f prevModelView = new Matrix4f();
    @Unique
    private final Matrix4f prevProjection = new Matrix4f();
    @Unique
    private double prevCamX;
    @Unique
    private double prevCamY;
    @Unique
    private double prevCamZ;
    @Unique
    private boolean vayuclient$appliedThisFrame = false;

    @Unique
    private final Matrix4f curModelView = new Matrix4f();
    @Unique
    private final Matrix4f curProjection = new Matrix4f();

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void vayuclient$onRenderHead(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        this.vayuclient$appliedThisFrame = false;
        if (!MotionBlurShaderManager.isMotionBlurActive()) {
            return;
        }
        MotionBlurShaderManager.captureAllocator(resourceAllocator);
        double cx = cameraState.pos.x();
        double cy = cameraState.pos.y();
        double cz = cameraState.pos.z();
        float dx = (float)(cx - this.prevCamX);
        float dy = (float)(cy - this.prevCamY);
        float dz = (float)(cz - this.prevCamZ);
        this.curModelView.set(modelViewMatrix);
        this.curProjection.set((Matrix4fc)cameraState.projectionMatrix);
        MotionBlurShaderManager.setFrameMotionBlur(this.curModelView, this.prevModelView, this.curProjection, this.prevProjection, dx, dy, dz);
        this.prevModelView.set((Matrix4fc)this.curModelView);
        this.prevProjection.set((Matrix4fc)this.curProjection);
        this.prevCamX = cx;
        this.prevCamY = cy;
        this.prevCamZ = cz;
    }

    @Inject(method={"submitEntities"}, at={@At(value="HEAD")})
    private void vayuclient$beforeSubmitEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
        if (!this.vayuclient$appliedThisFrame && MotionBlurShaderManager.shouldExcludeEntities()) {
            this.vayuclient$appliedThisFrame = true;
            MotionBlurShaderManager.applyMotionBlur();
        }
    }

    @Inject(method={"render"}, at={@At(value="TAIL")})
    private void vayuclient$onRenderLevelTail(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        if (!this.vayuclient$appliedThisFrame && MotionBlurShaderManager.isMotionBlurActive()) {
            this.vayuclient$appliedThisFrame = true;
            MotionBlurShaderManager.applyMotionBlur();
        }
    }
}

