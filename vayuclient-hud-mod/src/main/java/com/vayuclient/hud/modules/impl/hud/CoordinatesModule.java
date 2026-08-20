/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 */
package com.vayuclient.hud.modules.impl.hud;

import java.util.ArrayList;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

public class CoordinatesModule
extends Module {
    private final ModeSetting displayMode = this.register(new ModeSetting("display_mode", "Display format", "compact", new String[]{"compact", "detailed", "minimal"}));
    private final BooleanSetting showDimension = this.register(new BooleanSetting("show_dimension", "Show dimension", true));
    private final BooleanSetting showDirection = this.register(new BooleanSetting("show_direction", "Show facing direction", true));
    private final BooleanSetting showBiome = this.register(new BooleanSetting("show_biome", "Show biome", false));
    private final ModeSetting colorMode = this.register(new ModeSetting("color_mode", "Color mode", "static", new String[]{"static", "axis"}));
    private final ColorSetting textColor = this.register(new ColorSetting("color", "Text color", 255, 255, 255));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));

    public CoordinatesModule() {
        super("Coordinates", "Display player coordinates on screen", Category.HUD);
        this.textColor.visibleWhen(() -> this.colorMode.is("static"));
        this.bgOpacity.visibleWhen(this.background::isEnabled);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        String[] lines;
        if (!this.isInGame() || CoordinatesModule.mc.player == null) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        double playerX = CoordinatesModule.mc.player.getX();
        double playerY = CoordinatesModule.mc.player.getY();
        double playerZ = CoordinatesModule.mc.player.getZ();
        int lineHeight = 11;
        int currentY = y;
        int maxWidth = 0;
        for (String line : lines = this.buildDisplayLines(playerX, playerY, playerZ)) {
            int lineWidth = CoordinatesModule.mc.font.width(line);
            if (lineWidth <= maxWidth) continue;
            maxWidth = lineWidth;
        }
        if (((Boolean)this.background.getValue()).booleanValue()) {
            int totalHeight = lines.length * lineHeight + 6;
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, maxWidth + 10, totalHeight, this.bgOpacity.getIntValue());
        }
        for (int i = 0; i < lines.length; ++i) {
            int color = this.getLineColor(i);
            graphics.text(CoordinatesModule.mc.font, lines[i], x, currentY, color, true);
            currentY += lineHeight;
        }
        graphics.pose().popMatrix();
    }

    private String[] buildDisplayLines(double playerX, double playerY, double playerZ) {
        ArrayList<String> lines = new ArrayList<String>(4);
        if (this.displayMode.is("minimal")) {
            lines.add((int)playerX + ", " + (int)playerY + ", " + (int)playerZ);
        } else if (this.displayMode.is("compact")) {
            lines.add(String.format("XYZ: %.1f / %.1f / %.1f", playerX, playerY, playerZ));
            if (((Boolean)this.showDirection.getValue()).booleanValue()) {
                lines.add("Facing: " + this.getCardinalDirection());
            }
            if (((Boolean)this.showDimension.getValue()).booleanValue()) {
                lines.add("Dim: " + this.getDimensionName());
            }
        } else {
            lines.add(String.format("X: %.2f", playerX));
            lines.add(String.format("Y: %.2f", playerY));
            lines.add(String.format("Z: %.2f", playerZ));
            if (((Boolean)this.showDirection.getValue()).booleanValue()) {
                lines.add("Facing: " + this.getCardinalDirection());
            }
            if (((Boolean)this.showDimension.getValue()).booleanValue()) {
                lines.add("Dimension: " + this.getDimensionName());
            }
            if (((Boolean)this.showBiome.getValue()).booleanValue()) {
                lines.add("Biome: " + this.getBiomeName());
            }
            BlockPos blockPos = CoordinatesModule.mc.player.blockPosition();
            lines.add("Block: " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ());
        }
        return lines.toArray(new String[0]);
    }

    private int getLineColor(int lineIndex) {
        if (this.colorMode.is("axis")) {
            if (this.displayMode.is("detailed") && lineIndex < 3) {
                return switch (lineIndex) {
                    case 0 -> -65536;
                    case 1 -> -16711936;
                    default -> -16776961;
                };
            }
            return -1;
        }
        return this.textColor.getRGB();
    }

    private String getCardinalDirection() {
        float yaw = CoordinatesModule.mc.player.getYRot();
        int degrees = (int)this.wrapDegrees(yaw);
        if (degrees >= -22 && degrees < 23) {
            return "S (+Z)";
        }
        if (degrees >= 23 && degrees < 68) {
            return "SW (-X/+Z)";
        }
        if (degrees >= 68 && degrees < 113) {
            return "W (-X)";
        }
        if (degrees >= 113 && degrees < 158) {
            return "NW (-X/-Z)";
        }
        if (degrees >= 158 || degrees < -157) {
            return "N (-Z)";
        }
        if (degrees >= -157 && degrees < -112) {
            return "NE (+X/-Z)";
        }
        if (degrees >= -112 && degrees < -67) {
            return "E (+X)";
        }
        return "SE (+X/+Z)";
    }

    private String getDimensionName() {
        if (CoordinatesModule.mc.level == null) {
            return "Unknown";
        }
        String dim = CoordinatesModule.mc.level.dimension().identifier().getPath();
        return switch (dim) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> dim;
        };
    }

    private String getBiomeName() {
        if (CoordinatesModule.mc.level == null || CoordinatesModule.mc.player == null) {
            return "Unknown";
        }
        BlockPos pos = CoordinatesModule.mc.player.blockPosition();
        Holder<net.minecraft.world.level.biome.Biome> biome = CoordinatesModule.mc.level.getBiome(pos);
        if (biome.unwrapKey().isPresent()) {
            String path = biome.unwrapKey().get().identifier().getPath();
            String[] words = path.split("_");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return result.toString();
        }
        return "Unknown";
    }

    private float wrapDegrees(float degrees) {
        float result = degrees % 360.0f;
        if (result >= 180.0f) {
            result -= 360.0f;
        }
        if (result < -180.0f) {
            result += 360.0f;
        }
        return result;
    }

    @Override
    public int getHudWidth() {
        if (CoordinatesModule.mc.player == null) {
            return 120;
        }
        double playerX = CoordinatesModule.mc.player.getX();
        double playerY = CoordinatesModule.mc.player.getY();
        double playerZ = CoordinatesModule.mc.player.getZ();
        String[] lines = this.buildDisplayLines(playerX, playerY, playerZ);
        int maxWidth = 0;
        for (String line : lines) {
            int lineWidth = CoordinatesModule.mc.font.width(line);
            if (lineWidth <= maxWidth) continue;
            maxWidth = lineWidth;
        }
        return maxWidth + 10;
    }

    @Override
    public int getHudHeight() {
        if (CoordinatesModule.mc.player == null) {
            return 33;
        }
        double playerX = CoordinatesModule.mc.player.getX();
        double playerY = CoordinatesModule.mc.player.getY();
        double playerZ = CoordinatesModule.mc.player.getZ();
        String[] lines = this.buildDisplayLines(playerX, playerY, playerZ);
        return lines.length * 11 + 6;
    }
}

