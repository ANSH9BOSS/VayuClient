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
        int centerY = this.height / 2;
        int btnWidth = 190;
        int btnHeight = 22;
        int spacing = 26;
        int startY = centerY - 32;

        // 1. Primary Action: Singleplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("⚔  SINGLEPLAYER"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new SelectWorldScreen(this));
                }
            }
        ).bounds(centerX - btnWidth - 6, startY, btnWidth, btnHeight).build());

        // 2. Primary Action: Multiplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("🌐  MULTIPLAYER"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                }
            }
        ).bounds(centerX + 6, startY, btnWidth, btnHeight).build());

        // 3. Secondary Actions: Discover & Store / Vayu Mods
        this.addRenderableWidget(Button.builder(
            Component.literal("✨  DISCOVER MODS"),
            btn -> openModsOrConfig()
        ).bounds(centerX - btnWidth - 6, startY + spacing, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("💎  STORE & COSMETICS"),
            btn -> openModsOrConfig()
        ).bounds(centerX + 6, startY + spacing, btnWidth, btnHeight).build());

        // 4. Center Bottom: Options & Quit
        int smallWidth = 120;
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙  OPTIONS"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(centerX - smallWidth - 4, startY + spacing * 2 + 4, smallWidth, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("✖  QUIT"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.stop();
                }
            }
        ).bounds(centerX + 4, startY + spacing * 2 + 4, smallWidth, 20).build());
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
        int centerY = this.height / 2;

        // ─── 1. TOP-LEFT: Player Account Card ────────────────────────────────
        String username = "ANSH9BOSS";
        String accountType = "Microsoft Account";
        try {
            if (this.minecraft != null && this.minecraft.getUser() != null) {
                String u = this.minecraft.getUser().getName();
                if (u != null && !u.isEmpty()) username = u;
                if (this.minecraft.getUser().getType() != null) {
                    accountType = this.minecraft.getUser().getType().toString().equalsIgnoreCase("msa") ? "Microsoft Account" : "Offline / Local";
                }
            }
        } catch (Throwable ignored) {}

        int accountCardX = 14;
        int accountCardY = 14;
        int accountCardW = 160;
        int accountCardH = 36;

        // Glass panel for account card
        if (adapter != null) {
            adapter.renderGlassPanel(graphics, accountCardX, accountCardY, accountCardW, accountCardH, 0xD0080D1A, 0x3300D2FF);
        } else {
            graphics.fill(accountCardX, accountCardY, accountCardX + accountCardW, accountCardY + accountCardH, 0xD0080D1A);
        }

        // Avatar Head Box
        int headSize = 22;
        int headX = accountCardX + 7;
        int headY = accountCardY + 7;
        graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xFF1E293B);
        graphics.fill(headX + 2, headY + 2, headX + headSize - 2, headY + headSize - 2, 0xFF00D2FF);

        // Status Indicator Dot (Green) + Username
        int textX = headX + headSize + 8;
        graphics.fill(textX, accountCardY + 9, textX + 5, accountCardY + 14, 0xFF10B981);
        graphics.text(font, username, textX + 8, accountCardY + 8, 0xFFFFFF);
        graphics.text(font, accountType, textX + 8, accountCardY + 20, 0x64748B);

        // ─── 2. TOP-RIGHT: Controls & Status HUD ──────────────────────────────
        int trCardW = 140;
        int trCardH = 32;
        int trCardX = this.width - trCardW - 14;
        int trCardY = 14;

        if (adapter != null) {
            adapter.renderGlassPanel(graphics, trCardX, trCardY, trCardW, trCardH, 0xD0080D1A, 0x3300D2FF);
        }

        graphics.text(font, "● Vayu Online", trCardX + 12, trCardY + 8, 0x38BDF8);
        graphics.text(font, "Ping: 24ms", trCardX + 12, trCardY + 19, 0x64748B);

        // ─── 3. CENTER: VayuClient Emblem & Typography ─────────────────────────
        String brandTitle = "VAYUCLIENT";
        String brandSubtitle = "PREMIUM MINECRAFT CLIENT";
        String badge = "v1.6.0";

        int titleY = Math.max(20, centerY - 110);
        int titleW = font.width(brandTitle);

        // Centered Main Glass Plate behind Title
        int plateW = 420;
        int plateH = 50;
        int plateX = centerX - plateW / 2;
        int plateY = titleY - 8;
        if (adapter != null) {
            adapter.renderGlassPanel(graphics, plateX, plateY, plateW, plateH, 0xB0060913, 0x2200D2FF);
        }

        // Title with Cyan Glow Accent
        graphics.text(font, brandTitle, centerX - titleW / 2, titleY + 4, 0x00E5FF);

        // Subtitle
        int subW = font.width(brandSubtitle);
        graphics.text(font, brandSubtitle, centerX - subW / 2, titleY + 18, 0x94A3B8);

        // Version Badge
        int bX = centerX + titleW / 2 + 8;
        int bY = titleY + 2;
        graphics.fill(bX, bY, bX + 42, bY + 12, 0x4400D2FF);
        graphics.fill(bX, bY, bX + 42, bY + 1, 0xFF00D2FF);
        graphics.text(font, badge, bX + 4, bY + 2, 0x38BDF8);

        // ─── 4. BOTTOM NAVIGATION DOCK ────────────────────────────────────────
        int dockW = Math.min(560, this.width - 32);
        int dockH = 26;
        int dockX = centerX - dockW / 2;
        int dockY = this.height - dockH - 12;

        if (adapter != null) {
            adapter.renderGlassPanel(graphics, dockX, dockY, dockW, dockH, 0xDD050811, 0x3300D2FF);
        } else {
            graphics.fill(dockX, dockY, dockX + dockW, dockY + dockH, 0xDD050811);
        }

        // Environment pill on left of dock
        String mcVer = EnvironmentResolver.getMinecraftVersion();
        String loader = EnvironmentResolver.getLoader();
        String renderer = EnvironmentResolver.getRenderer();
        String env = "Minecraft " + mcVer + " • " + loader + " • " + renderer;
        graphics.text(font, env, dockX + 12, dockY + 9, 0x64748B);

        // Navigation links on right of dock
        String navLinks = "Mods  |  Cosmetics  |  Friends  |  Settings";
        int navW = font.width(navLinks);
        graphics.text(font, navLinks, dockX + dockW - navW - 12, dockY + 9, 0x94A3B8);
    }
}
