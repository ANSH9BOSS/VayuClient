package com.vayuclient.ui.gui;

import com.vayuclient.ui.core.EnvironmentResolver;
import com.vayuclient.ui.core.VayuUIDesignSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public class VayuTitleScreen extends VayuScreen {

    private float animTime = 0.0f;
    private int hoveredDockIndex = -1;

    public VayuTitleScreen() {
        super(Component.literal("VayuClient"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int mainBtnW = 240;
        int mainBtnH = 24;
        int startY = centerY - 14;

        // 1. Center Primary Button: Singleplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("👤  Singleplayer"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new SelectWorldScreen(this));
                }
            }
        ).bounds(centerX - mainBtnW / 2, startY, mainBtnW, mainBtnH).build());

        // 2. Center Primary Button: Multiplayer
        this.addRenderableWidget(Button.builder(
            Component.literal("🎮  Multiplayer"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                }
            }
        ).bounds(centerX - mainBtnW / 2, startY + 28, mainBtnW, mainBtnH).build());

        // 3. Center Split Row: Discover (Servers) & Store (Cosmetics)
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
            btn -> openModsOrConfig()
        ).bounds(centerX - mainBtnW / 2 + splitW + 6, startY + 56, splitW, mainBtnH).build());

        // 4. Top-Left Quick Action: Profile / Account
        this.addRenderableWidget(Button.builder(
            Component.literal("+ Account"),
            btn -> {}
        ).bounds(14, 14, 68, 18).build());

        // 5. Top-Right Quick Action: Options
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        ).bounds(this.width - 56, 14, 20, 18).build());

        // 6. Top-Right Quick Action: Quit Game
        this.addRenderableWidget(Button.builder(
            Component.literal("✕"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.stop();
                }
            }
        ).bounds(this.width - 32, 14, 20, 18).build());
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
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
            }
        }
    }

    @Override
    protected void renderVayuForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = this.font;
        if (font == null) return;

        animTime += (delta > 0 ? delta : 0.05f) * 0.03f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTime * 3.0f);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // ─── 1. TOP-LEFT: Real Account Card & Status ───────────────────────────
        String username = "ANSH9BOSS";
        String accountType = "Microsoft Account";
        try {
            if (this.minecraft != null && this.minecraft.getUser() != null) {
                String u = this.minecraft.getUser().getName();
                if (u != null && !u.isEmpty()) username = u;
                if (this.minecraft.getUser().getProfileId() != null) {
                    accountType = "Active Profile";
                }
            }
        } catch (Throwable ignored) {}

        // Status Pill below button: "● Connected"
        int statusPillX = 14;
        int statusPillY = 36;
        int statusPillW = 86;
        int statusPillH = 18;
        VayuUIDesignSystem.drawPill(graphics, statusPillX, statusPillY, statusPillW, statusPillH, 0xCC090E17, 0x22FFFFFF);
        graphics.fill(statusPillX + 6, statusPillY + 7, statusPillX + 10, statusPillY + 11, 0xFF00F0FF);
        graphics.text(font, "Connected", statusPillX + 14, statusPillY + 5, 0xFF38BDF8);

        // Account Details Card
        int accCardX = 14;
        int accCardY = 58;
        int accCardW = 140;
        int accCardH = 30;
        VayuUIDesignSystem.drawPill(graphics, accCardX, accCardY, accCardW, accCardH, 0xAA0C1220, 0x2238BDF8);
        
        // Avatar Box
        int headX = accCardX + 5;
        int headY = accCardY + 5;
        graphics.fill(headX, headY, headX + 20, headY + 20, 0xFF1E293B);
        graphics.fill(headX + 2, headY + 2, headX + 18, headY + 18, 0xFF00D2FF);

        // Username & Account Type
        graphics.text(font, username, headX + 26, accCardY + 6, VayuUIDesignSystem.COLOR_TEXT_PRIMARY);
        graphics.text(font, accountType, headX + 26, accCardY + 16, VayuUIDesignSystem.COLOR_TEXT_MUTED);

        // ─── 2. TOP-RIGHT: Coin Counter & Controls ─────────────────────────────
        int coinX = this.width - 110;
        int coinY = 14;
        VayuUIDesignSystem.drawPill(graphics, coinX, coinY, 48, 18, 0xCC090E17, 0x33F59E0B);
        graphics.text(font, "★ 0", coinX + 8, coinY + 5, VayuUIDesignSystem.COLOR_ACCENT_GOLD);

        // ─── 3. CENTER: VayuClient Logo & Insignia ─────────────────────────────
        int emblemY = centerY - 72;
        VayuUIDesignSystem.drawVayuEmblem(graphics, centerX, emblemY, pulse);

        String brandTitle = "VAYUCLIENT";
        int brandW = font.width(brandTitle);
        graphics.text(font, brandTitle, centerX - brandW / 2, emblemY + 36, VayuUIDesignSystem.COLOR_ACCENT_CYAN);

        // ─── 4. BOTTOM-CENTER: Quick-Access Icon Dock ─────────────────────────
        int dockW = 230;
        int dockH = 26;
        int dockX = centerX - dockW / 2;
        int dockY = this.height - dockH - 14;

        VayuUIDesignSystem.drawPill(graphics, dockX, dockY, dockW, dockH, 0xDD090D18, 0x33FFFFFF);

        String[] dockIcons = {"🌙", "👕", "💬", "👥", "⚙", "🌐", "📷"};
        int iconCount = dockIcons.length;
        int itemW = dockW / iconCount;

        for (int i = 0; i < iconCount; i++) {
            int ix = dockX + (i * itemW);
            boolean isHov = mouseX >= ix && mouseX < ix + itemW && mouseY >= dockY && mouseY < dockY + dockH;
            if (isHov) {
                graphics.fill(ix + 2, dockY + 2, ix + itemW - 2, dockY + dockH - 2, 0x4438BDF8);
            }
            int tw = font.width(dockIcons[i]);
            graphics.text(font, dockIcons[i], ix + (itemW - tw) / 2, dockY + 8, isHov ? 0xFF00F0FF : 0xFF94A3B8);
        }

        // ─── 5. BOTTOM-RIGHT: Featured / Partnered Showcase Card ───────────────
        int cardW = 160;
        int cardH = 64;
        int cardX = this.width - cardW - 14;
        int cardY = this.height - cardH - 30;

        VayuUIDesignSystem.drawGlassCard(graphics, cardX, cardY, cardW, cardH, 0xEE090E1A, 0x3338BDF8);
        
        // Inner card badge & preview
        graphics.fill(cardX + 8, cardY + 8, cardX + 44, cardY + 44, 0xFF1E293B);
        graphics.fill(cardX + 10, cardY + 10, cardX + 42, cardY + 42, 0xFF00D2FF);
        
        graphics.text(font, "Vayu Partner", cardX + 50, cardY + 12, VayuUIDesignSystem.COLOR_TEXT_PRIMARY);
        graphics.text(font, "Featured Hub", cardX + 50, cardY + 24, VayuUIDesignSystem.COLOR_TEXT_MUTED);

        // Status badge inside card
        int badgeX = cardX + 50;
        int badgeY = cardY + 36;
        VayuUIDesignSystem.drawPill(graphics, badgeX, badgeY, 52, 12, 0xDD064E3B, 0x6610B981);
        graphics.text(font, "ONLINE", badgeX + 8, badgeY + 2, VayuUIDesignSystem.COLOR_ACCENT_EMERALD);

        // Carousel dot dashes underneath card
        int dotY = cardY + cardH + 4;
        graphics.fill(cardX + 50, dotY, cardX + 66, dotY + 2, 0xFFFFFFFF);
        graphics.fill(cardX + 70, dotY, cardX + 86, dotY + 2, 0x44FFFFFF);
        graphics.fill(cardX + 90, dotY, cardX + 106, dotY + 2, 0x44FFFFFF);

        // ─── 6. FOOTER: Build Metadata & Legal Disclaimer ─────────────────────
        String mcVer = EnvironmentResolver.getMinecraftVersion();
        String loader = EnvironmentResolver.getLoader();
        String footerLeft = "VayuClient 1.6.2 (" + mcVer + "-" + loader.toLowerCase() + ")";
        graphics.text(font, footerLeft, 14, this.height - 14, 0x44FFFFFF);

        String legalText = "Not affiliated with Mojang or Microsoft.";
        int legalW = font.width(legalText);
        graphics.text(font, legalText, this.width - legalW - 14, this.height - 14, 0x44FFFFFF);
    }
}
