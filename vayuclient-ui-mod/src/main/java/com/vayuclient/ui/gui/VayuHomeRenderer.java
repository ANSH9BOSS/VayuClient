package com.vayuclient.ui.gui;

import com.vayuclient.ui.core.EnvironmentResolver;
import com.vayuclient.ui.core.VayuBackgroundProvider;
import com.vayuclient.ui.core.VayuParticleEngine;
import com.vayuclient.ui.core.VayuUIDesignSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class VayuHomeRenderer {

    private static final VayuParticleEngine particles = new VayuParticleEngine();
    private static float animTime = 0.0f;
    private static boolean particlesInit = false;

    private VayuHomeRenderer() {}

    public static void render(Screen screen, GuiGraphicsExtractor graphics, Font font, int width, int height, int mouseX, int mouseY, float delta) {
        if (graphics == null) return;

        if (!particlesInit) {
            particles.init(width, height);
            particlesInit = true;
        }

        animTime += (delta > 0 ? delta : 0.05f) * 0.03f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTime * 3.0f);

        int centerX = width / 2;
        int centerY = height / 2;

        // 1. Background Backdrop & Dark Gradient
        VayuBackgroundProvider.getInstance().renderBackgroundWithBlur(graphics, width, height, delta);

        // 2. Floating Ambient Cyber Particles
        particles.renderAndTick(graphics, width, height, delta);

        // 3. Center VayuClient Insignia & Logo
        int emblemY = centerY - 72;
        VayuUIDesignSystem.drawVayuEmblem(graphics, centerX, emblemY, pulse);

        if (font != null) {
            String brandTitle = "VAYUCLIENT";
            int brandW = font.width(brandTitle);
            graphics.text(font, brandTitle, centerX - brandW / 2, emblemY + 36, VayuUIDesignSystem.COLOR_ACCENT_CYAN);
        }

        // 4. TOP-LEFT: Real Player Account Card
        String username = "ANSH9BOSS";
        String accountType = "Microsoft Account";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String u = mc.getUser().getName();
            if (u != null && !u.isEmpty()) username = u;
            if (mc.getUser().getProfileId() != null) {
                accountType = "Active Profile";
            }
        }

        int statusPillX = 14;
        int statusPillY = 36;
        int statusPillW = 86;
        int statusPillH = 18;
        VayuUIDesignSystem.drawPill(graphics, statusPillX, statusPillY, statusPillW, statusPillH, 0xCC090E17, 0x22FFFFFF);
        graphics.fill(statusPillX + 6, statusPillY + 7, statusPillX + 10, statusPillY + 11, 0xFF00F0FF);
        if (font != null) {
            graphics.text(font, "Connected", statusPillX + 14, statusPillY + 5, 0xFF38BDF8);
        }

        int accCardX = 14;
        int accCardY = 58;
        int accCardW = 140;
        int accCardH = 30;
        VayuUIDesignSystem.drawPill(graphics, accCardX, accCardY, accCardW, accCardH, 0xAA0C1220, 0x2238BDF8);

        int headX = accCardX + 5;
        int headY = accCardY + 5;
        graphics.fill(headX, headY, headX + 20, headY + 20, 0xFF1E293B);
        graphics.fill(headX + 2, headY + 2, headX + 18, headY + 18, 0xFF00D2FF);

        if (font != null) {
            graphics.text(font, username, headX + 26, accCardY + 6, VayuUIDesignSystem.COLOR_TEXT_PRIMARY);
            graphics.text(font, accountType, headX + 26, accCardY + 16, VayuUIDesignSystem.COLOR_TEXT_MUTED);
        }

        // 5. TOP-RIGHT: Coin Counter
        int coinX = width - 110;
        int coinY = 14;
        VayuUIDesignSystem.drawPill(graphics, coinX, coinY, 48, 18, 0xCC090E17, 0x33F59E0B);
        if (font != null) {
            graphics.text(font, "★ 0", coinX + 8, coinY + 5, VayuUIDesignSystem.COLOR_ACCENT_GOLD);
        }

        // 6. BOTTOM-CENTER: Quick-Access Icon Dock
        int dockW = 230;
        int dockH = 26;
        int dockX = centerX - dockW / 2;
        int dockY = height - dockH - 14;

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
            if (font != null) {
                int tw = font.width(dockIcons[i]);
                graphics.text(font, dockIcons[i], ix + (itemW - tw) / 2, dockY + 8, isHov ? 0xFF00F0FF : 0xFF94A3B8);
            }
        }

        // 7. BOTTOM-RIGHT: Featured / Partnered Showcase Card
        int cardW = 160;
        int cardH = 64;
        int cardX = width - cardW - 14;
        int cardY = height - cardH - 30;

        VayuUIDesignSystem.drawGlassCard(graphics, cardX, cardY, cardW, cardH, 0xEE090E1A, 0x3338BDF8);
        graphics.fill(cardX + 8, cardY + 8, cardX + 44, cardY + 44, 0xFF1E293B);
        graphics.fill(cardX + 10, cardY + 10, cardX + 42, cardY + 42, 0xFF00D2FF);

        if (font != null) {
            graphics.text(font, "Vayu Partner", cardX + 50, cardY + 12, VayuUIDesignSystem.COLOR_TEXT_PRIMARY);
            graphics.text(font, "Featured Hub", cardX + 50, cardY + 24, VayuUIDesignSystem.COLOR_TEXT_MUTED);
        }

        int badgeX = cardX + 50;
        int badgeY = cardY + 36;
        VayuUIDesignSystem.drawPill(graphics, badgeX, badgeY, 52, 12, 0xDD064E3B, 0x6610B981);
        if (font != null) {
            graphics.text(font, "ONLINE", badgeX + 8, badgeY + 2, VayuUIDesignSystem.COLOR_ACCENT_EMERALD);
        }

        int dotY = cardY + cardH + 4;
        graphics.fill(cardX + 50, dotY, cardX + 66, dotY + 2, 0xFFFFFFFF);
        graphics.fill(cardX + 70, dotY, cardX + 86, dotY + 2, 0x44FFFFFF);
        graphics.fill(cardX + 90, dotY, cardX + 106, dotY + 2, 0x44FFFFFF);

        // 8. FOOTER: Build Metadata & Legal Disclaimer
        if (font != null) {
            String mcVer = EnvironmentResolver.getMinecraftVersion();
            String loader = EnvironmentResolver.getLoader();
            String footerLeft = "VayuClient 1.6.2 (" + mcVer + "-" + loader.toLowerCase() + ")";
            graphics.text(font, footerLeft, 14, height - 14, 0x44FFFFFF);

            String legalText = "Not affiliated with Mojang or Microsoft.";
            int legalW = font.width(legalText);
            graphics.text(font, legalText, width - legalW - 14, height - 14, 0x44FFFFFF);
        }
    }
}
