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
import com.vayuclient.hud.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SpeedHUD
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));

    public SpeedHUD() {
        super("SpeedHUD", "Shows player movement speed", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        double speed = PlayerUtils.getSpeed();
        String text = String.format("Speed: %.2f m/s", speed);
        if (this.background.isEnabled()) {
            VayuHUDUI.hudText(graphics, SpeedHUD.mc.font, text, x, y, -1, true);
        } else {
            graphics.text(SpeedHUD.mc.font, text, x, y, -1, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        double speed = PlayerUtils.getSpeed();
        String text = String.format("Speed: %.2f m/s", speed);
        return SpeedHUD.mc.font.width(text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

