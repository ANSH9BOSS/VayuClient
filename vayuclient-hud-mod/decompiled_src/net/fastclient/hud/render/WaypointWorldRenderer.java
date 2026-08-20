/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.client.renderer.state.level.LevelRenderState
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.phys.Vec3
 */
package net.fastclient.hud.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.WaypointsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

public final class WaypointWorldRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;

    private WaypointWorldRenderer() {
    }

    public static void onCollectSubmits(LevelRenderContext context) {
        ModuleManager manager = ModuleManager.getInstance();
        if (manager == null) {
            return;
        }
        WaypointsModule module = manager.getModule(WaypointsModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector output = context.submitNodeCollector();
        LevelRenderState worldState = context.levelState();
        if (poseStack == null || output == null || worldState == null) {
            return;
        }
        CameraRenderState camera = worldState.cameraRenderState;
        List<WaypointsModule.Waypoint> waypoints = module.getVisibleWorldWaypoints();
        for (WaypointsModule.Waypoint waypoint : waypoints) {
            WaypointWorldRenderer.renderMarker(poseStack, output, camera, camera.pos, module, waypoint);
        }
    }

    private static void renderMarker(PoseStack poseStack, SubmitNodeCollector output, CameraRenderState camera, Vec3 cameraPos, WaypointsModule module, WaypointsModule.Waypoint waypoint) {
        poseStack.pushPose();
        poseStack.translate(waypoint.x - cameraPos.x, waypoint.y - cameraPos.y, waypoint.z - cameraPos.z);
        float markerScale = (float)Math.max(1.4, Math.min(64.0, module.distanceToPlayer(waypoint) / 16.0));
        poseStack.scale(markerScale, markerScale, markerScale);
        MutableComponent label = Component.literal((String)"\u25c6").withColor(waypoint.color & 0xFFFFFF).append((Component)Component.literal((String)("  " + module.getWorldLabel(waypoint))).withColor(0xFFFFFF));
        output.submitNameTag(poseStack, Vec3.ZERO, 0, (Component)label, true, 0xF000F0, camera);
        poseStack.popPose();
    }
}

