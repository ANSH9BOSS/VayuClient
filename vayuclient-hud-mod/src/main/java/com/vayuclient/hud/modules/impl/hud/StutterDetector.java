package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class StutterDetector extends Module {
    private final ModeSetting displayMode = this.register(new ModeSetting("display_mode", "Telemetry style", "detailed", new String[]{"compact", "detailed"}));
    private final BooleanSetting flashOnSpike = this.register(new BooleanSetting("flash_on_spike", "Flash red dot on severe lag spike", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Glass background panel", true));
    private final ColorSetting textColor = this.register(new ColorSetting("text_color", "Text color", 255, 255, 255));

    private long lastFrameNanos = System.nanoTime();
    private float maxFrameTimeMs = 0.0f;
    private int spikesCount = 0;
    private long spikeResetTimer = System.currentTimeMillis();
    private boolean isSpikeActive = false;
    private long lastSpikeTime = 0L;

    public StutterDetector() {
        super("StutterDetector", "Tracks frametime consistency and micro-stutter spikes", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) return;

        long now = System.nanoTime();
        float frameTimeMs = (now - this.lastFrameNanos) / 1_000_000.0f;
        this.lastFrameNanos = now;

        if (frameTimeMs > 25.0f) { // Stutter detected (>25ms = <40 FPS frame spike)
            this.spikesCount++;
            this.lastSpikeTime = System.currentTimeMillis();
            this.isSpikeActive = true;
        }

        if (frameTimeMs > this.maxFrameTimeMs) {
            this.maxFrameTimeMs = frameTimeMs;
        }

        long currentMs = System.currentTimeMillis();
        if (currentMs - this.spikeResetTimer > 60000L) { // Reset max stats every minute
            this.spikeResetTimer = currentMs;
            this.maxFrameTimeMs = frameTimeMs;
            this.spikesCount = 0;
        }

        if (currentMs - this.lastSpikeTime > 800L) {
            this.isSpikeActive = false;
        }

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        String label;
        if (this.displayMode.is("compact")) {
            label = String.format("Stutter: %d/min (Max: %.1fms)", this.spikesCount, this.maxFrameTimeMs);
        } else {
            label = String.format("Frametime: %.1fms | Spikes: %d/min", frameTimeMs, this.spikesCount);
        }

        int textW = mc.font.width(label) + (this.flashOnSpike.isEnabled() && this.isSpikeActive ? 12 : 0);
        if (this.background.isEnabled()) {
            VayuHUDUI.hudPanelStrong(graphics, x - 4, y - 3, textW + 8, 14);
        }

        int color = this.spikesCount == 0 ? 0xFF00FF88 : (this.spikesCount < 3 ? 0xFFFFCC00 : 0xFFFF4444);
        graphics.text(mc.font, label, x, y, color, true);

        if (this.flashOnSpike.isEnabled() && this.isSpikeActive) {
            int dotX = x + mc.font.width(label) + 4;
            graphics.fill(dotX, y + 2, dotX + 5, y + 7, 0xFFFF0000);
        }

        graphics.pose().popMatrix();
    }
}
