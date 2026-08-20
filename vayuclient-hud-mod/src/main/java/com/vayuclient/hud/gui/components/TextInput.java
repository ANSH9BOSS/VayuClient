/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 */
package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class TextInput
extends UIComponent {
    private final String label;
    private String value;
    private final Consumer<String> onChange;
    private boolean focused = false;
    private int cursorPosition;
    private int selectionEnd;
    private float hoverProgress;
    private float focusProgress;
    private long lastUpdate = System.currentTimeMillis();

    public TextInput(int x, int y, int width, int height, String label, String initialValue, Consumer<String> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.value = initialValue != null ? initialValue : "";
        this.onChange = onChange;
        this.cursorPosition = this.value.length();
        this.selectionEnd = this.cursorPosition;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.focusProgress = AnimationUtils.smoothDelta(this.focusProgress, this.focused ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();
        String displayLabel = VayuTheme.formatSettingName(this.label);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), -1);
        int inputWidth = Math.max(150, Math.min(260, this.width / 2));
        int inputX = this.x + this.width - inputWidth - 12;
        int inputY = this.y + 2;
        int inputHeight = this.height - 4;
        VayuHUDUI.roundedRect(graphics, inputX, inputY, inputWidth, inputHeight, 4, this.focused ? -266722777 : -435153640);
        VayuHUDUI.outline(graphics, inputX, inputY, inputWidth, inputHeight, this.focused ? -16723201 : 1143616571);
        Object visibleValue = this.fitFromEnd(mc, this.value, inputWidth - 18);
        if (this.focused && System.currentTimeMillis() / 500L % 2L == 0L) {
            visibleValue = (String)visibleValue + "|";
        }
        this.drawUiText(graphics, mc, (String)visibleValue, inputX + 8, this.centeredTextY(mc, inputY, inputHeight), this.value.isEmpty() && !this.focused ? -9934744 : -723724);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        this.focused = event.button() == 0 && this.isHovered(event.x(), event.y());
        return this.focused;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.focused) {
            return false;
        }
        if (event.key() == 256 || event.key() == 257 || event.key() == 335) {
            this.focused = false;
            return true;
        }
        if (event.key() == 259) {
            if (!this.value.isEmpty()) {
                this.value = this.value.substring(0, this.value.length() - 1);
                this.notifyChange();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.focused) {
            return false;
        }
        int codepoint = event.codepoint();
        if (codepoint >= 32 && !Character.isISOControl(codepoint) && this.value.length() < 160) {
            this.value = this.value + Character.toString(codepoint);
            this.notifyChange();
        }
        return true;
    }

    private String fitFromEnd(Minecraft mc, String text, int maxWidth) {
        String result = text;
        while (!result.isEmpty() && this.uiTextWidth(mc, result) > maxWidth) {
            result = result.substring(1);
        }
        return result;
    }

    private void notifyChange() {
        if (this.onChange != null) {
            this.onChange.accept(this.value);
        }
    }

    public boolean isFocused() {
        return this.focused;
    }
}

