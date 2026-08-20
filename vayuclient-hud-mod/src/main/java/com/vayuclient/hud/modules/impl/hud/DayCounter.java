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

public class DayCounter
extends Module {
    private final BooleanSetting showLabel = this.register(new BooleanSetting("show_label", "Show 'Day' label", true));
    private final BooleanSetting showTime = this.register(new BooleanSetting("show_time", "Show in-game time", false));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final ColorSetting color = this.register(new ColorSetting("color", "Text color", 255, 220, 100));

    public DayCounter() {
        super("DayCounter", "Shows in-game day count", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || DayCounter.mc.level == null) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        long worldTime = DayCounter.mc.level.getOverworldClockTime();
        long day = worldTime / 24000L + 1L;
        StringBuilder text = new StringBuilder();
        if (this.showLabel.isEnabled()) {
            text.append("Day ");
        }
        text.append(day);
        if (this.showTime.isEnabled()) {
            int hours = (int)(worldTime % 24000L / 1000L + 6L) % 24;
            int minutes = (int)(worldTime % 1000L * 60L / 1000L);
            text.append(String.format(" (%02d:%02d)", hours, minutes));
        }
        String displayText = text.toString();
        if (this.background.isEnabled()) {
            VayuHUDUI.hudText(graphics, DayCounter.mc.font, displayText, x, y, this.color.getRGB() | 0xFF000000, true);
        } else {
            graphics.text(DayCounter.mc.font, displayText, x, y, this.color.getRGB() | 0xFF000000, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        if (DayCounter.mc.level == null) {
            return 60;
        }
        long worldTime = DayCounter.mc.level.getOverworldClockTime();
        long day = worldTime / 24000L + 1L;
        StringBuilder text = new StringBuilder();
        if (this.showLabel.isEnabled()) {
            text.append("Day ");
        }
        text.append(day);
        if (this.showTime.isEnabled()) {
            int hours = (int)(worldTime % 24000L / 1000L + 6L) % 24;
            int minutes = (int)(worldTime % 1000L * 60L / 1000L);
            text.append(String.format(" (%02d:%02d)", hours, minutes));
        }
        return DayCounter.mc.font.width(text.toString()) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

