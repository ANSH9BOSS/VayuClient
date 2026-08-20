/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected T value;
    private BooleanSupplier visibility = () -> true;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public boolean isVisible() {
        return this.visibility.getAsBoolean();
    }

    public void visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
    }
}

