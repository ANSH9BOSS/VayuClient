package com.vayuclient.ui.gui;

import com.vayuclient.ui.core.EnvironmentResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public class VayuTitleScreen extends VayuScreen {

    public VayuTitleScreen() {
        super(Component.literal("VayuClient Home"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = Math.max(70, this.height / 2 - 60);
        int btnWidth = 200;
        int btnHeight = 20;
        int spacing = 24;

        // 1. Singleplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("⚔  SINGLEPLAYER"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new SelectWorldScreen(this));
                }
            }
        ).bounds(centerX - btnWidth / 2, startY, btnWidth, btnHeight).build());

        // 2. Multiplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("🌐  MULTIPLAYER"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing, btnWidth, btnHeight).build());

        // 3. Vayu Mods & Addons
        this.addRenderableWidget(Button.builder(
            Component.literal("⚡  VAYU CLIENT MODS"),
            btn -> openModsOrConfig()
        ).bounds(centerX - btnWidth / 2, startY + spacing * 2, btnWidth, btnHeight).build());

        // 4. Options & Settings
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙  OPTIONS & VIDEO"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 3, btnWidth, btnHeight).build());

        // 5. Quit Game
        this.addRenderableWidget(Button.builder(
            Component.literal("✖  QUIT GAME"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.stop();
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 4, btnWidth, btnHeight).build());
    }

    private void openModsOrConfig() {
        try {
            Class<?> configCls = Class.forName("com.ansh9boss.lungehelper.client.gui.LungeConfigScreen");
            var ctor = configCls.getConstructor(net.minecraft.client.gui.screens.Screen.class);
            var screen = (net.minecraft.client.gui.screens.Screen) ctor.newInstance(this);
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(screen);
            }
        } catch (Throwable t) {
            System.out.println("[VayuClient UI] Mods screen opening fallback: " + t.getMessage());
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
            }
        }
    }

    @Override
    protected void renderVayuForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = this.font;
        if (font == null) return;

        int centerX = this.width / 2;

        // 1. Header Branding
        String title = "VAYUCLIENT";
        String subtitle = "PREMIUM MINECRAFT CLIENT";
        String versionBadge = "v1.6.0";

        int titleY = Math.max(16, this.height / 2 - 110);
        int titleWidth = font.width(title);
        
        // Brand Title with Cyan Glow Accent
        graphics.text(font, title, centerX - titleWidth / 2, titleY, 0x00E5FF);
        
        // Subtitle
        int subWidth = font.width(subtitle);
        graphics.text(font, subtitle, centerX - subWidth / 2, titleY + 11, 0x94A3B8);

        // Version Badge Pill
        int badgeX = centerX + titleWidth / 2 + 6;
        int badgeY = titleY - 2;
        graphics.fill(badgeX, badgeY, badgeX + 38, badgeY + 11, 0x3300D2FF);
        graphics.fill(badgeX, badgeY, badgeX + 38, badgeY + 1, 0xFF00D2FF);
        graphics.text(font, versionBadge, badgeX + 3, badgeY + 2, 0x38BDF8);

        // 2. Bottom Environment Footer Pill
        String mcVer = EnvironmentResolver.getMinecraftVersion();
        String loader = EnvironmentResolver.getLoader();
        String renderer = EnvironmentResolver.getRenderer();
        String envText = "Minecraft " + mcVer + " • " + loader + " • " + renderer;

        int footerY = this.height - 16;
        graphics.fill(0, footerY - 4, this.width, this.height, 0xCC050811);
        graphics.fill(0, footerY - 4, this.width, footerY - 3, 0x2200D2FF);
        graphics.text(font, envText, 10, footerY, 0x64748B);

        // User Account Tag
        String username = "Player";
        try {
            if (this.minecraft != null && this.minecraft.getUser() != null) {
                username = this.minecraft.getUser().getName();
            }
        } catch (Throwable ignored) {}
        
        String userTag = "Account: " + username;
        int userWidth = font.width(userTag);
        graphics.text(font, userTag, this.width - userWidth - 10, footerY, 0x38BDF8);
    }
}
