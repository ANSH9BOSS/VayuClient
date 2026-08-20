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
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ServerInfoHUD
extends Module {
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));

    public ServerInfoHUD() {
        super("ServerInfo", "Shows server information", Category.HUD);
        this.bgOpacity.visibleWhen(this.background::isEnabled);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        String serverAddress;
        String serverName;
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
        int offsetY = 0;
        if (mc.hasSingleplayerServer()) {
            serverName = "Singleplayer";
            serverAddress = "Local";
        } else if (mc.getCurrentServer() != null) {
            serverName = ServerInfoHUD.mc.getCurrentServer().name;
            serverAddress = ServerInfoHUD.mc.getCurrentServer().ip;
        } else {
            return;
        }
        if (this.background.isEnabled()) {
            VayuHUDUI.hudTwoLine(graphics, ServerInfoHUD.mc.font, serverName, serverAddress, x, y, -1, -3025448, this.bgOpacity.getIntValue());
        } else {
            graphics.text(ServerInfoHUD.mc.font, serverName, x, y + offsetY, -1, true);
            graphics.text(ServerInfoHUD.mc.font, serverAddress, x, y + (offsetY += 10), -3025448, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        String serverAddress;
        String serverName;
        if (mc.hasSingleplayerServer()) {
            serverName = "Singleplayer";
            serverAddress = "Local";
        } else if (mc.getCurrentServer() != null) {
            serverName = ServerInfoHUD.mc.getCurrentServer().name;
            serverAddress = ServerInfoHUD.mc.getCurrentServer().ip;
        } else {
            return 80;
        }
        return Math.max(ServerInfoHUD.mc.font.width(serverName), ServerInfoHUD.mc.font.width(serverAddress)) + 10;
    }

    @Override
    public int getHudHeight() {
        return 28;
    }
}

