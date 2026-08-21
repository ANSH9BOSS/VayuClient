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

public class VayuHUDClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("VayuHUD");
    public static final String MOD_ID = "vayuclient-hud";
    public static final String MC_VERSION = "26.2";
    public static final String RELEASE_ID = "ca786cd3";
    public static final String VERSION_LABEL = "VayuClient (Universal HUD)";
    private static VayuHUDClient instance;
    private ModuleManager moduleManager;
    private KeybindManager keybindManager;
    private RenderManager renderManager;
    private boolean joinedServer = false;

    public static String getModVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("1.9.1");
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("VayuHUD initializing across Minecraft 1.21+...");

        try {
            this.moduleManager = new ModuleManager();
            this.keybindManager = new KeybindManager();
            this.renderManager = new RenderManager();

            try {
                this.keybindManager.init();
            } catch (Throwable t) {
                LOGGER.warn("KeybindManager init non-fatal warning: {}", t.getMessage());
            }

            try {
                this.renderManager.init();
            } catch (Throwable t) {
                LOGGER.warn("RenderManager init non-fatal warning: {}", t.getMessage());
            }

            try {
                this.moduleManager.registerModules();
            } catch (Throwable t) {
                LOGGER.warn("ModuleManager registerModules non-fatal warning: {}", t.getMessage());
            }

            try {
                LevelRenderEvents.COLLECT_SUBMITS.register(WorldHealthBarRenderer::onCollectSubmits);
                LevelRenderEvents.COLLECT_SUBMITS.register(BlockOverlayRenderer::onCollectSubmits);
                LevelRenderEvents.COLLECT_SUBMITS.register(WaypointWorldRenderer::onCollectSubmits);
            } catch (Throwable t) {
                LOGGER.warn("LevelRenderEvents registration warning: {}", t.getMessage());
            }

            try {
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                    if (this.moduleManager != null && client != null && client.player != null) {
                        try {
                            this.moduleManager.onTick();
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                LOGGER.warn("ClientTickEvents registration warning: {}", t.getMessage());
            }

            try {
                VayuUserCache.getInstance();
            } catch (Throwable t) {
                LOGGER.warn("VayuUserCache init warning: {}", t.getMessage());
            }

            try {
                com.vayuclient.hud.discord.DiscordPresenceService.getInstance().start();
            } catch (Throwable ignored) {}

            LOGGER.info("VayuHUD successfully initialized!");
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize VayuHUD client components cleanly", t);
        }
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
