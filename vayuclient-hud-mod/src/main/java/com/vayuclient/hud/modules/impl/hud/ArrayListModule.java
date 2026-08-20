/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 */
package com.vayuclient.hud.modules.impl.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ArrayListModule
extends Module {
    private final ModeSetting sortMode = this.register(new ModeSetting("sort_mode", "Sort order", "length", new String[]{"length", "alphabetical"}));
    private final ColorSetting textColor = this.register(new ColorSetting("text_color", "Module text color", 255, 255, 255));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));

    private record Entry(Component name, int textWidth) {}
    private final List<Entry> cachedEntries = new ArrayList<Entry>();
    private int cachedMaxWidth = 60;
    private long lastCacheTime = 0L;

    public ArrayListModule() {
        super("ArrayList", "List of active modules", Category.HUD);
    }

    private void updateCache() {
        long now = System.currentTimeMillis();
        if (now - this.lastCacheTime < 250L && !this.cachedEntries.isEmpty()) {
            return;
        }
        this.lastCacheTime = now;
        this.cachedEntries.clear();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null || ArrayListModule.mc == null || ArrayListModule.mc.font == null) {
            return;
        }
        List<Module> all = mm.getModules();
        List<Module> enabled = new ArrayList<Module>(all.size());
        for (int i = 0; i < all.size(); i++) {
            Module m = all.get(i);
            if (m.isEnabled() && m != this) {
                enabled.add(m);
            }
        }
        if (this.sortMode.is("length")) {
            enabled.sort((a, b) -> ArrayListModule.mc.font.width((FormattedText)VayuFonts.moduleName(b.getDisplayName())) - ArrayListModule.mc.font.width((FormattedText)VayuFonts.moduleName(a.getDisplayName())));
        } else {
            enabled.sort(Comparator.comparing(Module::getDisplayName));
        }
        int max = 0;
        for (int i = 0; i < enabled.size(); i++) {
            Module m = enabled.get(i);
            Component name = VayuFonts.moduleName(m.getDisplayName());
            int width = ArrayListModule.mc.font.width((FormattedText)name);
            this.cachedEntries.add(new Entry(name, width));
            if (width > max) {
                max = width;
            }
        }
        this.cachedMaxWidth = max + 10;
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        this.updateCache();
        if (this.cachedEntries.isEmpty()) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        
        int offsetY = 0;
        int color = this.textColor.getRGB() | 0xFF000000;
        boolean showBg = ((Boolean)this.background.getValue()).booleanValue();
        for (int i = 0; i < this.cachedEntries.size(); i++) {
            Entry entry = this.cachedEntries.get(i);
            if (showBg) {
                VayuHUDUI.hudPanel(graphics, x - 5, y + offsetY - 3, entry.textWidth() + 10, 15);
            }
            graphics.text(ArrayListModule.mc.font, entry.name(), x, y + offsetY, color, false);
            offsetY += 13;
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        this.updateCache();
        return this.cachedMaxWidth;
    }

    @Override
    public int getHudHeight() {
        this.updateCache();
        return Math.max(16, this.cachedEntries.size() * 13 + 2);
    }
}

