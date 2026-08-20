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
package net.fastclient.hud.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"FastClientHUD");
    private static RenderManager instance;

    public RenderManager() {
        instance = this;
    }

    public static RenderManager getInstance() {
        return instance;
    }

    public void init() {
        HudElementRegistry.addLast((Identifier)Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"hud_overlay"), this::onHudRender);
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
            for (Module module : mm.getModulesByCategory(Category.HUD)) {
                if (!module.isEnabled()) continue;
                try {
                    module.onRender(graphics, tickDelta);
                }
                catch (Exception e) {
                    LOGGER.error("[{}] Error in render: {}", (Object)module.getName(), (Object)e.getMessage());
                }
            }
            for (Module module : mm.getModulesByCategory(Category.RENDER)) {
                if (!module.isEnabled()) continue;
                try {
                    module.onRender(graphics, tickDelta);
                }
                catch (Exception e) {
                    LOGGER.error("[{}] Error in render: {}", (Object)module.getName(), (Object)e.getMessage());
                }
            }
            for (Module module : mm.getModulesByCategory(Category.UTILITY)) {
                if (!module.isEnabled()) continue;
                try {
                    module.onRender(graphics, tickDelta);
                }
                catch (Exception e) {
                    LOGGER.error("[{}] Error in render: {}", (Object)module.getName(), (Object)e.getMessage());
                }
            }
        }
        finally {
            DisplaySpace.pop(graphics);
        }
    }
}

