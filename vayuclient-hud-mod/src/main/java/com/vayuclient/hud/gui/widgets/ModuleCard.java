package com.vayuclient.hud.gui.widgets;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ModuleCard {
    public static final int CARD_WIDTH = 140;
    public static final int CARD_HEIGHT = 68;

    private final Module module;
    private int x;
    private int y;
    private int width;
    private int height;
    private final Consumer<Module> onToggle;
    private final Consumer<Module> onSettings;
    private float hoverProgress;
    private boolean settingsHovered;
    private long lastUpdate = System.currentTimeMillis();

    public ModuleCard(Module module, int x, int y, int width, int height, Consumer<Module> onToggle, Consumer<Module> onSettings) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onToggle = onToggle;
        this.onSettings = onSettings;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = this.contains(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float delta = (float) (now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, hovered ? 1.0f : 0.0f, 0.28f, delta * 60.0f);

        int cardColor = VayuHUDUI.blend(0xD00A111A, 0xE6141E2D, this.hoverProgress);
        VayuHUDUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 6, cardColor);
        VayuHUDUI.roundedOutline(graphics, this.x, this.y, this.width, this.height, 6, this.module.isEnabled() ? VayuTheme.PRIMARY : (hovered ? VayuTheme.BORDER_HOVER : VayuTheme.BORDER_SUBTLE));

        if (this.module.isEnabled()) {
            graphics.fill(this.x + 4, this.y + 1, this.x + this.width - 4, this.y + 2, VayuTheme.PRIMARY);
        }

        this.drawTitle(graphics, mc);
        this.drawIcon(graphics);
        this.drawActions(graphics, mc, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Minecraft mc) {
        String name = this.module.getDisplayName();
        int titleX = this.x + 36;
        int titleY = this.y + 10;
        int maxTitleWidth = this.width - 42;
        String fitted = this.fit(mc, name, maxTitleWidth);
        graphics.text(mc.font, fitted, titleX, titleY, this.module.isEnabled() ? VayuTheme.TEXT_PRIMARY : VayuTheme.TEXT_MUTED, true);
    }

    private void drawIcon(GuiGraphicsExtractor graphics) {
        int iconSize = 22;
        int iconX = this.x + 8;
        int iconY = this.y + 8;
        VayuHUDUI.roundedRect(graphics, iconX, iconY, iconSize, iconSize, 4, 0xFF0F1722);
        VayuHUDUI.roundedOutline(graphics, iconX, iconY, iconSize, iconSize, 4, this.module.isEnabled() ? 0x4438BDF8 : 0x2238BDF8);
        graphics.blit(RenderPipelines.GUI_TEXTURED, VayuHUDUI.icon(this.module), iconX + 3, iconY + 3, 0.0f, 0.0f, 16, 16, 16, 16, VayuHUDUI.withAlpha(-1, this.module.isEnabled() ? 255 : 140));
    }

    private void drawActions(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY) {
        int actionY = this.y + this.height - 24;
        int gearX = this.x + 8;
        int gearSize = 18;
        int toggleX = gearX + gearSize + 6;
        int toggleW = this.width - 16 - gearSize - 6;
        int toggleH = 18;

        this.settingsHovered = mouseX >= gearX && mouseX <= gearX + gearSize && mouseY >= actionY && mouseY <= actionY + gearSize;
        VayuHUDUI.roundedRect(graphics, gearX, actionY, gearSize, gearSize, 4, this.settingsHovered ? VayuTheme.GLASS_HOVER : VayuTheme.GLASS_PANEL);
        VayuHUDUI.roundedOutline(graphics, gearX, actionY, gearSize, gearSize, 4, this.settingsHovered ? VayuTheme.PRIMARY : 0x2A38BDF8);
        VayuHUDUI.drawSettingsVector(graphics, gearX + gearSize / 2, actionY + gearSize / 2, 8, this.settingsHovered ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED);

        boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= actionY && mouseY <= actionY + toggleH;
        int toggleBg = this.module.isEnabled() 
            ? (toggleHovered ? 0xFF0284C7 : 0xFF0891B2) 
            : (toggleHovered ? VayuTheme.GLASS_HOVER : VayuTheme.GLASS_PANEL);

        VayuHUDUI.roundedRect(graphics, toggleX, actionY, toggleW, toggleH, 4, toggleBg);
        VayuHUDUI.roundedOutline(graphics, toggleX, actionY, toggleW, toggleH, 4, this.module.isEnabled() ? VayuTheme.PRIMARY : 0x2A38BDF8);

        String label = this.module.isEnabled() ? "ON" : "OFF";
        int textW = mc.font.width(label);
        graphics.text(mc.font, label, toggleX + (toggleW - textW) / 2, actionY + 5, this.module.isEnabled() ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, true);
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= (double) this.x && mouseX <= (double) (this.x + this.width) && mouseY >= (double) this.y && mouseY <= (double) (this.y + this.height);
    }

    private String fit(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && mc.font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (!this.contains(mouseX, mouseY)) {
            return false;
        }
        if (event.button() == 0 && this.settingsHovered && this.onSettings != null) {
            this.onSettings.accept(this.module);
            return true;
        }
        if (event.button() == 0 && this.module.isHotkeyOnly() && this.module.getKeyBinding() == 0 && this.onSettings != null) {
            this.onSettings.accept(this.module);
            return true;
        }
        if (event.button() == 0 && this.onToggle != null) {
            this.onToggle.accept(this.module);
            return true;
        }
        return true;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public Module getModule() {
        return this.module;
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        return this.module.getDisplayName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
            || this.module.getDescription().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
