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
import com.vayuclient.hud.modules.settings.NumberSetting;
import com.vayuclient.hud.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DirectionHUD
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));

    public DirectionHUD() {
        super("DirectionHUD", "Shows facing direction and yaw", Category.HUD);
        this.bgOpacity.visibleWhen(this.background::isEnabled);
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
        String direction = PlayerUtils.getFacingDirection();
        float yaw = DirectionHUD.mc.player.getYRot();
        String yawText = String.format("%.1f\u00b0", Float.valueOf((yaw % 360.0f + 360.0f) % 360.0f));
        if (this.background.isEnabled()) {
            VayuHUDUI.hudTwoLine(graphics, DirectionHUD.mc.font, direction, yawText, x, y, -1, -3025448, this.bgOpacity.getIntValue());
        } else {
            graphics.text(DirectionHUD.mc.font, direction, x, y, -1, true);
            graphics.text(DirectionHUD.mc.font, yawText, x, y + 10, -3025448, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        if (DirectionHUD.mc.player == null) {
            return 60;
        }
        String direction = PlayerUtils.getFacingDirection();
        float yaw = DirectionHUD.mc.player.getYRot();
        String yawText = String.format("%.1f\u00b0", Float.valueOf((yaw % 360.0f + 360.0f) % 360.0f));
        return Math.max(DirectionHUD.mc.font.width(direction), DirectionHUD.mc.font.width(yawText)) + 10;
    }

    @Override
    public int getHudHeight() {
        return 28;
    }
}

