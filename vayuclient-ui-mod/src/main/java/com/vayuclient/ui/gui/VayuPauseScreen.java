package com.vayuclient.ui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

public class VayuPauseScreen extends VayuScreen {
    private final Screen parentScreen;

    public VayuPauseScreen(Screen parent) {
        super(Component.literal("Vayu Pause Menu"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = Math.max(80, this.height / 2 - 45);
        int btnWidth = 190;
        int btnHeight = 20;
        int spacing = 24;

        // 1. Back to Game
        this.addRenderableWidget(Button.builder(
            Component.literal("▶  BACK TO GAME"),
            btn -> this.onClose()
        ).bounds(centerX - btnWidth / 2, startY, btnWidth, btnHeight).build());

        // 2. Options
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙  OPTIONS"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing, btnWidth, btnHeight).build());

        // 3. Disconnect
        this.addRenderableWidget(Button.builder(
            Component.literal("🚪  SAVE & QUIT TO TITLE"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.disconnect(new VayuTitleScreen(), false);
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 2, btnWidth, btnHeight).build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(null);
        }
    }

    @Override
    protected void renderVayuForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = this.font;
        if (font == null) return;

        int centerX = this.width / 2;
        int titleY = Math.max(35, this.height / 2 - 85);

        String title = "GAME PAUSED";
        int w = font.width(title);
        graphics.text(font, title, centerX - w / 2, titleY, 0x00E5FF);

        String clientBrand = "VayuClient • In-Game Overlay";
        int bw = font.width(clientBrand);
        graphics.text(font, clientBrand, centerX - bw / 2, titleY + 11, 0x64748B);
    }
}
