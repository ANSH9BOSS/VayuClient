/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer
 *  net.minecraft.gizmos.Gizmos
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.HitboxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityHitboxDebugRenderer.class})
public class HitboxMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method={"showHitboxes"}, at={@At(value="HEAD")}, cancellable=true)
    private void onShowHitboxes(Entity entity, float tickDelta, boolean showVehicle, CallbackInfo ci) {
        HitboxModule hitboxModule;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (hitboxModule = mm.getModule(HitboxModule.class)) != null && hitboxModule.isEnabled()) {
            this.renderCustomHitboxGizmo(entity, tickDelta, hitboxModule);
            if (showVehicle && entity.getVehicle() != null) {
                this.renderCustomHitboxGizmo(entity.getVehicle(), tickDelta, hitboxModule);
            }
            ci.cancel();
        }
    }

    private void renderCustomHitboxGizmo(Entity entity, float tickDelta, HitboxModule module) {
        AABB box = entity.getBoundingBox();
        int boxColor = module.getBoxColorARGB();
        int eyeColor = module.getEyeHeightColorARGB();
        int lookColor = module.getLookDirColorARGB();
        float lineWidth = 1.0f;
        
        Vec3 v000 = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 v100 = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 v001 = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 v101 = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 v010 = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 v110 = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 v011 = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 v111 = new Vec3(box.maxX, box.maxY, box.maxZ);

        // Bottom 4 edges
        Gizmos.line(v000, v100, boxColor, lineWidth);
        Gizmos.line(v000, v001, boxColor, lineWidth);
        Gizmos.line(v100, v101, boxColor, lineWidth);
        Gizmos.line(v001, v101, boxColor, lineWidth);

        // Top 4 edges
        Gizmos.line(v010, v110, boxColor, lineWidth);
        Gizmos.line(v010, v011, boxColor, lineWidth);
        Gizmos.line(v110, v111, boxColor, lineWidth);
        Gizmos.line(v011, v111, boxColor, lineWidth);

        // Vertical 4 pillars
        Gizmos.line(v000, v010, boxColor, lineWidth);
        Gizmos.line(v100, v110, boxColor, lineWidth);
        Gizmos.line(v001, v011, boxColor, lineWidth);
        Gizmos.line(v101, v111, boxColor, lineWidth);

        if (module.shouldShowEyeHeight()) {
            double eyeY = entity.getY() + (double)entity.getEyeHeight();
            double halfWidth = (double)entity.getBbWidth() / 2.0;
            Vec3 pos = entity.position();
            Vec3 e1 = new Vec3(pos.x - halfWidth, eyeY, pos.z - halfWidth);
            Vec3 e2 = new Vec3(pos.x + halfWidth, eyeY, pos.z + halfWidth);
            Vec3 e3 = new Vec3(pos.x - halfWidth, eyeY, pos.z + halfWidth);
            Vec3 e4 = new Vec3(pos.x + halfWidth, eyeY, pos.z - halfWidth);
            Gizmos.line(e1, e2, eyeColor, lineWidth);
            Gizmos.line(e3, e4, eyeColor, lineWidth);
        }
        if (module.shouldShowLookVector()) {
            Vec3 eyePos = entity.getEyePosition(tickDelta);
            Vec3 look = entity.getViewVector(tickDelta);
            Vec3 lookEnd = eyePos.add(look.scale(2.0));
            Gizmos.line(eyePos, lookEnd, lookColor, lineWidth);
        }
    }
}

