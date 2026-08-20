/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
 *  net.fabricmc.loader.api.FabricLoader
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.vayuclient.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.vayuclient.hud.core.KeybindManager;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.network.VayuUserCache;
import com.vayuclient.hud.render.BlockOverlayRenderer;
import com.vayuclient.hud.render.RenderManager;
import com.vayuclient.hud.render.WaypointWorldRenderer;
import com.vayuclient.hud.render.WorldHealthBarRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VayuHUDClient
implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"VayuHUD");
    public static final String MOD_ID = "vayuclient-hud";
    public static final String MC_VERSION = "26.2";
    public static final String RELEASE_ID = "ca786cd3";
    public static final String VERSION_LABEL = "VayuClient 26.2 (release/ca786cd3)";
    private static VayuHUDClient instance;
    private ModuleManager moduleManager;
    private KeybindManager keybindManager;
    private RenderManager renderManager;
    private boolean joinedServer = false;

    public static String getModVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("1.0.0");
    }

    public void onInitializeClient() {
        instance = this;
        LOGGER.info("VayuHUD initializing...");
        this.moduleManager = new ModuleManager();
        this.keybindManager = new KeybindManager();
        this.renderManager = new RenderManager();
        this.keybindManager.init();
        this.renderManager.init();
        this.moduleManager.registerModules();

        LevelRenderEvents.COLLECT_SUBMITS.register(WorldHealthBarRenderer::onCollectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(BlockOverlayRenderer::onCollectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(WaypointWorldRenderer::onCollectSubmits);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.moduleManager != null && client.player != null) {
                this.moduleManager.onTick();
            }
        });
        VayuUserCache.getInstance();
        try {
            com.vayuclient.hud.discord.DiscordPresenceService.getInstance().start();
        } catch (Throwable ignored) {}
        LOGGER.info("VayuHUD initialized!");
    }

    public static VayuHUDClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public RenderManager getRenderManager() {
        return this.renderManager;
    }
}

