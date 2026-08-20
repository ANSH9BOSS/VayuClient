/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 */
package net.fastclient.hud.gui.widgets;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.fastclient.hud.gui.FastClientFonts;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ModuleCard {
    private static final int BUTTON_H = 43;
    private static final int GEAR_SIZE = 43;
    private static final int GAP = 14;
    private static final int SIDE_PAD = 17;
    private static final int BOTTOM_PAD = 17;
    private static final float MATERIAL_SYMBOL_Y_OFFSET = 2.5f;
    public static final int CARD_HEIGHT = 186;
    public static final int CARD_WIDTH = 232;
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
        float delta = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, hovered ? 1.0f : 0.0f, 0.28f, delta * 60.0f);
        int cardColor = FastClientUI.blend(-653586413, -434955745, this.hoverProgress);
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, cardColor);
        FastClientUI.outline(graphics, this.x, this.y, this.width, this.height, FastClientUI.blend(1143616571, 1717331565, this.hoverProgress));
        if (this.module.isEnabled()) {
            graphics.fill(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 3, FastClientUI.withAlpha(-39373, 210));
        }
        this.drawTitle(graphics, mc);
        this.drawIcon(graphics);
        this.drawActions(graphics, mc, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Minecraft mc) {
        String name = this.module.getDisplayName();
        int titleX = this.x + 17;
        int titleY = this.y + 15;
        int maxTitleWidth = this.width - 34;
        float titleScale = FastClientFonts.titleScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)titleX, (float)titleY);
        graphics.pose().scale(titleScale, titleScale);
        graphics.pose().translate((float)(-titleX), (float)(-titleY));
        graphics.text(mc.font, FastClientFonts.title(this.fit(mc, name, Math.round((float)maxTitleWidth / titleScale))), titleX, titleY, this.module.isEnabled() ? -723724 : -4671304, false);
        graphics.pose().popMatrix();
    }

    private void drawIcon(GuiGraphicsExtractor graphics) {
        int iconSize = Math.min(60, Math.max(44, this.width / 5));
        int iconX = this.x + this.width / 2 - iconSize / 2;
        int iconY = this.y + 39;
        graphics.blit(RenderPipelines.GUI_TEXTURED, FastClientUI.icon(this.module), iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize, FastClientUI.withAlpha(-1, this.module.isEnabled() ? 245 : 145));
    }

    private void drawActions(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY) {
        boolean toggleHovered;
        int actionY = this.y + this.height - 17 - 43;
        int gearX = this.x + 17;
        int toggleX = gearX + 43 + 14;
        int toggleW = this.width - 34 - 43 - 14;
        int gearCenterY = actionY + 0;
        this.settingsHovered = mouseX >= gearX && mouseX <= gearX + 43 && mouseY >= gearCenterY && mouseY <= gearCenterY + 43;
        FastClientUI.roundedRect(graphics, gearX, gearCenterY, 43, 43, 4, this.settingsHovered ? -266722777 : -435153640);
        this.drawCenteredMaterialSymbol(graphics, mc, "\ue8b8", gearX + 21, gearCenterY + 21 + 2, 1.7f, this.settingsHovered ? -723724 : -7303024);
        boolean bl = toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= actionY && mouseY <= actionY + 43;
        int toggleColor = this.module.isEnabled() ? (toggleHovered ? -15243738 : -15511009) : (toggleHovered ? -266722777 : -435153640);
        FastClientUI.roundedRect(graphics, toggleX, actionY, toggleW, 43, 4, toggleColor);
        String label = this.module.isEnabled() ? "Enabled" : "Disabled";
        this.drawCenteredComponent(graphics, mc, FastClientFonts.body(label), toggleX + toggleW / 2, actionY + 21, FastClientFonts.bodyScale(), this.module.isEnabled() ? -8658034 : -4671304);
    }

    private void drawCenteredComponent(GuiGraphicsExtractor graphics, Minecraft mc, Component text, int centerX, int centerY, float scale, int color) {
        float textX = (float)centerX - (float)mc.font.width((FormattedText)text) * scale / 2.0f;
        float f = centerY;
        Objects.requireNonNull(mc.font);
        float textY = f - 9.0f * scale / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-textX, -textY);
        graphics.text(mc.font, text, Math.round(textX), Math.round(textY), color, false);
        graphics.pose().popMatrix();
    }

    private void drawCenteredMaterialSymbol(GuiGraphicsExtractor graphics, Minecraft mc, String symbol, int centerX, int centerY, float scale, int color) {
        int correctedCenterY = centerY + Math.round(2.5f * scale);
        this.drawCenteredComponent(graphics, mc, FastClientFonts.filledMaterialSymbol(symbol), centerX, correctedCenterY, scale, color);
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
    }

    private String fit(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width((FormattedText)FastClientFonts.title(text)) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && mc.font.width((FormattedText)FastClientFonts.title(result + "...")) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private String getKeyName(int keyCode) {
        return switch (keyCode) {
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BACK";
            case 32 -> "SPACE";
            default -> keyCode >= 65 && keyCode <= 90 ? String.valueOf((char)keyCode) : (keyCode >= 48 && keyCode <= 57 ? String.valueOf((char)keyCode) : (keyCode >= 290 && keyCode <= 301 ? "F" + (keyCode - 289) : "KEY" + keyCode));
        };
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseY;
        double mouseX = event.x();
        if (!this.contains(mouseX, mouseY = event.y())) {
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

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getY() {
        return this.y;
    }

    public Module getModule() {
        return this.module;
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        String lowerName = this.module.getName().toLowerCase(Locale.ROOT);
        String lowerDisplayName = this.module.getDisplayName().toLowerCase(Locale.ROOT);
        String lowerDesc = this.module.getDescription() != null ? this.module.getDescription().toLowerCase(Locale.ROOT) : "";
        return lowerName.contains(lowerQuery) || lowerDisplayName.contains(lowerQuery) || lowerDesc.contains(lowerQuery) || this.getKeyName(this.module.getKeyBinding()).toLowerCase(Locale.ROOT).contains(lowerQuery);
    }
}

