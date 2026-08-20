/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.PoseStack$Pose
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.rendertype.RenderTypes
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.client.renderer.state.level.LevelRenderState
 *  net.minecraft.core.BlockPos
 *  net.minecraft.util.Mth
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package com.vayuclient.hud.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.BlockOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class BlockOverlayRenderer {
    public static void onCollectSubmits(LevelRenderContext context) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        BlockOverlayModule module = mm.getModule(BlockOverlayModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector output = context.submitNodeCollector();
        LevelRenderState worldState = context.levelState();
        if (poseStack == null || output == null || worldState == null) {
            return;
        }
        CameraRenderState cameraState = worldState.cameraRenderState;
        Vec3 cameraPos = cameraState.pos;
        BlockOverlayRenderer.renderBlockIndicator(poseStack, output, cameraPos, module);
    }

    private static void renderBlockIndicator(PoseStack poseStack, SubmitNodeCollector output, Vec3 cameraPos, BlockOverlayModule module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult blockHit = (BlockHitResult)mc.hitResult;
        BlockPos blockPos = blockHit.getBlockPos();
        float expand = (float)module.getExpandAmount();
        float minX = (float)((double)blockPos.getX() - cameraPos.x) - expand;
        float minY = (float)((double)blockPos.getY() - cameraPos.y) - expand;
        float minZ = (float)((double)blockPos.getZ() - cameraPos.z) - expand;
        float maxX = minX + 1.0f + expand * 2.0f;
        float maxY = minY + 1.0f + expand * 2.0f;
        float maxZ = minZ + 1.0f + expand * 2.0f;
        if (module.shouldShowFill()) {
            int fillColor = module.getOverlayColorARGB();
            output.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> BlockOverlayRenderer.renderFilledBox(buffer, pose, minX, minY, minZ, maxX, maxY, maxZ, fillColor));
        }
        if (module.shouldShowOutline()) {
            int outlineColor = module.getOutlineColorARGB();
            float lineWidth = module.getOutlineWidth();
            output.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> BlockOverlayRenderer.renderBoxOutline(buffer, pose, minX, minY, minZ, maxX, maxY, maxZ, outlineColor, lineWidth));
        }
    }

    private static void renderFilledBox(VertexConsumer buffer, PoseStack.Pose pose, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color) {
        Matrix4f matrix = pose.pose();
        buffer.addVertex((Matrix4fc)matrix, minX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, minY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, minY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, minY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, minX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, minZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, maxZ).setColor(color);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, maxZ).setColor(color);
    }

    private static void renderBoxOutline(VertexConsumer buffer, PoseStack.Pose pose, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color, float lineWidth) {
        BlockOverlayRenderer.renderLine(buffer, pose, minX, minY, minZ, maxX, minY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, minY, minZ, maxX, minY, maxZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, minY, maxZ, minX, minY, maxZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, minX, minY, maxZ, minX, minY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, minX, maxY, minZ, maxX, maxY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, minX, maxY, maxZ, minX, maxY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, minX, minY, minZ, minX, maxY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, color, lineWidth);
        BlockOverlayRenderer.renderLine(buffer, pose, minX, minY, maxZ, minX, maxY, maxZ, color, lineWidth);
    }

    private static void renderLine(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = Mth.sqrt((float)(dx * dx + dy * dy + dz * dz));
        if (len < 1.0E-4f) {
            return;
        }
        buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx /= len, dy /= len, dz /= len).setLineWidth(lineWidth);
        buffer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
    }
}

