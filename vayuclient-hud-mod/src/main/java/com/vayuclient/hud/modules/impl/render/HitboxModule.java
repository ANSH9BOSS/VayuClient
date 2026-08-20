/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package com.vayuclient.hud.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class HitboxModule
extends Module {
    private final ColorSetting boxColor = this.register(new ColorSetting("box_color", "Hitbox color", 255, 255, 255, 255));
    private final ColorSetting eyeHeightColor = this.register(new ColorSetting("eye_height_color", "Eye height indicator color", 255, 0, 0, 255));
    private final ColorSetting lookDirColor = this.register(new ColorSetting("look_dir_color", "Look direction line color", 0, 0, 255, 255));
    private final BooleanSetting showEyeHeight = this.register(new BooleanSetting("show_eye_height", "Show eye height indicator", true));
    private final BooleanSetting showLookVector = this.register(new BooleanSetting("show_look_vector", "Show look direction line", true));

    public HitboxModule() {
        super("Hitbox", "Customizable entity hitbox rendering (F3+B)", Category.RENDER);
        this.eyeHeightColor.visibleWhen(this.showEyeHeight::getValue);
        this.lookDirColor.visibleWhen(this.showLookVector::getValue);
    }

    @Override
    protected void onEnable() {
        this.sendMessage("Hitbox customization enabled - use F3+B to toggle hitboxes");
    }

    public void renderCustomHitbox(PoseStack poseStack, VertexConsumer vertices, Entity entity, float tickDelta) {
        double eyeHeight;
        AABB box = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
        float r = (float)this.boxColor.getRed() / 255.0f;
        float g = (float)this.boxColor.getGreen() / 255.0f;
        float b = (float)this.boxColor.getBlue() / 255.0f;
        float a = (float)this.boxColor.getAlpha() / 255.0f;
        Matrix4f matrix = poseStack.last().pose();
        this.drawBoxOutline(vertices, matrix, box, r, g, b, a);
        if (((Boolean)this.showEyeHeight.getValue()).booleanValue()) {
            float eyeR = (float)this.eyeHeightColor.getRed() / 255.0f;
            float eyeG = (float)this.eyeHeightColor.getGreen() / 255.0f;
            float eyeB = (float)this.eyeHeightColor.getBlue() / 255.0f;
            float eyeA = (float)this.eyeHeightColor.getAlpha() / 255.0f;
            eyeHeight = entity.getEyeHeight();
            float minX = (float)box.minX;
            float maxX = (float)box.maxX;
            float minZ = (float)box.minZ;
            float maxZ = (float)box.maxZ;
            this.drawLine(vertices, matrix, minX, (float)eyeHeight, minZ, maxX, (float)eyeHeight, minZ, eyeR, eyeG, eyeB, eyeA);
            this.drawLine(vertices, matrix, minX, (float)eyeHeight, maxZ, maxX, (float)eyeHeight, maxZ, eyeR, eyeG, eyeB, eyeA);
            this.drawLine(vertices, matrix, minX, (float)eyeHeight, minZ, minX, (float)eyeHeight, maxZ, eyeR, eyeG, eyeB, eyeA);
            this.drawLine(vertices, matrix, maxX, (float)eyeHeight, minZ, maxX, (float)eyeHeight, maxZ, eyeR, eyeG, eyeB, eyeA);
        }
        if (((Boolean)this.showLookVector.getValue()).booleanValue()) {
            float lookR = (float)this.lookDirColor.getRed() / 255.0f;
            float lookG = (float)this.lookDirColor.getGreen() / 255.0f;
            float lookB = (float)this.lookDirColor.getBlue() / 255.0f;
            float lookA = (float)this.lookDirColor.getAlpha() / 255.0f;
            eyeHeight = entity.getEyeHeight();
            Vec3 look = entity.getViewVector(tickDelta);
            this.drawLine(vertices, matrix, 0.0f, (float)eyeHeight, 0.0f, (float)(look.x * 2.0), (float)(eyeHeight + look.y * 2.0), (float)(look.z * 2.0), lookR, lookG, lookB, lookA);
        }
    }

    private void drawBoxOutline(VertexConsumer vertices, Matrix4f matrix, AABB box, float r, float g, float b, float a) {
        float minX = (float)box.minX;
        float minY = (float)box.minY;
        float minZ = (float)box.minZ;
        float maxX = (float)box.maxX;
        float maxY = (float)box.maxY;
        float maxZ = (float)box.maxZ;
        this.drawLine(vertices, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        this.drawLine(vertices, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        this.drawLine(vertices, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        this.drawLine(vertices, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private void drawLine(VertexConsumer vertices, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0f) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        vertices.addVertex((Matrix4fc)matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(1.0f);
        vertices.addVertex((Matrix4fc)matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(1.0f);
    }

    public int getBoxColorARGB() {
        return this.boxColor.getAlpha() << 24 | this.boxColor.getRed() << 16 | this.boxColor.getGreen() << 8 | this.boxColor.getBlue();
    }

    public int getEyeHeightColorARGB() {
        return this.eyeHeightColor.getAlpha() << 24 | this.eyeHeightColor.getRed() << 16 | this.eyeHeightColor.getGreen() << 8 | this.eyeHeightColor.getBlue();
    }

    public int getLookDirColorARGB() {
        return this.lookDirColor.getAlpha() << 24 | this.lookDirColor.getRed() << 16 | this.lookDirColor.getGreen() << 8 | this.lookDirColor.getBlue();
    }

    public boolean shouldShowEyeHeight() {
        return (Boolean)this.showEyeHeight.getValue();
    }

    public boolean shouldShowLookVector() {
        return (Boolean)this.showLookVector.getValue();
    }

    public float getBoxColorR() {
        return (float)this.boxColor.getRed() / 255.0f;
    }

    public float getBoxColorG() {
        return (float)this.boxColor.getGreen() / 255.0f;
    }

    public float getBoxColorB() {
        return (float)this.boxColor.getBlue() / 255.0f;
    }
}

