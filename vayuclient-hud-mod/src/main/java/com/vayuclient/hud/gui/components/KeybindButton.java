package com.vayuclient.hud.gui.components;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class KeybindButton extends UIComponent {
    private final String label;
    private int keyCode;
    private int modifiers;
    private final BiConsumer<Integer, Integer> onChangeWithMods;
    private boolean listening = false;
    private float hoverProgress;
    private float pulseProgress;
    private long lastUpdate = System.currentTimeMillis();

    public KeybindButton(int x, int y, int width, int height, String label, int keyCode, Consumer<Integer> onChange) {
        this(x, y, width, height, label, keyCode, 0, (k, m) -> onChange.accept(k));
    }

    public KeybindButton(int x, int y, int width, int height, String label, int keyCode, int modifiers, BiConsumer<Integer, Integer> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.onChangeWithMods = onChange;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.pulseProgress = this.listening ? (float)(Math.sin((double)now / 180.0) * 0.5 + 0.5) : AnimationUtils.smoothDelta(this.pulseProgress, 0.0f, 0.5f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();

        // 1. Setting Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);

        // 2. Keybind Button
        int btnWidth = 110;
        int btnX = this.x + this.width - btnWidth - 12;
        int btnY = this.y + 2;
        int btnHeight = this.height - 4;

        int bgColor = this.listening 
            ? VayuHUDUI.blend(0xFF0284C7, 0xFF38BDF8, this.pulseProgress) 
            : (this.hovered ? 0xD0132034 : 0xC00B1320);
        int borderColor = this.listening 
            ? 0xFF38BDF8 
            : (this.hovered ? 0x8838BDF8 : 0x3338BDF8);

        VayuHUDUI.roundedRect(graphics, btnX, btnY, btnWidth, btnHeight, 6, bgColor);
        VayuHUDUI.roundedOutline(graphics, btnX, btnY, btnWidth, btnHeight, 6, borderColor);

        String keyText;
        int textColor;
        if (this.listening) {
            keyText = "PRESS KEY...";
            textColor = 0xFFFFFFFF;
        } else if (this.keyCode == 0) {
            keyText = "[ NONE ]";
            textColor = 0xFF64748B;
        } else {
            keyText = "[ " + this.getKeyName().toUpperCase() + " ]";
            textColor = 0xFF38BDF8;
        }

        int textX = btnX + (btnWidth - this.uiTextWidth(mc, keyText)) / 2;
        this.drawUiText(graphics, mc, keyText, textX, this.centeredTextY(mc, btnY, btnHeight), textColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            if (event.button() == 0) {
                this.listening = true;
                return true;
            }
            if (event.button() == 1) { // Right-click clears keybind
                this.keyCode = 0;
                this.modifiers = 0;
                this.listening = false;
                if (this.onChangeWithMods != null) {
                    this.onChangeWithMods.accept(0, 0);
                }
                return true;
            }
        }
        if (this.listening) {
            this.listening = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.listening) {
            return false;
        }
        if (event.key() == 256) { // ESC clears/cancels
            this.keyCode = 0;
            this.modifiers = 0;
        } else {
            this.keyCode = event.key();
            this.modifiers = event.modifiers();
        }
        this.listening = false;
        if (this.onChangeWithMods != null) {
            this.onChangeWithMods.accept(this.keyCode, this.modifiers);
        }
        return true;
    }

    private String getKeyName() {
        if (this.keyCode <= 0) {
            return "None";
        }
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(this.keyCode).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + this.keyCode;
        }
    }
}
