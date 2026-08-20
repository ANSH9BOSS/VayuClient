/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  org.lwjgl.glfw.GLFW
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.fastclient.hud.core;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.gui.screens.HudOverlayScreen;
import net.fastclient.hud.gui.screens.WaypointsScreen;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.impl.render.WaypointsModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeybindManager {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"FastClientHUD");
    private static KeybindManager instance;
    private KeyMapping zoomKey;
    private KeyMapping fpsToggleKey;
    private KeyMapping fullbrightToggleKey;
    private KeyMapping clickGuiKey;
    private boolean fpsKeyWasDown = false;
    private boolean fullbrightKeyWasDown = false;
    private boolean clickGuiKeyWasDown = false;
    private boolean zoomKeyWasDown = false;
    private final Map<String, Boolean> customKeyStates = new HashMap<String, Boolean>();

    public KeybindManager() {
        instance = this;
    }

    public static KeybindManager getInstance() {
        return instance;
    }

    public void init() {
        this.zoomKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.fast-client-hud.zoom", InputConstants.Type.KEYSYM, -1, KeyMapping.Category.MISC));
        this.fpsToggleKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.fast-client-hud.fps_toggle", InputConstants.Type.KEYSYM, 295, KeyMapping.Category.MISC));
        this.fullbrightToggleKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.fast-client-hud.fullbright_toggle", InputConstants.Type.KEYSYM, 296, KeyMapping.Category.MISC));
        this.clickGuiKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.fast-client-hud.click_gui", InputConstants.Type.KEYSYM, 344, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.handleKeybinds());
        LOGGER.info("[KeybindManager] Initialized");
    }

    private void handleKeybinds() {
        boolean clickGuiKeyDown;
        Module fullbrightModule;
        Module fpsModule;
        Minecraft mc = Minecraft.getInstance();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        long windowHandle = mc.getWindow().handle();
        Module zoomModule = mm.getModule("zoom");
        if (zoomModule != null) {
            boolean zoomKeyDown;
            boolean bl = zoomKeyDown = this.zoomKey.isDown() || this.isCustomKeyDownWithModifiers(zoomModule, windowHandle);
            if (zoomModule.isKeyHeld()) {
                if (zoomKeyDown && !zoomModule.isEnabled()) {
                    zoomModule.setEnabled(true);
                } else if (!zoomKeyDown && zoomModule.isEnabled()) {
                    zoomModule.setEnabled(false);
                }
            } else if (zoomKeyDown && !this.zoomKeyWasDown) {
                mm.toggleModule(zoomModule);
            }
            this.zoomKeyWasDown = zoomKeyDown;
        }
        if ((fpsModule = mm.getModule("fps")) != null) {
            boolean fpsKeyDown;
            boolean bl = fpsKeyDown = this.fpsToggleKey.isDown() || this.isCustomKeyDownWithModifiers(fpsModule, windowHandle);
            if (fpsKeyDown && !this.fpsKeyWasDown) {
                mm.toggleModule(fpsModule);
            }
            this.fpsKeyWasDown = fpsKeyDown;
        }
        if ((fullbrightModule = mm.getModule("fullbright")) != null) {
            boolean fullbrightKeyDown;
            boolean bl = fullbrightKeyDown = this.fullbrightToggleKey.isDown() || this.isCustomKeyDownWithModifiers(fullbrightModule, windowHandle);
            if (fullbrightKeyDown && !this.fullbrightKeyWasDown) {
                mm.toggleModule(fullbrightModule);
            }
            this.fullbrightKeyWasDown = fullbrightKeyDown;
        }
        if ((clickGuiKeyDown = this.clickGuiKey.isDown()) && !this.clickGuiKeyWasDown && mc.gui.screen() == null) {
            mc.gui.setScreen((Screen)new HudOverlayScreen());
        }
        this.clickGuiKeyWasDown = clickGuiKeyDown;
        this.handleCustomModuleKeybinds(mm, mc, windowHandle);
    }

    private boolean isCustomKeyDownWithModifiers(Module module, long windowHandle) {
        int keyCode = module.getKeyBinding();
        if (keyCode == 0) {
            return false;
        }
        if (GLFW.glfwGetKey((long)windowHandle, (int)keyCode) != 1) {
            return false;
        }
        int requiredMods = module.getKeyModifiers();
        return this.areModifiersMatching(windowHandle, requiredMods);
    }

    private boolean areModifiersMatching(long windowHandle, int requiredMods) {
        boolean shiftRequired = (requiredMods & 1) != 0;
        boolean ctrlRequired = (requiredMods & 2) != 0;
        boolean altRequired = (requiredMods & 4) != 0;
        boolean shiftDown = GLFW.glfwGetKey((long)windowHandle, (int)340) == 1 || GLFW.glfwGetKey((long)windowHandle, (int)344) == 1;
        boolean ctrlDown = GLFW.glfwGetKey((long)windowHandle, (int)341) == 1 || GLFW.glfwGetKey((long)windowHandle, (int)345) == 1;
        boolean altDown = GLFW.glfwGetKey((long)windowHandle, (int)342) == 1 || GLFW.glfwGetKey((long)windowHandle, (int)346) == 1;
        return shiftDown == shiftRequired && ctrlDown == ctrlRequired && altDown == altRequired;
    }

    private void handleCustomModuleKeybinds(ModuleManager mm, Minecraft mc, long windowHandle) {
        boolean screenOpen = mc.gui.screen() != null;
        for (Module module : mm.getModules()) {
            String moduleName;
            int keyCode = module.getKeyBinding();
            if (keyCode == 0 || (moduleName = module.getName().toLowerCase(Locale.ROOT)).equals("zoom") || moduleName.equals("fps") || moduleName.equals("fullbright")) continue;
            boolean keyDown = GLFW.glfwGetKey((long)windowHandle, (int)keyCode) == 1;
            boolean modifiersMatch = this.areModifiersMatching(windowHandle, module.getKeyModifiers());
            boolean fullMatch = keyDown && modifiersMatch;
            boolean wasDown = this.customKeyStates.getOrDefault(moduleName, false);
            if (module instanceof WaypointsModule) {
                WaypointsModule waypoints = (WaypointsModule)module;
                if (!screenOpen && waypoints.isEnabled() && fullMatch && !wasDown) {
                    mc.gui.setScreen((Screen)new WaypointsScreen(waypoints));
                }
                this.customKeyStates.put(moduleName, fullMatch);
                continue;
            }
            if (module.isKeyHeld()) {
                if (fullMatch && !module.isEnabled()) {
                    module.setEnabled(true);
                } else if (!fullMatch && module.isEnabled()) {
                    module.setEnabled(false);
                }
            } else if (!screenOpen && fullMatch && !wasDown) {
                mm.toggleModule(module);
            }
            this.customKeyStates.put(moduleName, fullMatch);
        }
    }

    public KeyMapping getZoomKey() {
        return this.zoomKey;
    }
}

