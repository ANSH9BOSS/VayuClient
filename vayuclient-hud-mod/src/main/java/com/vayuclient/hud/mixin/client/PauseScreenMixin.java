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
 *  net.minecraft.client.gui.screens.MultiplayerOptionsScreen
 *  net.minecraft.client.gui.screens.PauseScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.achievement.StatsScreen
 *  net.minecraft.client.gui.screens.advancements.AdvancementsScreen
 *  net.minecraft.client.gui.screens.options.OptionsScreen
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 *  net.minecraft.client.gui.screens.social.SocialInteractionsScreen
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Util
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.mojang.realmsclient.RealmsMainScreen;
import java.util.Locale;
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
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PauseScreen.class})
public abstract class PauseScreenMixin
extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="HEAD")}, cancellable=true)
    private void onInit(CallbackInfo ci) {
        if (this.showsMenu() && LauncherSkinPreference.isVayuClientSkinEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method={"extractBackground"}, at={@At(value="HEAD")}, cancellable=true)
    private void onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.showsMenu() && LauncherSkinPreference.isVayuClientSkinEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method={"extractRenderState"}, at={@At(value="HEAD")}, cancellable=true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.showsMenu() || !LauncherSkinPreference.isVayuClientSkinEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        DisplaySpace.push(graphics);
        LauncherRenderer.renderPause(graphics, mc.font, DisplaySpace.width(), DisplaySpace.height(), DisplaySpace.mouseX(mouseX), DisplaySpace.mouseY(mouseY));
        DisplaySpace.pop(graphics);
        ci.cancel();
    }

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void onExtractVanillaRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.showsMenu() || LauncherSkinPreference.isVayuClientSkinEnabled()) {
            return;
        }
        DisplaySpace.push(graphics);
        LauncherRenderer.renderVanillaOverlay(graphics, Minecraft.getInstance().font, DisplaySpace.width(), DisplaySpace.height(), DisplaySpace.mouseX(mouseX), DisplaySpace.mouseY(mouseY));
        DisplaySpace.pop(graphics);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int mouseX = DisplaySpace.mouseX(event.x());
        int mouseY = DisplaySpace.mouseY(event.y());
        Minecraft mc = Minecraft.getInstance();
        if (this.showsMenu() && event.button() == 0 && LauncherRenderer.isSkinToggleClicked(DisplaySpace.width(), DisplaySpace.height(), mouseX, mouseY)) {
            LauncherSkinPreference.toggle();
            mc.gui.setScreen((Screen)new PauseScreen(this.showsMenu()));
            return true;
        }
        if (!this.showsMenu()) {
            return super.mouseClicked(event, bl);
        }
        if (!LauncherSkinPreference.isVayuClientSkinEnabled()) {
            if (event.button() == 0 && LauncherRenderer.isDiscordClicked(DisplaySpace.width(), DisplaySpace.height(), mouseX, mouseY)) {
                ConfirmLinkScreen.confirmLinkNow((Screen)(Object)this, (String)"https://discord.gg/RGzATq3v7J", (boolean)true);
                return true;
            }
            return super.mouseClicked(event, bl);
        }
        if (event.button() != 0) {
            return false;
        }
        String clicked = LauncherRenderer.getClickedButton(mouseX, mouseY);
        if (clicked == null) {
            return true;
        }
        Screen self = this;
        switch (clicked) {
            case "pause_backtogame": {
                mc.gui.setScreen(null);
                mc.mouseHandler.grabMouse();
                break;
            }
            case "pause_vayuclient_settings": 
            case "window": {
                mc.gui.setScreen((Screen)new ClickGUIScreen());
                break;
            }
            case "pause_friends":
            case "friends":
            case "social": {
                mc.gui.setScreen((Screen)new com.vayuclient.hud.gui.screens.FriendsScreen(self));
                break;
            }
            case "pause_store": {
                SystemToast.add((ToastManager)mc.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"VayuClient Store"), (Component)Component.literal((String)"Coming Soon"));
                break;
            }
            case "pause_modmenu": {
                OptionalMenuIntegrations.openModMenu(self);
                break;
            }
            case "pause_options": 
            case "settings": {
                mc.gui.setScreen((Screen)new OptionsScreen(self, mc.options, true));
                break;
            }
            case "pause_open_to_lan": {
                mc.gui.setScreen((Screen)new MultiplayerOptionsScreen(self));
                break;
            }
            case "box": {
                PauseScreenMixin.openResourcePacks(mc, self);
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
            case "pause_disconnect": {
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                break;
            }
            case "pause_advancements": {
                mc.gui.setScreen((Screen)new AdvancementsScreen(mc.player.connection.getAdvancements(), self));
                break;
            }
            case "pause_statistics": {
                mc.gui.setScreen((Screen)new StatsScreen(self, mc.player.getStats()));
                break;
            }
            case "pause_player_reporting": {
                mc.gui.setScreen((Screen)new SocialInteractionsScreen(self));
                break;
            }
            case "pause_minecraftfolder": {
                PauseScreenMixin.openGameDirectory(mc);
                break;
            }
            case "flashback_record_start": {
                OptionalMenuIntegrations.startFlashbackRecording();
                break;
            }
            case "flashback_record_finish": {
                OptionalMenuIntegrations.finishFlashbackRecording();
                break;
            }
            case "flashback_record_pause": {
                OptionalMenuIntegrations.pauseFlashbackRecording(true);
                break;
            }
            case "flashback_record_resume": {
                OptionalMenuIntegrations.pauseFlashbackRecording(false);
                break;
            }
            case "flashback_record_cancel": {
                OptionalMenuIntegrations.confirmCancelFlashbackRecording();
                break;
            }
            default: {
                SystemToast.add((ToastManager)mc.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"VayuClient"), (Component)Component.literal((String)"Coming Soon"));
            }
        }
        return true;
    }

    private static void openResourcePacks(Minecraft mc, Screen parent) {
        mc.gui.setScreen((Screen)new PackSelectionScreen(mc.getResourcePackRepository(), repository -> {
            mc.options.updateResourcePacks(repository);
            mc.gui.setScreen(parent);
        }, mc.getResourcePackDirectory(), (Component)Component.translatable((String)"resourcePack.title")));
    }

    private static void openGameDirectory(Minecraft mc) {
        boolean opened = false;
        if (PauseScreenMixin.isWindows()) {
            try {
                new ProcessBuilder("explorer.exe", mc.gameDirectory.getAbsolutePath()).start();
                opened = true;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (!opened) {
            try {
                Util.getPlatform().openFile(mc.gameDirectory);
                opened = true;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (!opened) {
            SystemToast.add((ToastManager)mc.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"VayuClient"), (Component)Component.literal((String)"Could not open folder"));
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private boolean showsMenu() {
        return ((PauseScreen)(Object)this).showsPauseMenu();
    }
}

