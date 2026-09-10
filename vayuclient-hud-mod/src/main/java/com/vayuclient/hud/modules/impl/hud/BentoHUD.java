package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public class BentoHUD extends Module {
    private final BooleanSetting showFPS = this.register(new BooleanSetting("show_fps", "Show FPS Card", true));
    private final BooleanSetting showPing = this.register(new BooleanSetting("show_ping", "Show Ping Card", true));
    private final BooleanSetting showCoords = this.register(new BooleanSetting("show_coords", "Show Coordinates Card", true));
    private final BooleanSetting showBiome = this.register(new BooleanSetting("show_biome", "Show Biome Card", true));
    private final ColorSetting accentColor = this.register(new ColorSetting("accent_color", "Card accent border color", 0, 210, 255));

    public BentoHUD() {
        super("BentoHUD", "Modern Apple-style modular glass Bento grid dashboard", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || mc.player == null) return;

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        int curX = x;
        int cardH = 24;
        int accent = this.accentColor.getRGB() | 0xFF000000;

        // Card 1: FPS
        if (this.showFPS.isEnabled()) {
            int fps = mc.getFps();
            String label = fps + " FPS";
            int cardW = mc.font.width(label) + 20;
            drawBentoCard(graphics, curX, y, cardW, cardH, "⚡", label, accent);
            curX += cardW + 4;
        }

        // Card 2: Ping
        if (this.showPing.isEnabled()) {
            int ping = getPing();
            String label = ping + " ms";
            int cardW = mc.font.width(label) + 20;
            drawBentoCard(graphics, curX, y, cardW, cardH, "📶", label, accent);
            curX += cardW + 4;
        }

        // Card 3: Coords
        if (this.showCoords.isEnabled()) {
            BlockPos pos = mc.player.blockPosition();
            String label = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            int cardW = mc.font.width(label) + 20;
            drawBentoCard(graphics, curX, y, cardW, cardH, "📍", label, accent);
            curX += cardW + 4;
        }

        // Card 4: Biome
        if (this.showBiome.isEnabled() && mc.level != null) {
            Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
            String biome = biomeHolder.unwrapKey()
                .map(key -> key.identifier().getPath().replace('_', ' '))
                .orElse("unknown");
            String label = capitalize(biome);
            int cardW = mc.font.width(label) + 20;
            drawBentoCard(graphics, curX, y, cardW, cardH, "🌿", label, accent);
        }

        graphics.pose().popMatrix();
    }

    private static void drawBentoCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String icon, String text, int accent) {
        // Frosted Glass Base
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 4, 0xCC111827); // Dark sleek background
        VayuHUDUI.outline(graphics, x, y, w, h, 0x3300D2FF);       // Subtle cyan glow outline

        // Accent top line
        graphics.fill(x + 4, y + 1, x + w - 4, y + 2, accent);

        // Icon + Text
        graphics.text(mc.font, icon, x + 4, y + 8, 0xFFFFFFFF, true);
        graphics.text(mc.font, text, x + 16, y + 8, 0xFFE0E0E0, true);
    }

    private int getPing() {
        if (mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) return info.getLatency();
        }
        return 0;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        char[] chars = s.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        for (int i = 1; i < chars.length; i++) {
            if (chars[i - 1] == ' ') chars[i] = Character.toUpperCase(chars[i]);
        }
        return new String(chars);
    }
}
