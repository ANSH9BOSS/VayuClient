/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SaturationHUD
extends Module {
    private final BooleanSetting showBar = this.register(new BooleanSetting("show_bar", "Show progress bar", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
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
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, 90, height + 6);
        }
        String satText = String.format("Sat: %.1f/%d", Float.valueOf(saturation), food);
        graphics.text(SaturationHUD.mc.font, satText, x, lineY, this.saturationColor.getRGB() | 0xFF000000, true);
        if (this.showBar.isEnabled()) {
            this.drawProgressBar(graphics, x, lineY += 10, 80, 4, saturation / 20.0f, this.saturationColor.getRGB() | 0xFF000000);
        }
        graphics.pose().popMatrix();
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, int color) {
        VayuHUDUI.roundedRect(graphics, x, y, width, height, 2, -1441128412);
        int filledWidth = (int)((float)width * Math.max(0.0f, Math.min(1.0f, progress)));
        if (filledWidth > 0) {
            VayuHUDUI.roundedRect(graphics, x, y, filledWidth, height, 2, color);
        }
        VayuHUDUI.outline(graphics, x, y, width, height, 1154997472);
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

