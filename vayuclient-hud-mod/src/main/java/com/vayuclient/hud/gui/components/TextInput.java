package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class TextInput extends UIComponent {
    private final String label;
    private String value;
    private final Consumer<String> onChange;
    private boolean focused = false;
    private float hoverProgress;
    private float focusProgress;
    private long lastUpdate = System.currentTimeMillis();

    public TextInput(int x, int y, int width, int height, String label, String initialValue, Consumer<String> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.value = initialValue != null ? initialValue : "";
        this.onChange = onChange;
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

        // 1. Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);

        // 2. Text Box
        int inputWidth = Math.max(150, Math.min(260, this.width / 2));
        int inputX = this.x + this.width - inputWidth - 12;
        int inputY = this.y + 2;
        int inputHeight = this.height - 4;

        int bg = this.focused ? 0xE6142338 : (this.hovered ? 0xD0111D2E : 0xC00B1320);
        int border = this.focused ? 0xFF38BDF8 : (this.hovered ? 0x8838BDF8 : 0x3338BDF8);

        VayuHUDUI.roundedRect(graphics, inputX, inputY, inputWidth, inputHeight, 6, bg);
        VayuHUDUI.roundedOutline(graphics, inputX, inputY, inputWidth, inputHeight, 6, border);

        String visibleValue = this.value;
        if (this.focused && System.currentTimeMillis() / 450L % 2L == 0L) {
            visibleValue = visibleValue + "§b|§r";
        }

        int textColor = this.value.isEmpty() && !this.focused ? 0xFF64748B : 0xFFFFFFFF;
        String renderText = this.value.isEmpty() && !this.focused ? "Enter text..." : visibleValue;
        this.drawUiText(graphics, mc, renderText, inputX + 8, this.centeredTextY(mc, inputY, inputHeight), textColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        this.focused = event.button() == 0 && this.isHovered(event.x(), event.y());
        return this.focused;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.focused) return false;
        if (event.key() == 256 || event.key() == 257 || event.key() == 335) { // ESC or ENTER
            this.focused = false;
            return true;
        }
        if (event.key() == 259) { // Backspace
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
        if (!this.focused) return false;
        int codepoint = event.codepoint();
        if (codepoint >= 32 && !Character.isISOControl(codepoint) && this.value.length() < 120) {
            this.value = this.value + new String(Character.toChars(codepoint));
            this.notifyChange();
            return true;
        }
        return false;
    }

    private void notifyChange() {
        if (this.onChange != null) {
            this.onChange.accept(this.value);
        }
    }
}
