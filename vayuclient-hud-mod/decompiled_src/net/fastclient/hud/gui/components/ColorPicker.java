/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 */
package net.fastclient.hud.gui.components;

import java.awt.Color;
import java.util.function.Consumer;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.gui.components.UIComponent;
import net.fastclient.hud.render.AnimationUtils;
import net.fastclient.hud.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ColorPicker
extends UIComponent {
    private final String label;
    private Color color;
    private final Consumer<Color> onChange;
    private boolean expanded = false;
    private float hue = 0.0f;
    private float saturation = 1.0f;
    private float brightness = 1.0f;
    private float hoverProgress = 0.0f;
    private float expandProgress = 0.0f;
    private long lastUpdate = System.currentTimeMillis();
    private boolean draggingHue = false;
    private boolean draggingSatBright = false;
    private static final int PICKER_WIDTH = 180;
    private static final int PICKER_HEIGHT = 150;
    private static final int HUE_BAR_HEIGHT = 16;
    private static final int SB_AREA_HEIGHT = 100;
    private static final int SB_RESOLUTION = 16;
    private static final int HUE_SEGMENTS = 24;

    public ColorPicker(int x, int y, int width, int height, String label, Color initialColor, Consumer<Color> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.color = initialColor;
        this.onChange = onChange;
        float[] hsb = Color.RGBtoHSB(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        float targetHover = this.hovered ? 1.0f : 0.0f;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, targetHover, 0.4f, dt * 60.0f);
        float targetExpand = this.expanded ? 1.0f : 0.0f;
        this.expandProgress = AnimationUtils.smoothDelta(this.expandProgress, targetExpand, 0.3f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();
        if (this.hoverProgress > 0.01f && !this.expanded) {
            int bgAlpha = (int)(15.0f * this.hoverProgress);
            FastClientUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 4, FastClientUI.withAlpha(-266722777, bgAlpha));
        }
        String displayLabel = Theme.formatSettingName(this.label);
        int labelColor = FastClientUI.blend(-7303024, -723724, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);
        int previewSize = 24;
        int previewX = this.x + this.width - previewSize - 12;
        int previewY = this.y + (this.height - previewSize) / 2;
        int checkSize = 6;
        for (int cx = 0; cx < previewSize / checkSize; ++cx) {
            for (int cy = 0; cy < previewSize / checkSize; ++cy) {
                int checkColor = (cx + cy) % 2 == 0 ? -8355712 : -12566464;
                graphics.fill(previewX + cx * checkSize, previewY + cy * checkSize, previewX + (cx + 1) * checkSize, previewY + (cy + 1) * checkSize, checkColor);
            }
        }
        graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, this.color.getRGB() | 0xFF000000);
        int borderColor = FastClientUI.blend(1143616571, -39373, this.hoverProgress);
        FastClientUI.outline(graphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, borderColor);
        String hexValue = String.format("#%02X%02X%02X", this.color.getRed(), this.color.getGreen(), this.color.getBlue());
        int hexX = previewX - this.uiTextWidth(mc, hexValue) - 8;
        this.drawUiText(graphics, mc, hexValue, hexX, this.centeredTextY(mc, this.y, this.height), -7303024);
        if (this.expandProgress > 0.01f) {
            this.renderExpandedPicker(graphics, mouseX, mouseY, mc);
        }
    }

    private void renderExpandedPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Minecraft mc) {
        int pickerX = this.x + this.width - 180 - 12;
        int pickerY = this.y + this.height + 4;
        int animatedHeight = (int)(150.0f * this.expandProgress);
        int alpha = (int)(255.0f * this.expandProgress);
        FastClientUI.roundedRect(graphics, pickerX - 4, pickerY - 4, 188, animatedHeight + 8, 5, FastClientUI.withAlpha(-435219433, alpha));
        graphics.fill(pickerX - 4, pickerY - 4, pickerX + 180 + 4, pickerY - 2, FastClientUI.withAlpha(-39373, alpha));
        if (this.expandProgress < 0.3f) {
            return;
        }
        int sbX = pickerX;
        int sbY = pickerY;
        int sbWidth = 180;
        int sbHeight = 100;
        this.renderSaturationBrightnessGradient(graphics, sbX, sbY, sbWidth, sbHeight);
        FastClientUI.outline(graphics, sbX - 1, sbY - 1, sbWidth + 2, sbHeight + 2, 1143616571);
        int selectorX = sbX + (int)(this.saturation * (float)sbWidth);
        int selectorY = sbY + (int)((1.0f - this.brightness) * (float)sbHeight);
        graphics.fill(selectorX - 5, selectorY - 1, selectorX + 5, selectorY + 2, -1);
        graphics.fill(selectorX - 1, selectorY - 5, selectorX + 2, selectorY + 5, -1);
        graphics.fill(selectorX - 4, selectorY, selectorX + 4, selectorY + 1, -16777216);
        graphics.fill(selectorX, selectorY - 4, selectorX + 1, selectorY + 4, -16777216);
        int hueY = sbY + sbHeight + 8;
        int hueWidth = sbWidth;
        this.renderHueGradient(graphics, sbX, hueY, hueWidth, 16);
        FastClientUI.outline(graphics, sbX - 1, hueY - 1, hueWidth + 2, 18, 1143616571);
        int hueSelectorX = sbX + (int)(this.hue * (float)hueWidth);
        graphics.fill(hueSelectorX - 2, hueY - 2, hueSelectorX + 3, hueY + 16 + 2, -1);
        graphics.fill(hueSelectorX - 1, hueY - 1, hueSelectorX + 2, hueY + 16 + 1, -16777216);
        graphics.fill(hueSelectorX, hueY, hueSelectorX + 1, hueY + 16, Color.HSBtoRGB(this.hue, 1.0f, 1.0f) | 0xFF000000);
        int previewY2 = hueY + 16 + 8;
        graphics.fill(sbX, previewY2, sbX + 40, previewY2 + 18, this.color.getRGB() | 0xFF000000);
        FastClientUI.outline(graphics, sbX - 1, previewY2 - 1, 42, 20, 1143616571);
        String hex = String.format("#%02X%02X%02X", this.color.getRed(), this.color.getGreen(), this.color.getBlue());
        this.drawUiText(graphics, mc, hex, sbX + 48, previewY2 + 5, -723724);
    }

    private void renderSaturationBrightnessGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int cellWidth = width / 16;
        int cellHeight = height / 16;
        for (int row = 0; row < 16; ++row) {
            for (int col = 0; col < 16; ++col) {
                int x1 = x + col * cellWidth;
                int y1 = y + row * cellHeight;
                int x2 = col == 15 ? x + width : x1 + cellWidth;
                int y2 = row == 15 ? y + height : y1 + cellHeight;
                float s = ((float)col + 0.5f) / 16.0f;
                float b = 1.0f - ((float)row + 0.5f) / 16.0f;
                int cellColor = Color.HSBtoRGB(this.hue, s, b) | 0xFF000000;
                graphics.fill(x1, y1, x2, y2, cellColor);
            }
        }
    }

    private void renderHueGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int segmentWidth = width / 24;
        for (int i = 0; i < 24; ++i) {
            int x1 = x + i * segmentWidth;
            int x2 = i == 23 ? x + width : x1 + segmentWidth;
            float h = ((float)i + 0.5f) / 24.0f;
            int segColor = Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000;
            graphics.fill(x1, y, x2, y + height, segColor);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) {
            return false;
        }
        if (this.expanded && this.expandProgress > 0.9f) {
            int pickerX = this.x + this.width - 180 - 12;
            int pickerY = this.y + this.height + 4;
            int sbWidth = 180;
            int sbHeight = 100;
            int hueY = pickerY + sbHeight + 8;
            if (mouseX >= (double)pickerX && mouseX <= (double)(pickerX + sbWidth) && mouseY >= (double)pickerY && mouseY <= (double)(pickerY + sbHeight)) {
                this.draggingSatBright = true;
                this.updateSaturationBrightness(mouseX, mouseY, pickerX, pickerY, sbWidth, sbHeight);
                return true;
            }
            if (mouseX >= (double)pickerX && mouseX <= (double)(pickerX + sbWidth) && mouseY >= (double)hueY && mouseY <= (double)(hueY + 16)) {
                this.draggingHue = true;
                this.updateHue(mouseX, pickerX, sbWidth);
                return true;
            }
            if (mouseX < (double)(pickerX - 4) || mouseX > (double)(pickerX + 180 + 4) || mouseY < (double)(pickerY - 4) || mouseY > (double)(pickerY + 150 + 4)) {
                this.expanded = false;
                return true;
            }
            return true;
        }
        if (this.isHovered((int)mouseX, (int)mouseY)) {
            this.expanded = !this.expanded;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) {
            return false;
        }
        int pickerX = this.x + this.width - 180 - 12;
        int pickerY = this.y + this.height + 4;
        int sbWidth = 180;
        int sbHeight = 100;
        if (this.draggingSatBright) {
            this.updateSaturationBrightness(mouseX, mouseY, pickerX, pickerY, sbWidth, sbHeight);
            return true;
        }
        if (this.draggingHue) {
            this.updateHue(mouseX, pickerX, sbWidth);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingHue = false;
        this.draggingSatBright = false;
        return false;
    }

    private void updateSaturationBrightness(double mouseX, double mouseY, int pickerX, int pickerY, int sbWidth, int sbHeight) {
        this.saturation = (float)Math.max(0.0, Math.min(1.0, (mouseX - (double)pickerX) / (double)sbWidth));
        this.brightness = (float)Math.max(0.0, Math.min(1.0, 1.0 - (mouseY - (double)pickerY) / (double)sbHeight));
        this.updateColor();
    }

    private void updateHue(double mouseX, int pickerX, int hueWidth) {
        this.hue = (float)Math.max(0.0, Math.min(1.0, (mouseX - (double)pickerX) / (double)hueWidth));
        this.updateColor();
    }

    private void updateColor() {
        int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        this.color = new Color(rgb);
        if (this.onChange != null) {
            this.onChange.accept(this.color);
        }
    }

    @Override
    public int getHeight() {
        if (this.expanded && this.expandProgress > 0.5f) {
            return this.height + 150 + 8;
        }
        return this.height;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color color) {
        this.color = color;
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }
}

