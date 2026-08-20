/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.multiplayer.PlayerInfo
 */
package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingDisplay
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));

    public PingDisplay() {
        super("PingDisplay", "Shows network latency", Category.HUD);
        this.bgOpacity.visibleWhen(this.background::isEnabled);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        int color;
        PlayerInfo entry;
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
        int ping = 0;
        if (mc.getConnection() != null && (entry = mc.getConnection().getPlayerInfo(PingDisplay.mc.player.getUUID())) != null) {
            ping = entry.getLatency();
        }
        String text = "Ping: " + ping + "ms";
        color = ping < 50 ? -11141291 : (ping < 100 ? -171 : -43691);
        if (this.background.isEnabled()) {
            VayuHUDUI.hudText(graphics, PingDisplay.mc.font, text, x, y, color, true, this.bgOpacity.getIntValue());
        } else {
            graphics.text(PingDisplay.mc.font, text, x, y, color, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        PlayerInfo entry;
        int ping = 0;
        if (mc.getConnection() != null && PingDisplay.mc.player != null && (entry = mc.getConnection().getPlayerInfo(PingDisplay.mc.player.getUUID())) != null) {
            ping = entry.getLatency();
        }
        String text = "Ping: " + ping + "ms";
        return PingDisplay.mc.font.width(text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

