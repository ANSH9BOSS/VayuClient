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
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.HitboxModule;
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
        Gizmos.line((Vec3)new Vec3(box.minX, box.minY, box.minZ), (Vec3)new Vec3(box.maxX, box.minY, box.minZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.minY, box.minZ), (Vec3)new Vec3(box.minX, box.minY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.maxX, box.minY, box.minZ), (Vec3)new Vec3(box.maxX, box.minY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.minY, box.maxZ), (Vec3)new Vec3(box.maxX, box.minY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.maxY, box.minZ), (Vec3)new Vec3(box.maxX, box.maxY, box.minZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.maxY, box.minZ), (Vec3)new Vec3(box.minX, box.maxY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.maxX, box.maxY, box.minZ), (Vec3)new Vec3(box.maxX, box.maxY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.maxY, box.maxZ), (Vec3)new Vec3(box.maxX, box.maxY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.minY, box.minZ), (Vec3)new Vec3(box.minX, box.maxY, box.minZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.maxX, box.minY, box.minZ), (Vec3)new Vec3(box.maxX, box.maxY, box.minZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.minX, box.minY, box.maxZ), (Vec3)new Vec3(box.minX, box.maxY, box.maxZ), (int)boxColor, (float)lineWidth);
        Gizmos.line((Vec3)new Vec3(box.maxX, box.minY, box.maxZ), (Vec3)new Vec3(box.maxX, box.maxY, box.maxZ), (int)boxColor, (float)lineWidth);
        if (module.shouldShowEyeHeight()) {
            double eyeY = entity.getY() + (double)entity.getEyeHeight();
            double halfWidth = (double)entity.getBbWidth() / 2.0;
            Vec3 pos = entity.position();
            Gizmos.line((Vec3)new Vec3(pos.x - halfWidth, eyeY, pos.z - halfWidth), (Vec3)new Vec3(pos.x + halfWidth, eyeY, pos.z + halfWidth), (int)eyeColor, (float)lineWidth);
            Gizmos.line((Vec3)new Vec3(pos.x - halfWidth, eyeY, pos.z + halfWidth), (Vec3)new Vec3(pos.x + halfWidth, eyeY, pos.z - halfWidth), (int)eyeColor, (float)lineWidth);
        }
        if (module.shouldShowLookVector()) {
            Vec3 eyePos = entity.getEyePosition(tickDelta);
            Vec3 look = entity.getViewVector(tickDelta);
            Vec3 lookEnd = eyePos.add(look.scale(2.0));
            Gizmos.line((Vec3)eyePos, (Vec3)lookEnd, (int)lookColor, (float)lineWidth);
        }
    }
}

