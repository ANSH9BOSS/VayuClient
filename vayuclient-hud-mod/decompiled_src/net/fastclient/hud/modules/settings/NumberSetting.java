/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import net.fastclient.hud.modules.settings.Setting;

public class NumberSetting
extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public double getStep() {
        return this.step;
    }

    public int getIntValue() {
        return ((Double)this.getValue()).intValue();
    }

    public float getFloatValue() {
        return ((Double)this.getValue()).floatValue();
    }

    @Override
    public void setValue(Double value) {
        super.setValue(Math.max(this.min, Math.min(this.max, value)));
    }
}

