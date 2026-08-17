package com.vayuclient.ui.gui;

import com.vayuclient.ui.core.EnvironmentResolver;
import com.vayuclient.ui.platform.IRenderBackend;
import com.vayuclient.ui.platform.VayuPlatformConfig;
import com.vayuclient.ui.core.VayuUIAdapterFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class VayuWelcomeScreen extends VayuScreen {
    private float introAnim = 0.0f;

    public VayuWelcomeScreen() {
        super(Component.literal("Welcome to VayuClient"));
    }

    @Override
    protected void init() {
        super.init();
        this.introAnim = 0.0f;

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int btnWidth = 220;
        int btnHeight = 26;
        int btnY = centerY + 45;

        // Continue to VayuClient Home
        this.addRenderableWidget(Button.builder(
            Component.literal("🚀  CONTINUE TO CLIENT"),
            btn -> {
                VayuPlatformConfig.setFirstLaunchCompleted(true);
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new VayuTitleScreen());
                }
            }
        ).bounds(centerX - btnWidth / 2, btnY, btnWidth, btnHeight).build());
    }

    @Override
    protected void renderVayuForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (introAnim < 1.0f) {
            introAnim = Math.min(1.0f, introAnim + (0.05f * (delta > 0 ? delta : 1.0f)));
        }

        Font font = this.font;
        if (font == null) return;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 1. Central Cinematic Glass Welcome Hero Panel
        int panelW = 440;
        int panelH = 220;
        int panelX = centerX - panelW / 2;
        int panelY = centerY - panelH / 2 - 10;

        if (adapter != null) {
            adapter.renderGlassPanel(graphics, panelX, panelY, panelW, panelH, 0xD8050914, 0x4400D2FF);
        } else {
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD8050914);
        }

        // Top Accent Neon Bar
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00D2FF);

        // 2. Brand Header with entrance offset
        int animOffsetY = (int) ((1.0f - introAnim) * 15.0f);
        String brand = "VAYUCLIENT";
        String welcome = "Welcome to VayuClient";
        String tag = "Your Minecraft. Your way.";

        int brandW = font.width(brand);
        graphics.text(font, brand, centerX - brandW / 2, panelY + 24 + animOffsetY, 0x00E5FF);

        int welW = font.width(welcome);
        graphics.text(font, welcome, centerX - welW / 2, panelY + 44 + animOffsetY, 0xFFFFFF);

        int tagW = font.width(tag);
        graphics.text(font, tag, centerX - tagW / 2, panelY + 60 + animOffsetY, 0x94A3B8);

        // 3. User Identity & Status Tag
        String username = "ANSH9BOSS";
        try {
            if (this.minecraft != null && this.minecraft.getUser() != null) {
                String u = this.minecraft.getUser().getName();
                if (u != null && !u.isEmpty()) username = u;
            }
        } catch (Throwable ignored) {}

        String accountPill = "● Logged in as: " + username;
        int accW = font.width(accountPill);
        int accX = centerX - accW / 2;
        int accY = panelY + 88;

        graphics.fill(accX - 10, accY - 4, accX + accW + 10, accY + 14, 0x3310B981);
        graphics.text(font, accountPill, accX, accY, 0x34D399);

        // 4. Subtle Bottom Environment Footer
        String env = "Environment: Minecraft " + EnvironmentResolver.getMinecraftVersion() + " • " + EnvironmentResolver.getLoader() + " • " + EnvironmentResolver.getRenderer();
        int envW = font.width(env);
        graphics.text(font, env, centerX - envW / 2, this.height - 18, 0x64748B);
    }
}
