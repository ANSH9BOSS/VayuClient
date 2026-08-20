/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 */
package com.vayuclient.hud.gui.components;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.render.AnimationUtils;
import com.vayuclient.hud.gui.VayuTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class KeybindButton
extends UIComponent {
    private final String label;
    private int keyCode;
    private int modifiers;
    private final BiConsumer<Integer, Integer> onChangeWithMods;
    private boolean listening = false;
    private float hoverProgress;
    private float pulseProgress;
    private long lastUpdate = System.currentTimeMillis();

    public KeybindButton(int x, int y, int width, int height, String label, int keyCode, Consumer<Integer> onChange) {
        this(x, y, width, height, label, keyCode, 0, (k, m) -> onChange.accept((Integer)k));
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
        int textColor;
        String keyText;
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.pulseProgress = this.listening ? (float)(Math.sin((double)now / 200.0) * 0.5 + 0.5) : AnimationUtils.smoothDelta(this.pulseProgress, 0.0f, 0.5f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();
        if (this.hoverProgress > 0.01f && !this.listening) {
            int bgAlpha = (int)(15.0f * this.hoverProgress);
            VayuHUDUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 4, VayuHUDUI.withAlpha(-266722777, bgAlpha));
        }
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(-7303024, -723724, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);
        int btnWidth = 100;
        int btnX = this.x + this.width - btnWidth - 12;
        int btnY = this.y + 2;
        int btnHeight = this.height - 4;
        int bgColor = this.listening ? VayuHUDUI.blend(-16723201, -13058312, this.pulseProgress) : (this.hovered ? -266722777 : -435153640);
        VayuHUDUI.roundedRect(graphics, btnX, btnY, btnWidth, btnHeight, 4, bgColor);
        int borderColor = this.listening ? -13058312 : VayuHUDUI.blend(1143616571, -16723201, this.hoverProgress * 0.5f);
        VayuHUDUI.outline(graphics, btnX, btnY, btnWidth, btnHeight, borderColor);
        if (this.listening) {
            int glowAlpha = (int)(40.0f * this.pulseProgress);
            graphics.fill(btnX - 2, btnY - 2, btnX + btnWidth + 2, btnY, VayuHUDUI.withAlpha(-16723201, glowAlpha));
            graphics.fill(btnX - 2, btnY + btnHeight, btnX + btnWidth + 2, btnY + btnHeight + 2, VayuHUDUI.withAlpha(-16723201, glowAlpha));
        }
        if (this.listening) {
            keyText = "Press Key...";
            textColor = -723724;
        } else if (this.keyCode == 0) {
            keyText = "Not Set";
            textColor = -9934744;
        } else {
            keyText = this.getKeyName();
            textColor = -16723201;
        }
        int textX = btnX + (btnWidth - this.uiTextWidth(mc, keyText)) / 2;
        this.drawUiText(graphics, mc, keyText, textX, this.centeredTextY(mc, btnY, btnHeight), textColor);
        if (this.hovered && !this.listening && this.keyCode != 0) {
            String hint = "Right-click to clear";
            int hintX = btnX - this.uiTextWidth(mc, hint) - 8;
            this.drawUiText(graphics, mc, hint, hintX, this.centeredTextY(mc, this.y, this.height), -9934744);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            if (event.button() == 0) {
                this.listening = true;
            } else if (event.button() == 1) {
                this.keyCode = 0;
                this.modifiers = 0;
                this.listening = false;
                this.notifyChange();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.listening) {
            int keyCode = event.key();
            int mods = event.modifiers();
            if (keyCode == 256) {
                this.listening = false;
            } else {
                if (keyCode == 340 || keyCode == 344 || keyCode == 341 || keyCode == 345 || keyCode == 342 || keyCode == 346) {
                    return true;
                }
                this.keyCode = keyCode;
                this.modifiers = 0;
                if ((mods & 1) != 0) {
                    this.modifiers |= 1;
                }
                if ((mods & 2) != 0) {
                    this.modifiers |= 2;
                }
                if ((mods & 4) != 0) {
                    this.modifiers |= 4;
                }
                this.listening = false;
                this.notifyChange();
            }
            return true;
        }
        return false;
    }

    private void notifyChange() {
        if (this.onChangeWithMods != null) {
            this.onChangeWithMods.accept(this.keyCode, this.modifiers);
        }
    }

    private String getKeyName() {
        if (this.keyCode == 0) {
            return "None";
        }
        StringBuilder name = new StringBuilder();
        if ((this.modifiers & 2) != 0) {
            name.append("Ctrl+");
        }
        if ((this.modifiers & 4) != 0) {
            name.append("Alt+");
        }
        if ((this.modifiers & 1) != 0) {
            name.append("Shift+");
        }
        try {
            Object keyName = InputConstants.Type.KEYSYM.getOrCreate(this.keyCode).getDisplayName().getString();
            if (((String)keyName).length() > 6 && name.length() > 0) {
                keyName = ((String)keyName).substring(0, 4) + "..";
            } else if (((String)keyName).length() > 10) {
                keyName = ((String)keyName).substring(0, 8) + "..";
            }
            name.append((String)keyName);
        }
        catch (Exception e) {
            name.append("Key").append(this.keyCode);
        }
        return name.toString();
    }

    public boolean isListening() {
        return this.listening;
    }
}

