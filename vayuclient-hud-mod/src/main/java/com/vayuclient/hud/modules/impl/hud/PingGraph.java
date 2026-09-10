package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingGraph extends Module {
    private final NumberSetting historySamples = this.register(new NumberSetting("history_samples", "Number of ping points", 30.0, 15.0, 60.0, 5.0));
    private final BooleanSetting showStats = this.register(new BooleanSetting("show_stats", "Show avg/max/jitter text", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Glass background panel", true));
    private final ColorSetting graphColor = this.register(new ColorSetting("graph_color", "Graph line color", 0, 210, 255));

    private final int[] pingHistory = new int[60];
    private int historyIndex = 0;
    private long lastSampleTime = 0L;
    private int currentPing = 0;

    public PingGraph() {
        super("PingGraph", "Live oscilloscope of server ping and network jitter", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastSampleTime > 1000L) { // Sample every second
            this.lastSampleTime = now;
            this.updatePing();
            this.pingHistory[this.historyIndex % this.pingHistory.length] = this.currentPing;
            this.historyIndex++;
        }

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        int samples = this.historySamples.getValue().intValue();
        int width = samples * 3 + 10;
        int height = 32;

        if (this.background.isEnabled()) {
            VayuHUDUI.hudPanelStrong(graphics, x - 4, y - 3, width + 8, height + (this.showStats.isEnabled() ? 14 : 6));
        }

        // Draw graph background grid line
        graphics.fill(x, y + height / 2, x + width, y + height / 2 + 1, 0x33FFFFFF);
        graphics.fill(x, y + height, x + width, y + height + 1, 0x55FFFFFF);

        // Find min/max
        int maxPing = 100;
        int minPing = 999;
        long total = 0;
        int count = 0;

        for (int i = 0; i < samples; i++) {
            int idx = (this.historyIndex - samples + i) % this.pingHistory.length;
            if (idx < 0) idx += this.pingHistory.length;
            int val = this.pingHistory[idx];
            if (val > 0) {
                if (val > maxPing) maxPing = val;
                if (val < minPing) minPing = val;
                total += val;
                count++;
            }
        }
        if (minPing == 999) minPing = 0;
        int avgPing = count > 0 ? (int)(total / count) : this.currentPing;

        // Draw line segments
        int color = this.graphColor.getRGB() | 0xFF000000;
        for (int i = 0; i < samples - 1; i++) {
            int idx1 = (this.historyIndex - samples + i) % this.pingHistory.length;
            if (idx1 < 0) idx1 += this.pingHistory.length;
            int idx2 = (this.historyIndex - samples + i + 1) % this.pingHistory.length;
            if (idx2 < 0) idx2 += this.pingHistory.length;

            int p1 = this.pingHistory[idx1];
            int p2 = this.pingHistory[idx2];

            int y1 = y + height - (int)((float)p1 / (float)maxPing * (height - 4));
            int y2 = y + height - (int)((float)p2 / (float)maxPing * (height - 4));

            int x1 = x + i * 3;
            int x2 = x + (i + 1) * 3;

            graphics.fill(x1, Math.min(y1, y2), x2, Math.max(y1, y2) + 1, color);
        }

        if (this.showStats.isEnabled()) {
            String stats = String.format("%d ms (avg %d | max %d)", this.currentPing, avgPing, maxPing);
            graphics.text(mc.font, stats, x, y + height + 3, 0xFFE0E0E0, true);
        }

        graphics.pose().popMatrix();
    }

    private void updatePing() {
        if (mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                this.currentPing = info.getLatency();
            }
        }
    }
}
