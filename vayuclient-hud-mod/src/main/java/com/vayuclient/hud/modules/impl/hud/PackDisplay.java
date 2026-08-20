/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.server.packs.repository.Pack
 */
package com.vayuclient.hud.modules.impl.hud;

import java.util.Collection;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.packs.repository.Pack;

public class PackDisplay
extends Module {
    public PackDisplay() {
        super("PackDisplay", "Shows active resource packs", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        Collection<Pack> packs = mc.getResourcePackRepository().getSelectedPacks();
        int maxWidth = 0;
        int count = 0;
        for (Pack pack : packs) {
            String name = pack.getTitle().getString();
            if (name.equals("Default") || name.equals("Fabric Mods")) continue;
            maxWidth = Math.max(maxWidth, PackDisplay.mc.font.width(name));
            ++count;
        }
        if (count == 0) {
            graphics.pose().popMatrix();
            return;
        }
        VayuHUDUI.hudPanel(graphics, x - 5, y - 4, maxWidth + 10, count * 12 + 6);
        int offsetY = 0;
        for (Pack pack : packs) {
            String name = pack.getTitle().getString();
            if (name.equals("Default") || name.equals("Fabric Mods")) continue;
            graphics.text(PackDisplay.mc.font, name, x, y + offsetY, -1, true);
            offsetY += 12;
        }
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        int maxWidth = 0;
        Collection<Pack> packs = mc.getResourcePackRepository().getSelectedPacks();
        for (Pack pack : packs) {
            int width;
            String name = pack.getTitle().getString();
            if (name.equals("Default") || name.equals("Fabric Mods") || (width = PackDisplay.mc.font.width(name)) <= maxWidth) continue;
            maxWidth = width;
        }
        return maxWidth + 10;
    }

    @Override
    public int getHudHeight() {
        int count = 0;
        Collection<Pack> packs = mc.getResourcePackRepository().getSelectedPacks();
        for (Pack pack : packs) {
            String name = pack.getTitle().getString();
            if (name.equals("Default") || name.equals("Fabric Mods")) continue;
            ++count;
        }
        return count * 12 + 6;
    }
}

