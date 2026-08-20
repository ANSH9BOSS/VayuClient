/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.world.level.biome.Biome
 */
package net.fastclient.hud.modules.impl.hud;

import java.util.Locale;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public class BiomeHUD
extends Module {
    private final BooleanSetting showLabel = this.register(new BooleanSetting("show_label", "Show 'Biome:' label", true));
    private final BooleanSetting formatName = this.register(new BooleanSetting("format_name", "Format biome name", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));
    private final ColorSetting color = this.register(new ColorSetting("color", "Text color", 100, 200, 100));

    public BiomeHUD() {
        super("Biome", "Shows the current biome", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        Object text;
        if (!this.isInGame() || BiomeHUD.mc.player == null || BiomeHUD.mc.level == null) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        BlockPos pos = BiomeHUD.mc.player.blockPosition();
        Holder biomeHolder = BiomeHUD.mc.level.getBiome(pos);
        String biomeName = this.getBiomeName((Holder<Biome>)biomeHolder);
        if (this.formatName.isEnabled()) {
            biomeName = this.formatBiomeName(biomeName);
        }
        Object object = text = this.showLabel.isEnabled() ? "Biome: " + biomeName : biomeName;
        if (this.background.isEnabled()) {
            FastClientUI.hudText(graphics, BiomeHUD.mc.font, (String)text, x, y, this.color.getRGB() | 0xFF000000, true);
        } else {
            graphics.text(BiomeHUD.mc.font, (String)text, x, y, this.color.getRGB() | 0xFF000000, true);
        }
        graphics.pose().popMatrix();
    }

    private String getBiomeName(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
    }

    private String formatBiomeName(String name) {
        String[] parts = name.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)).append(" ");
        }
        return formatted.toString().trim();
    }

    @Override
    public int getHudWidth() {
        if (BiomeHUD.mc.player == null || BiomeHUD.mc.level == null) {
            return 80;
        }
        BlockPos pos = BiomeHUD.mc.player.blockPosition();
        Holder biomeHolder = BiomeHUD.mc.level.getBiome(pos);
        String biomeName = this.getBiomeName((Holder<Biome>)biomeHolder);
        if (this.formatName.isEnabled()) {
            biomeName = this.formatBiomeName(biomeName);
        }
        Object text = this.showLabel.isEnabled() ? "Biome: " + biomeName : biomeName;
        return BiomeHUD.mc.font.width((String)text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

