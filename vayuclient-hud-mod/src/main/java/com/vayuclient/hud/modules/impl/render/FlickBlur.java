package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.NumberSetting;

public class FlickBlur extends Module {
    private final NumberSetting flickThreshold = this.register(new NumberSetting("flick_threshold", "Angular speed threshold (deg/sec)", 250.0, 100.0, 600.0, 25.0));
    private final NumberSetting blurIntensity = this.register(new NumberSetting("blur_intensity", "Blur accumulation strength", 5.0, 1.0, 10.0, 1.0));

    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private long lastTime = System.currentTimeMillis();
    private float currentAngularVelocity = 0.0f;

    public FlickBlur() {
        super("FlickBlur", "Cinematic motion blur strictly during rapid camera flicks", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (!this.isEnabled() || mc.player == null) return;

        long now = System.currentTimeMillis();
        float deltaSec = Math.max(0.001f, (now - this.lastTime) / 1000.0f);
        this.lastTime = now;

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        float dYaw = Math.abs(yaw - this.lastYaw);
        float dPitch = Math.abs(pitch - this.lastPitch);

        this.lastYaw = yaw;
        this.lastPitch = pitch;

        float speed = (float)Math.sqrt(dYaw * dYaw + dPitch * dPitch) / deltaSec;
        this.currentAngularVelocity = this.currentAngularVelocity * 0.7f + speed * 0.3f;
    }

    public boolean isFlicking() {
        return this.isEnabled() && this.currentAngularVelocity > this.flickThreshold.getValue().doubleValue();
    }

    public float getBlurStrength() {
        if (!isFlicking()) return 0.0f;
        float factor = (float)((this.currentAngularVelocity - this.flickThreshold.getValue().doubleValue()) / 200.0f);
        return Math.min(1.0f, factor) * (this.blurIntensity.getValue().floatValue() / 10.0f);
    }
}
