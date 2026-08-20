/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MemoryHUD
extends Module {
    private final ModeSetting displayMode = this.register(new ModeSetting("display", "Display format", "percent", new String[]{"percent", "mb", "both"}));
    private final BooleanSetting colorByUsage = this.register(new BooleanSetting("color_by_usage", "Color by usage", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final ColorSetting staticColor = this.register(new ColorSetting("color", "Static color", 255, 255, 255));

    public MemoryHUD() {
        super("Memory", "Shows RAM/memory usage", Category.HUD);
        this.staticColor.visibleWhen(() -> !this.colorByUsage.isEnabled());
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        String text;
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int percent = (int)(usedMemory * 100L / maxMemory);
        long usedMB = usedMemory / 0x100000L;
        long maxMB = maxMemory / 0x100000L;
        switch ((String)this.displayMode.getValue()) {
            case "mb": {
                text = usedMB + "/" + maxMB + " MB";
                break;
            }
            case "both": {
                text = percent + "% (" + usedMB + "/" + maxMB + " MB)";
                break;
            }
            default: {
                text = "RAM: " + percent + "%";
                break;
            }
        }
        int color = this.colorByUsage.isEnabled() ? (percent < 50 ? -11141291 : (percent < 75 ? -171 : (percent < 90 ? -22016 : -43691))) : this.staticColor.getRGB() | 0xFF000000;
        if (this.background.isEnabled()) {
            VayuHUDUI.hudText(graphics, MemoryHUD.mc.font, text, x, y, color, true);
        } else {
            graphics.text(MemoryHUD.mc.font, text, x, y, color, true);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int percent = (int)(usedMemory * 100L / maxMemory);
        long usedMB = usedMemory / 0x100000L;
        long maxMB = maxMemory / 0x100000L;
        String text = switch ((String)this.displayMode.getValue()) {
            case "mb" -> usedMB + "/" + maxMB + " MB";
            case "both" -> percent + "% (" + usedMB + "/" + maxMB + " MB)";
            default -> "RAM: " + percent + "%";
        };
        return MemoryHUD.mc.font.width(text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

