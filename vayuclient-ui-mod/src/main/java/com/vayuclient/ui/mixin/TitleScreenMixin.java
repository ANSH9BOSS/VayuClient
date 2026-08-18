package com.vayuclient.ui.mixin;

import com.vayuclient.ui.VayuClientUI;
import com.vayuclient.ui.gui.VayuHomeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (!VayuClientUI.isEnabled()) return;

        this.clearWidgets();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int mainBtnW = 240;
        int mainBtnH = 24;
        int startY = centerY - 14;

        this.addRenderableWidget(Button.builder(
            Component.literal("👤  Singleplayer"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new SelectWorldScreen(this));
                }
            }
        ).bounds(centerX - mainBtnW / 2, startY, mainBtnW, mainBtnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("🎮  Multiplayer"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                }
            }
        ).bounds(centerX - mainBtnW / 2, startY + 28, mainBtnW, mainBtnH).build());

        int splitW = (mainBtnW - 6) / 2;
        this.addRenderableWidget(Button.builder(
            Component.literal("🌐 Discover"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                }
            }
        ).bounds(centerX - mainBtnW / 2, startY + 56, splitW, mainBtnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("🛒 Store"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(centerX - mainBtnW / 2 + splitW + 6, startY + 56, splitW, mainBtnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("⚙"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(this.width - 56, 14, 20, 18).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("✕"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.stop();
                }
            }
        ).bounds(this.width - 32, 14, 20, 18).build());
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!VayuClientUI.isEnabled()) return;

        try {
            VayuHomeRenderer.render(this, graphics, this.font, this.width, this.height, mouseX, mouseY, delta);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            ci.cancel();
        } catch (Throwable t) {
            // Safe fallback
        }
    }
}
