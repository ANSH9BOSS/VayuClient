package com.vayuclient.ui.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class VayuBackgroundProvider implements IVayuBackgroundProvider {
    private static final VayuBackgroundProvider INSTANCE = new VayuBackgroundProvider();
    
    public static VayuBackgroundProvider getInstance() {
        return INSTANCE;
    }

    private static final Identifier BG_PVP_ARENA = Identifier.fromNamespaceAndPath("vayuclient-ui", "textures/bg_pvp_arena.png");
    private static final Identifier BG_MOUNTAIN_AURORA = Identifier.fromNamespaceAndPath("vayuclient-ui", "textures/bg_mountain_aurora.png");
    private static final Identifier BG_CHERRY_GROVE = Identifier.fromNamespaceAndPath("vayuclient-ui", "textures/bg_cherry_grove.png");
    private static final Identifier BG_LUSH_CAVES = Identifier.fromNamespaceAndPath("vayuclient-ui", "textures/bg_lush_caves.png");

    private float animTime = 0.0f;

    @Override
    public Identifier resolveBackground(String mcVersion, String theme) {
        if (mcVersion == null) mcVersion = "26.2";
        mcVersion = mcVersion.toLowerCase();

        if (mcVersion.startsWith("26.2")) {
            return BG_PVP_ARENA;
        } else if (mcVersion.startsWith("26.1")) {
            return BG_MOUNTAIN_AURORA;
        } else if (mcVersion.startsWith("1.21")) {
            return BG_CHERRY_GROVE;
        } else {
            return BG_LUSH_CAVES;
        }
    }

    @Override
    public void renderBackgroundWithBlur(GuiGraphicsExtractor graphics, int width, int height, float delta) {
        animTime += (delta > 0 ? delta * 0.015f : 0.015f);

        // 1. Base Dark Cyber Backdrop
        graphics.fill(0, 0, width, height, 0xFF050811);

        // 2. Subtle Cinematic Color Gradient / Atmospheric Wash
        int topGradient = 0x880B132B;
        int midGradient = 0xAA060913;
        int botGradient = 0xEE030509;

        graphics.fillGradient(0, 0, width, height / 2, topGradient, midGradient);
        graphics.fillGradient(0, height / 2, width, height, midGradient, botGradient);

        // 3. Ambient Cyber Glow Accents (Left Cyan, Right Blue)
        int glowWidth = Math.max(120, width / 4);
        graphics.fillGradient(0, 0, glowWidth, height, 0x1800D2FF, 0x0000D2FF);
        graphics.fillGradient(width - glowWidth, 0, width, height, 0x003B82F6, 0x183B82F6);

        // 4. Subtle Vignette Edge Darkening
        graphics.fill(0, 0, width, 24, 0x55000000);
        graphics.fill(0, height - 28, width, height, 0x77000000);
    }
}
