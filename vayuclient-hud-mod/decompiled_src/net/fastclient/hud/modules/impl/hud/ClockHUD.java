/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package net.fastclient.hud.modules.impl.hud;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ClockHUD
extends Module {
    private final ModeSetting format = this.register(new ModeSetting("format", "Time format", "24h", new String[]{"24h", "12h", "24h_seconds", "12h_seconds"}));
    private final BooleanSetting showDate = this.register(new BooleanSetting("show_date", "Show date", false));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));
    private final ColorSetting color = this.register(new ColorSetting("color", "Text color", 255, 255, 255));
    private final SimpleDateFormat format24h = new SimpleDateFormat("HH:mm");
    private final SimpleDateFormat format12h = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat format24hSec = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat format12hSec = new SimpleDateFormat("hh:mm:ss a");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

    public ClockHUD() {
        super("Clock", "Shows the current real-world time", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        Date now = new Date();
        Object time = this.getFormattedTime(now);
        if (this.showDate.isEnabled()) {
            time = this.dateFormat.format(now) + " " + (String)time;
        }
        if (this.background.isEnabled()) {
            FastClientUI.hudText(graphics, ClockHUD.mc.font, (String)time, x, y, this.color.getRGB() | 0xFF000000, true);
        } else {
            graphics.text(ClockHUD.mc.font, (String)time, x, y, this.color.getRGB() | 0xFF000000, true);
        }
        graphics.pose().popMatrix();
    }

    private String getFormattedTime(Date date) {
        return switch ((String)this.format.getValue()) {
            case "12h" -> this.format12h.format(date);
            case "24h_seconds" -> this.format24hSec.format(date);
            case "12h_seconds" -> this.format12hSec.format(date);
            default -> this.format24h.format(date);
        };
    }

    @Override
    public int getHudWidth() {
        Date now = new Date();
        Object time = this.getFormattedTime(now);
        if (this.showDate.isEnabled()) {
            time = this.dateFormat.format(now) + " " + (String)time;
        }
        return ClockHUD.mc.font.width((String)time) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

