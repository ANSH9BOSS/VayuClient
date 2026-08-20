/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package net.fastclient.hud.modules.impl.hud;

import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SaturationHUD
extends Module {
    private final BooleanSetting showBar = this.register(new BooleanSetting("show_bar", "Show progress bar", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));
    private final ColorSetting saturationColor = this.register(new ColorSetting("saturation_color", "Saturation color", 255, 200, 50));

    public SaturationHUD() {
        super("Saturation", "Shows hidden hunger saturation", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || SaturationHUD.mc.player == null) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        float saturation = SaturationHUD.mc.player.getFoodData().getSaturationLevel();
        int food = SaturationHUD.mc.player.getFoodData().getFoodLevel();
        int lineY = y;
        if (this.background.isEnabled()) {
            int height = 12;
            if (this.showBar.isEnabled()) {
                height += 6;
            }
            FastClientUI.hudPanel(graphics, x - 5, y - 4, 90, height + 6);
        }
        String satText = String.format("Sat: %.1f/%d", Float.valueOf(saturation), food);
        graphics.text(SaturationHUD.mc.font, satText, x, lineY, this.saturationColor.getRGB() | 0xFF000000, true);
        if (this.showBar.isEnabled()) {
            this.drawProgressBar(graphics, x, lineY += 10, 80, 4, saturation / 20.0f, this.saturationColor.getRGB() | 0xFF000000);
        }
        graphics.pose().popMatrix();
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, int color) {
        FastClientUI.roundedRect(graphics, x, y, width, height, 2, -1441128412);
        int filledWidth = (int)((float)width * Math.max(0.0f, Math.min(1.0f, progress)));
        if (filledWidth > 0) {
            FastClientUI.roundedRect(graphics, x, y, filledWidth, height, 2, color);
        }
        FastClientUI.outline(graphics, x, y, width, height, 1154997472);
    }

    @Override
    public int getHudWidth() {
        return 90;
    }

    @Override
    public int getHudHeight() {
        return this.showBar.isEnabled() ? 24 : 18;
    }
}

