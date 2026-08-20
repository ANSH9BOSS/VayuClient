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
import net.fastclient.hud.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SpeedHUD
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));

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
            FastClientUI.hudText(graphics, SpeedHUD.mc.font, text, x, y, -1, true);
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

