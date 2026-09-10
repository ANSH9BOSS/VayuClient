package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;

public class DynamicFPSBoost extends Module {
    private final BooleanSetting unfocusThrottle = this.register(new BooleanSetting("unfocus_throttle", "Throttle to 15 FPS when alt-tabbed", true));
    private final NumberSetting backgroundFPS = this.register(new NumberSetting("background_fps", "Background target FPS", 15.0, 5.0, 60.0, 5.0));
    private final BooleanSetting smartGC = this.register(new BooleanSetting("smart_gc", "Perform smart memory cleanup on menus", true));
    private final BooleanSetting explosionParticleCull = this.register(new BooleanSetting("explosion_cull", "Cull excess explosion smoke particles", true));

    private long lastGCTime = System.currentTimeMillis();

    public DynamicFPSBoost() {
        super("DynamicFPSBoost", "Smart background throttle and combat particle optimizer", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) return;

        if (this.smartGC.isEnabled()) {
            long now = System.currentTimeMillis();
            if (now - this.lastGCTime > 120000L && mc.isPaused()) { // Every 2 mins when paused
                this.lastGCTime = now;
                try {
                    System.gc();
                } catch (Throwable ignored) {}
            }
        }
    }

    public boolean shouldThrottleBackground() {
        return this.isEnabled() && this.unfocusThrottle.isEnabled();
    }

    public int getBackgroundTargetFPS() {
        return this.backgroundFPS.getValue().intValue();
    }

    public boolean shouldCullExplosionParticles() {
        return this.isEnabled() && this.explosionParticleCull.isEnabled();
    }
}
