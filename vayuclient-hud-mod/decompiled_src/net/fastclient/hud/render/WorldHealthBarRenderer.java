/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.Font$DisplayMode
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.client.renderer.rendertype.RenderTypes
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.client.renderer.state.level.LevelRenderState
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionfc
 */
package net.fastclient.hud.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fastclient.hud.accessor.EntityRenderStateAccessor;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public class WorldHealthBarRenderer {
    private static final float BAR_WIDTH = 0.8f;
    private static final float BAR_HEIGHT = 0.06f;
    private static final float BORDER_SIZE = 0.015f;
    private static final float HEIGHT_OFFSET = 0.3f;
    private static final float TEXT_SCALE = 0.006f;

    public static void onCollectSubmits(LevelRenderContext context) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        DamageIndicator module = mm.getModule(DamageIndicator.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (!module.isWorldMode()) {
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
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        for (EntityRenderState entityState : worldState.entityRenderStates) {
            LivingEntity living;
            EntityRenderStateAccessor accessor;
            Entity entity;
            if (!(entityState instanceof EntityRenderStateAccessor) || !((entity = (accessor = (EntityRenderStateAccessor)entityState).fastclient$getEntity()) instanceof LivingEntity) || !module.shouldRenderWorldHealthBar(living = (LivingEntity)entity) || living.distanceToSqr(cameraPos) > module.getWorldRange() * module.getWorldRange()) continue;
            float health = living.getHealth();
            float maxHealth = living.getMaxHealth();
            if (maxHealth <= 0.0f) continue;
            double barY = entityState.y + (double)entityState.boundingBoxHeight + (double)0.3f;
            WorldHealthBarRenderer.renderHealthBar(poseStack, output, cameraState, cameraPos, font, entityState.x, barY, entityState.z, living, health, maxHealth, module);
        }
    }

    private static void renderHealthBar(PoseStack poseStack, SubmitNodeCollector output, CameraRenderState cameraState, Vec3 cameraPos, Font font, double worldX, double worldY, double worldZ, LivingEntity entity, float health, float maxHealth, DamageIndicator module) {
        poseStack.pushPose();
        poseStack.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        poseStack.mulPose((Quaternionfc)cameraState.orientation);
        float barWidth = module.getWorldBarWidth();
        float barHeight = module.getWorldBarHeight();
        float halfWidth = barWidth / 2.0f;
        float healthPercent = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        int healthColor = module.getHealthColor(healthPercent);
        int r = healthColor >> 16 & 0xFF;
        int g = healthColor >> 8 & 0xFF;
        int b = healthColor & 0xFF;
        float filledWidth = barWidth * healthPercent;
        float filledRight = -halfWidth + filledWidth;
        output.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            Matrix4f matrix = pose.pose();
            WorldHealthBarRenderer.addQuad(buffer, matrix, -halfWidth - 0.015f, -0.015f, halfWidth + 0.015f, barHeight + 0.015f, 0, 0, 0, 255, 0.0f);
            WorldHealthBarRenderer.addQuad(buffer, matrix, -halfWidth, 0.0f, halfWidth, barHeight, 40, 40, 40, 255, 0.001f);
            WorldHealthBarRenderer.addQuad(buffer, matrix, -halfWidth, 0.0f, filledRight, barHeight, r, g, b, 255, 0.002f);
        });
        if (module.shouldShowWorldName()) {
            poseStack.pushPose();
            poseStack.translate(0.0f, barHeight + 0.015f + 0.05f, 0.0f);
            poseStack.scale(0.006f, -0.006f, 0.006f);
            Component name = entity.getDisplayName();
            int nameWidth = font.width((FormattedText)name);
            output.submitText(poseStack, (float)(-nameWidth) / 2.0f, 0.0f, name.getVisualOrderText(), false, Font.DisplayMode.NORMAL, 0xF000F0, -1, 0, 0);
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.translate(0.0f, -0.035f, 0.0f);
        poseStack.scale(0.006f, -0.006f, 0.006f);
        String healthText = WorldHealthBarRenderer.formatHealth(health, maxHealth);
        int textWidth = font.width(healthText);
        int textColor = 0xFF000000 | healthColor;
        output.submitText(poseStack, (float)(-textWidth) / 2.0f, 0.0f, Component.literal((String)healthText).getVisualOrderText(), false, Font.DisplayMode.NORMAL, 0xF000F0, textColor, 0, 0);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void addQuad(VertexConsumer buffer, Matrix4f matrix, float minX, float minY, float maxX, float maxY, int red, int green, int blue, int alpha, float z) {
        buffer.addVertex((Matrix4fc)matrix, minX, minY, z).setColor(red, green, blue, alpha);
        buffer.addVertex((Matrix4fc)matrix, maxX, minY, z).setColor(red, green, blue, alpha);
        buffer.addVertex((Matrix4fc)matrix, maxX, maxY, z).setColor(red, green, blue, alpha);
        buffer.addVertex((Matrix4fc)matrix, minX, maxY, z).setColor(red, green, blue, alpha);
    }

    private static String formatHealth(float health, float maxHealth) {
        if ((double)health == Math.floor(health) && (double)maxHealth == Math.floor(maxHealth)) {
            return String.format("%.0f / %.0f", Float.valueOf(health), Float.valueOf(maxHealth));
        }
        return String.format("%.1f / %.0f", Float.valueOf(health), Float.valueOf(maxHealth));
    }
}

