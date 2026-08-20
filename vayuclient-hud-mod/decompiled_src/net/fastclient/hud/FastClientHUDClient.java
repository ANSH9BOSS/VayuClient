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
package net.fastclient.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fastclient.hud.core.KeybindManager;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.network.FastClientUserCache;
import net.fastclient.hud.render.BlockOverlayRenderer;
import net.fastclient.hud.render.RenderManager;
import net.fastclient.hud.render.WaypointWorldRenderer;
import net.fastclient.hud.render.WorldHealthBarRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastClientHUDClient
implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"FastClientHUD");
    public static final String MOD_ID = "fastclient-hud";
    public static final String MC_VERSION = "26.2";
    public static final String RELEASE_ID = "ca786cd3";
    public static final String VERSION_LABEL = "Fastclient 26.2 (release/ca786cd3)";
    private static FastClientHUDClient instance;
    private ModuleManager moduleManager;
    private KeybindManager keybindManager;
    private RenderManager renderManager;

    public static String getModVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("1.0.0");
    }

    public void onInitializeClient() {
        instance = this;
        LOGGER.info("FastClientHUD initializing...");
        this.moduleManager = new ModuleManager();
        this.keybindManager = new KeybindManager();
        this.renderManager = new RenderManager();
        this.keybindManager.init();
        this.renderManager.init();
        this.moduleManager.registerModules();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                this.moduleManager.onTick();
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(WorldHealthBarRenderer::onCollectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(BlockOverlayRenderer::onCollectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(WaypointWorldRenderer::onCollectSubmits);
        FastClientUserCache.getInstance();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null) {
                return;
            }
            String localUsername = client.player.getGameProfile().name();
            FastClientUserCache cache = FastClientUserCache.getInstance();
            cache.pingServer(localUsername);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((_handler, _client) -> FastClientUserCache.getInstance().onDisconnect());
        LOGGER.info("FastClientHUD initialized!");
    }

    public static FastClientHUDClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public RenderManager getRenderManager() {
        return this.renderManager;
    }
}

