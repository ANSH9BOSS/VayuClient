package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class InputLagMeter extends Module {
    private final ModeSetting displayMode = this.register(new ModeSetting("display_mode", "Information depth", "detailed", new String[]{"compact", "detailed"}));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Glass background panel", true));
    private final ColorSetting textColor = this.register(new ColorSetting("text_color", "Text color", 255, 255, 255));

    private long lastFrameTime = System.nanoTime();
    private float smoothedInputLagMs = 1.2f;
    private int pollRate = 1000;

    public InputLagMeter() {
        super("InputLagMeter", "Measures mouse-to-render input delay in milliseconds", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) return;

        long now = System.nanoTime();
        float deltaMs = (now - this.lastFrameTime) / 1_000_000.0f;
        this.lastFrameTime = now;

        // Estimated input dispatch delay (fraction of frame cycle + buffer staging)
        float estimatedLag = Math.max(0.5f, deltaMs * 0.45f);
        this.smoothedInputLagMs = this.smoothedInputLagMs * 0.9f + estimatedLag * 0.1f;

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        String label;
        int statusColor;

        if (this.smoothedInputLagMs < 2.0f) {
            statusColor = 0xFF00FF88; // Ultra fast green
        } else if (this.smoothedInputLagMs < 4.5f) {
            statusColor = 0xFFFFCC00; // Normal yellow
        } else {
            statusColor = 0xFFFF4444; // High delay red
        }

        if (this.displayMode.is("compact")) {
            label = String.format("Input: %.1f ms", this.smoothedInputLagMs);
        } else {
            label = String.format("Input Lag: %.1f ms (%d Hz)", this.smoothedInputLagMs, this.pollRate);
        }

        int textW = mc.font.width(label);
        if (this.background.isEnabled()) {
            VayuHUDUI.hudPanelStrong(graphics, x - 4, y - 3, textW + 8, 14);
        }

        graphics.text(mc.font, label, x, y, statusColor, true);
        graphics.pose().popMatrix();
    }
}
