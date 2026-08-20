package com.vayuclient.hud.gui.components;

import java.awt.Color;
import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ColorPicker extends UIComponent {
    private final String label;
    private Color color;
    private final Consumer<Color> onChange;
    private boolean expanded = false;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private boolean draggingSatBright = false;
    private boolean draggingHue = false;
    private float expandProgress = 0.0f;
    private float hoverProgress = 0.0f;
    private long lastUpdate = System.currentTimeMillis();

    private static final int PICKER_WIDTH = 190;
    private static final int PICKER_HEIGHT = 150;
    private static final int HUE_BAR_HEIGHT = 14;
    private static final int SB_AREA_HEIGHT = 90;
    private static final int SB_RESOLUTION = 16;
    private static final int HUE_SEGMENTS = 24;

    public ColorPicker(int x, int y, int width, int height, String label, Color color, Consumer<Color> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.color = color;
        this.onChange = onChange;
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = color.getAlpha();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.expandProgress = AnimationUtils.smoothDelta(this.expandProgress, this.expanded ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();

        // 1. Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);

        // 2. Color Swatch & Hex Readout
        int previewW = 26;
        int previewH = 20;
        int previewX = this.x + this.width - previewW - 12;
        int previewY = this.y + (this.height - previewH) / 2;

        VayuHUDUI.roundedRect(graphics, previewX, previewY, previewW, previewH, 4, this.color.getRGB() | 0xFF000000);
        VayuHUDUI.roundedOutline(graphics, previewX, previewY, previewW, previewH, 4, this.expanded ? 0xFF38BDF8 : 0x4438BDF8);

        String hexValue = String.format("#%02X%02X%02X", this.color.getRed(), this.color.getGreen(), this.color.getBlue());
        int hexX = previewX - this.uiTextWidth(mc, hexValue) - 8;
        this.drawUiText(graphics, mc, hexValue, hexX, this.centeredTextY(mc, this.y, this.height), 0xFF38BDF8);

        // 3. Expanded Popup
        if (this.expandProgress > 0.01f) {
            this.renderExpandedPicker(graphics, mouseX, mouseY, mc);
        }
    }

    private void renderExpandedPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Minecraft mc) {
        int pickerX = this.x + this.width - PICKER_WIDTH - 12;
        int pickerY = this.y + this.height + 4;
        int animatedHeight = (int)((float)PICKER_HEIGHT * this.expandProgress);

        VayuHUDUI.roundedRect(graphics, pickerX - 6, pickerY - 6, PICKER_WIDTH + 12, animatedHeight + 12, 8, 0xF5060D17);
        VayuHUDUI.roundedOutline(graphics, pickerX - 6, pickerY - 6, PICKER_WIDTH + 12, animatedHeight + 12, 8, 0x6638BDF8);

        if (this.expandProgress < 0.3f) return;

        // Saturation & Brightness Area
        int sbCellWidth = Math.max(1, PICKER_WIDTH / SB_RESOLUTION);
        int sbCellHeight = Math.max(1, SB_AREA_HEIGHT / SB_RESOLUTION);
        for (int x = 0; x < SB_RESOLUTION; ++x) {
            for (int y = 0; y < SB_RESOLUTION; ++y) {
                float s = (float)x / (float)SB_RESOLUTION;
                float b = 1.0f - (float)y / (float)SB_RESOLUTION;
                int c = Color.HSBtoRGB(this.hue, s, b) | 0xFF000000;
                graphics.fill(pickerX + x * sbCellWidth, pickerY + y * sbCellHeight, pickerX + (x + 1) * sbCellWidth, pickerY + (y + 1) * sbCellHeight, c);
            }
        }
        VayuHUDUI.outline(graphics, pickerX, pickerY, PICKER_WIDTH, SB_AREA_HEIGHT, 0x4438BDF8);

        // SB Marker
        int markerX = pickerX + (int)(this.saturation * (float)PICKER_WIDTH);
        int markerY = pickerY + (int)((1.0f - this.brightness) * (float)SB_AREA_HEIGHT);
        VayuHUDUI.outline(graphics, markerX - 3, markerY - 3, 6, 6, 0xFFFFFFFF);

        // Hue Bar
        int hueY = pickerY + SB_AREA_HEIGHT + 8;
        int hueSegWidth = Math.max(1, PICKER_WIDTH / HUE_SEGMENTS);
        for (int i = 0; i < HUE_SEGMENTS; ++i) {
            float h = (float)i / (float)HUE_SEGMENTS;
            int c = Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000;
            graphics.fill(pickerX + i * hueSegWidth, hueY, pickerX + (i + 1) * hueSegWidth, hueY + HUE_BAR_HEIGHT, c);
        }
        VayuHUDUI.outline(graphics, pickerX, hueY, PICKER_WIDTH, HUE_BAR_HEIGHT, 0x4438BDF8);

        // Hue Marker
        int hueMarkerX = pickerX + (int)(this.hue * (float)PICKER_WIDTH);
        graphics.fill(hueMarkerX - 2, hueY - 2, hueMarkerX + 2, hueY + HUE_BAR_HEIGHT + 2, 0xFFFFFFFF);

        // Quick Preset Swatches
        int presetY = hueY + HUE_BAR_HEIGHT + 8;
        int[] presets = new int[]{0xFF00D2FF, 0xFF22C55E, 0xFFEF4444, 0xFFF59E0B, 0xFFA855F7, 0xFFFFFFFF};
        int presetW = 20;
        int presetH = 14;
        int gap = 8;
        for (int p = 0; p < presets.length; ++p) {
            int px = pickerX + p * (presetW + gap);
            VayuHUDUI.roundedRect(graphics, px, presetY, presetW, presetH, 3, presets[p]);
            VayuHUDUI.roundedOutline(graphics, px, presetY, presetW, presetH, 3, 0x44FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int previewW = 26;
        int previewH = 20;
        int previewX = this.x + this.width - previewW - 12;
        int previewY = this.y + (this.height - previewH) / 2;

        if (event.x() >= (double)previewX && event.x() <= (double)(previewX + previewW) && event.y() >= (double)previewY && event.y() <= (double)(previewY + previewH)) {
            this.expanded = !this.expanded;
            return true;
        }

        if (!this.expanded) return false;

        int pickerX = this.x + this.width - PICKER_WIDTH - 12;
        int pickerY = this.y + this.height + 4;

        // Sat/Bright Click
        if (event.x() >= (double)pickerX && event.x() <= (double)(pickerX + PICKER_WIDTH) && event.y() >= (double)pickerY && event.y() <= (double)(pickerY + SB_AREA_HEIGHT)) {
            this.draggingSatBright = true;
            this.updateSatBright(event.x(), event.y(), pickerX, pickerY);
            return true;
        }

        // Hue Click
        int hueY = pickerY + SB_AREA_HEIGHT + 8;
        if (event.x() >= (double)pickerX && event.x() <= (double)(pickerX + PICKER_WIDTH) && event.y() >= (double)hueY && event.y() <= (double)(hueY + HUE_BAR_HEIGHT)) {
            this.draggingHue = true;
            this.updateHue(event.x(), pickerX);
            return true;
        }

        // Preset Clicks
        int presetY = hueY + HUE_BAR_HEIGHT + 8;
        int[] presets = new int[]{0xFF00D2FF, 0xFF22C55E, 0xFFEF4444, 0xFFF59E0B, 0xFFA855F7, 0xFFFFFFFF};
        int presetW = 20;
        int presetH = 14;
        int gap = 8;
        for (int p = 0; p < presets.length; ++p) {
            int px = pickerX + p * (presetW + gap);
            if (event.x() >= (double)px && event.x() <= (double)(px + presetW) && event.y() >= (double)presetY && event.y() <= (double)(presetY + presetH)) {
                this.color = new Color(presets[p]);
                float[] hsb = Color.RGBtoHSB(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), null);
                this.hue = hsb[0];
                this.saturation = hsb[1];
                this.brightness = hsb[2];
                this.notifyChange();
                return true;
            }
        }

        if (event.x() < (double)(pickerX - 6) || event.x() > (double)(pickerX + PICKER_WIDTH + 6) || event.y() < (double)(pickerY - 6) || event.y() > (double)(pickerY + PICKER_HEIGHT + 6)) {
            this.expanded = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingSatBright = false;
        this.draggingHue = false;
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.expanded) return false;
        int pickerX = this.x + this.width - PICKER_WIDTH - 12;
        int pickerY = this.y + this.height + 4;
        if (this.draggingSatBright) {
            this.updateSatBright(event.x(), event.y(), pickerX, pickerY);
            return true;
        }
        if (this.draggingHue) {
            this.updateHue(event.x(), pickerX);
            return true;
        }
        return false;
    }

    private void updateSatBright(double mouseX, double mouseY, int pickerX, int pickerY) {
        this.saturation = Math.max(0.0f, Math.min(1.0f, (float)(mouseX - (double)pickerX) / (float)PICKER_WIDTH));
        this.brightness = Math.max(0.0f, Math.min(1.0f, 1.0f - (float)(mouseY - (double)pickerY) / (float)SB_AREA_HEIGHT));
        this.updateColor();
    }

    private void updateHue(double mouseX, int pickerX) {
        this.hue = Math.max(0.0f, Math.min(1.0f, (float)(mouseX - (double)pickerX) / (float)PICKER_WIDTH));
        this.updateColor();
    }

    private void updateColor() {
        int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        this.color = new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, this.alpha);
        this.notifyChange();
    }

    private void notifyChange() {
        if (this.onChange != null) {
            this.onChange.accept(this.color);
        }
    }

    public boolean isExpanded() {
        return this.expanded;
    }
}
