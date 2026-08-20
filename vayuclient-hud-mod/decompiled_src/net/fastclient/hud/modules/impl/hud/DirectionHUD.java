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
import net.fastclient.hud.modules.settings.NumberSetting;
import net.fastclient.hud.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DirectionHUD
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 80.0, 0.0, 255.0, 5.0));

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
            FastClientUI.hudTwoLine(graphics, DirectionHUD.mc.font, direction, yawText, x, y, -1, -3025448, this.bgOpacity.getIntValue());
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

