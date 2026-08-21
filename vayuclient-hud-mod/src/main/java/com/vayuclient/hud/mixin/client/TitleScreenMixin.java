/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.realmsclient.RealmsMainScreen
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.toasts.SystemToast
 *  net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId
 *  net.minecraft.client.gui.components.toasts.ToastManager
 *  net.minecraft.client.gui.screens.ConfirmLinkScreen
 *  net.minecraft.client.gui.screens.ConnectScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.TitleScreen
 *  net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
 *  net.minecraft.client.gui.screens.options.OptionsScreen
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 *  net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.multiplayer.ServerData
 *  net.minecraft.client.multiplayer.ServerData$Type
 *  net.minecraft.client.multiplayer.resolver.ServerAddress
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.vayuclient.hud.mixin.client;

import com.mojang.realmsclient.RealmsMainScreen;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.screens.ClickGUIScreen;
import com.vayuclient.hud.launcher.LauncherRenderer;
import com.vayuclient.hud.launcher.LauncherSkinPreference;
import com.vayuclient.hud.launcher.OptionalMenuIntegrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TitleScreen.class})
public class TitleScreenMixin {
    private static final int BACKGROUND_WIDTH = 3840;
    private static final int BACKGROUND_HEIGHT = 2160;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath((String)"vayuclient-hud", (String)"textures/gui/new-background.png");

    @Inject(method={"init"}, at={@At(value="HEAD")}, cancellable=true)
    private void onInit(CallbackInfo ci) {
        LauncherRenderer.onScreenOpen();
        if (LauncherSkinPreference.isVayuClientSkinEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method={"extractBackground"}, at={@At(value="HEAD")}, cancellable=true)
    private void onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (LauncherSkinPreference.isVayuClientSkinEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method={"extractRenderState"}, at={@At(value="HEAD")}, cancellable=true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LauncherSkinPreference.isVayuClientSkinEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = DisplaySpace.width();
        int height = DisplaySpace.height();
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        float backgroundAspect = 1.7777778f;
        int backgroundWidth = width;
        int backgroundHeight = Math.round((float)backgroundWidth / backgroundAspect);
        if (backgroundHeight < height) {
            backgroundHeight = height;
            backgroundWidth = Math.round((float)backgroundHeight * backgroundAspect);
        }
        int backgroundX = (width - backgroundWidth) / 2;
        int backgroundY = (height - backgroundHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(BACKGROUND), backgroundX, backgroundY, 0.0f, 0.0f, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight, -1);
        LauncherRenderer.render(graphics, mc.font, width, height, pxMouseX, pxMouseY);
        DisplaySpace.pop(graphics);
        ci.cancel();
    }

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void onExtractVanillaRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (LauncherSkinPreference.isVayuClientSkinEnabled()) {
            return;
        }
        DisplaySpace.push(graphics);
        LauncherRenderer.renderVanillaOverlay(graphics, Minecraft.getInstance().font, DisplaySpace.width(), DisplaySpace.height(), DisplaySpace.mouseX(mouseX), DisplaySpace.mouseY(mouseY));
        DisplaySpace.pop(graphics);
    }

    @Inject(method={"mouseClicked"}, at={@At(value="HEAD")}, cancellable=true)
    private void onMouseClicked(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }
        int mouseX = DisplaySpace.mouseX(event.x());
        int mouseY = DisplaySpace.mouseY(event.y());
        Minecraft mc = Minecraft.getInstance();
        if (LauncherRenderer.isSkinToggleClicked(DisplaySpace.width(), DisplaySpace.height(), mouseX, mouseY)) {
            LauncherSkinPreference.toggle();
            mc.gui.setScreen((Screen)new TitleScreen());
            cir.setReturnValue(true);
            return;
        }
        if (!LauncherSkinPreference.isVayuClientSkinEnabled()) {
            if (LauncherRenderer.isDiscordClicked(DisplaySpace.width(), DisplaySpace.height(), mouseX, mouseY)) {
                ConfirmLinkScreen.confirmLinkNow((Screen)((Screen)(Object)this), (String)"https://discord.gg/aXUkFajMc", (boolean)true);
                cir.setReturnValue(true);
            }
            return;
        }
        String clicked = LauncherRenderer.getClickedButton(mouseX, mouseY);
        if (clicked == null) {
            cir.setReturnValue(true);
            return;
        }
        Screen self = (Screen)(Object)this;
        switch (clicked) {
            case "play_hero":
            case "singleplayer": {
                mc.gui.setScreen((Screen)new SelectWorldScreen(self));
                break;
            }
            case "multiplayer": {
                mc.gui.setScreen((Screen)new JoinMultiplayerScreen(self));
                break;
            }
            case "vayu_hud_menu": {
                mc.gui.setScreen((Screen)new ClickGUIScreen());
                break;
            }
            case "friends":
            case "social": {
                mc.gui.setScreen((Screen)new com.vayuclient.hud.gui.screens.FriendsScreen(self));
                break;
            }
            case "waypoints": {
                mc.gui.setScreen((Screen)new com.vayuclient.hud.gui.screens.WaypointsScreen(self));
                break;
            }
            case "modmenu": {
                OptionalMenuIntegrations.openModMenu(self);
                break;
            }
            case "flashback_replays": {
                OptionalMenuIntegrations.openFlashbackReplays(self);
                break;
            }
            case "store": {
                SystemToast.add((ToastManager)mc.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"VayuClient Store"), (Component)Component.literal((String)"Coming Soon"));
                break;
            }
            case "quit": {
                mc.stop();
                break;
            }
            case "bananasmp": {
                TitleScreenMixin.connectToServer(self, mc, "Banana SMP", "play.bananasmp.net");
                break;
            }
            case "settings": {
                mc.gui.setScreen((Screen)new OptionsScreen(self, mc.options, false));
                break;
            }
            case "box": {
                TitleScreenMixin.openResourcePacks(mc, self);
                break;
            }
            case "diamond": {
                try {
                    Class<?> rCls = Class.forName("com.mojang.realmsclient.RealmsMainScreen");
                    Object scr = rCls.getConstructor(Screen.class).newInstance(self);
                    mc.gui.setScreen((Screen)scr);
                } catch (Throwable ignored) {}
                break;
            }
            case "window": {
                mc.gui.setScreen((Screen)new ClickGUIScreen());
                break;
            }
            case "joindiscord1": {
                ConfirmLinkScreen.confirmLinkNow((Screen)self, (String)"https://discord.gg/aXUkFajMc", (boolean)true);
                break;
            }
            default: {
                SystemToast.add((ToastManager)mc.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"VayuClient"), (Component)Component.literal((String)"Coming Soon"));
            }
        }
        cir.setReturnValue(true);
    }

    private static void openResourcePacks(Minecraft mc, Screen parent) {
        mc.gui.setScreen((Screen)new PackSelectionScreen(mc.getResourcePackRepository(), repository -> {
            mc.options.updateResourcePacks(repository);
            mc.gui.setScreen(parent);
        }, mc.getResourcePackDirectory(), (Component)Component.translatable((String)"resourcePack.title")));
    }

    private static void connectToServer(Screen parent, Minecraft mc, String name, String ip) {
        ServerAddress address = ServerAddress.parseString((String)ip);
        ServerData data = new ServerData(name, ip, ServerData.Type.OTHER);
        ConnectScreen.startConnecting((Screen)parent, (Minecraft)mc, (ServerAddress)address, (ServerData)data, (boolean)false, null);
    }
}

