/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.resources.Identifier
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.vayuclient.hud.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"VayuHUD");
    private static RenderManager instance;

    public RenderManager() {
        instance = this;
    }

    public static RenderManager getInstance() {
        return instance;
    }

    public void init() {
        HudElementRegistry.addLast((Identifier)Identifier.fromNamespaceAndPath((String)"vayuclient-hud", (String)"hud_overlay"), this::onHudRender);
        LOGGER.info("[RenderManager] Initialized");
    }

    private void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        this.renderAllModules(graphics, deltaTracker);
    }

    public void onRenderHudDirect(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        this.renderAllModules(graphics, deltaTracker);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderAllModules(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        DisplaySpace.push(graphics);
        try {
            Module[] activeModules = mm.getActiveRenderModules();
            for (int i = 0; i < activeModules.length; i++) {
                Module module = activeModules[i];
                if (module == null || !module.isEnabled()) continue;
                try {
                    module.onRender(graphics, tickDelta);
                } catch (Throwable t) {
                    LOGGER.error("[{}] Error in render: {}", (Object)module.getName(), (Object)t.getMessage());
                }
            }
        } finally {
            DisplaySpace.pop(graphics);
        }
    }
}

