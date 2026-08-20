package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.Minecraft;

public class PerformanceModule extends Module {
    private static PerformanceModule instance;

    private final BooleanSetting smartGc = this.register(new BooleanSetting("smart_gc", "Smart RAM Optimizer (Clean during pause/menus)", true));
    private final NumberSetting gcIntervalSec = this.register(new NumberSetting("gc_interval", "RAM Cleanup Interval (Seconds)", 120.0, 30.0, 600.0, 30.0));
    private final BooleanSetting bgThrottling = this.register(new BooleanSetting("bg_throttling", "Throttle FPS when Minimized", false));
    private final NumberSetting bgFpsLimit = this.register(new NumberSetting("bg_fps", "Background FPS Target", 15.0, 5.0, 60.0, 5.0));
    private final BooleanSetting fastHud = this.register(new BooleanSetting("fast_hud", "Fast HUD Zero-Allocation Path", true));

    private long lastGcTime = System.currentTimeMillis();

    public PerformanceModule() {
        super("Performance Optimizer", "Ultimate FPS and memory optimization engine", Category.RENDER);
        instance = this;
        this.setEnabled(true);
    }

    public static PerformanceModule getInstance() {
        return instance;
    }

    public static boolean isBackgroundThrottlingActive() {
        if (instance != null && instance.isEnabled() && instance.bgThrottling.isEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && !client.isWindowActive()) {
                return true;
            }
        }
        return false;
    }

    public static int getBackgroundFpsTarget() {
        if (instance != null) {
            return instance.bgFpsLimit.getIntValue();
        }
        return 15;
    }

    public static boolean isFastHudEnabled() {
        return instance == null || (instance.isEnabled() && instance.fastHud.isEnabled());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = (long)(this.gcIntervalSec.getValue() * 1000.0);

        if (this.smartGc.isEnabled() && (now - this.lastGcTime > intervalMs)) {
            Minecraft client = Minecraft.getInstance();
            // Only perform GC cleanup when in a screen or paused, never during active gameplay
            if (client != null && (client.isPaused() || (client.gui != null && client.gui.screen() != null))) {
                triggerMemoryClean();
                this.lastGcTime = now;
            }
        }
    }

    public static void triggerMemoryClean() {
        Thread gcThread = new Thread(() -> {
            try {
                System.gc();
            } catch (Throwable ignored) {
            }
        }, "VayuClient-RAMOptimizer");
        gcThread.setPriority(Thread.MIN_PRIORITY);
        gcThread.setDaemon(true);
        gcThread.start();
    }
}
