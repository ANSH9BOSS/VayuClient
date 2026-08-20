/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.toasts.SystemToast
 *  net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId
 *  net.minecraft.client.gui.components.toasts.ToastManager
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.resources.Identifier
 *  org.lwjgl.glfw.GLFW
 */
package net.fastclient.hud.gui.screens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.fastclient.hud.FastClientHUDClient;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientFonts;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.gui.screens.ClickGUIScreen;
import net.fastclient.hud.gui.screens.ModuleConfigScreen;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class HudOverlayScreen
extends Screen {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/logo.png");
    private static final Identifier FAST_SETTINGS_ICON = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/fasticon_white.png");
    private static final int FAST_SETTINGS_ICON_TEXTURE_SIZE = 96;
    private static final int LOGO_SIZE = 58;
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTON_SPACING = 14;
    private static final int QUICK_BUTTON_SIZE = 32;
    private static final int QUICK_ICON_GAP = 12;
    private static final float CENTER_UI_SCALE = 1.3f;
    private static final int MODULE_ACTION_SIZE = 18;
    private static final int MODULE_REMOVE_SIZE = 18;
    private static final int MODULE_REMOVE_HIT_SIZE = 22;
    private static final int MODULE_ACTION_MARGIN = 4;
    private static final int MODULE_ACTION_ICON_Y_OFFSET = 4;
    private static final int RESIZE_HANDLE_SIZE = 13;
    private static final int RESIZE_EDGE_HIT_SIZE = 12;
    private static final int CENTER_BUTTON_BG = -15921133;
    private static final int CENTER_BUTTON_BORDER = -14406863;
    private static final int CENTER_BUTTON_HOVER_LEFT = -12958640;
    private static final int CENTER_BUTTON_HOVER_RIGHT = -15591911;
    private static final int CENTER_BUTTON_HOVER_BORDER = -13616829;
    private static final float MATERIAL_SYMBOL_VISUAL_Y_OFFSET = 2.0f;
    private static final String[][] QUICK_BUTTONS = new String[][]{{"overlay_store", "\uea12", "Store"}, {"overlay_cosmetics", "\uf19e", "Cosmetics"}, {"overlay_social", "\ue8af", "Social"}};
    private final List<DraggableModule> draggables = new ArrayList<DraggableModule>();
    private DraggableModule dragging = null;
    private DraggableModule selected = null;
    private DraggableModule configTarget = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private DraggableModule resizing = null;
    private int resizeStartX;
    private int resizeStartY;
    private float resizeStartScale;
    private boolean snapToGrid = false;
    private boolean showGuides = true;
    private int gridSize = 10;
    private final Set<Integer> activeGuidesX = new HashSet<Integer>();
    private final Set<Integer> activeGuidesY = new HashSet<Integer>();
    private boolean configPanelOpen = false;
    private AnimationUtils.Animation configPanelAnimation;
    private static final int CONFIG_PANEL_WIDTH = 160;
    private AnimationUtils.Animation openAnimation;
    private AnimationUtils.Animation buttonHoverAnimation;
    private static final int SNAP_THRESHOLD = 8;
    private static final int NUDGE_AMOUNT = 1;
    private static final int NUDGE_AMOUNT_FAST = 10;
    private static final float SCALE_STEP = 0.1f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 10.0f;
    private boolean settingsButtonHovered = false;

    public HudOverlayScreen() {
        super((Component)Component.literal((String)"FastClient HUD"));
    }

    private int screenWidth() {
        return DisplaySpace.width();
    }

    private int screenHeight() {
        return DisplaySpace.height();
    }

    private boolean isShiftKeyDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey((long)handle, (int)340) == 1 || GLFW.glfwGetKey((long)handle, (int)344) == 1;
    }

    protected void init() {
        this.draggables.clear();
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 250L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        if (this.buttonHoverAnimation == null) {
            this.buttonHoverAnimation = new AnimationUtils.Animation(0.0f, 120L);
        }
        if (this.configPanelAnimation == null) {
            this.configPanelAnimation = new AnimationUtils.Animation(0.0f, 150L);
        }
        for (Module module : FastClientHUDClient.getInstance().getModuleManager().getModules()) {
            if (!module.isEnabled() || !module.isHudVisible()) continue;
            this.draggables.add(new DraggableModule(module, module.getHudX(), module.getHudY()));
        }
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.dragging == null) {
            graphics.fill(0, 0, this.screenWidth(), this.screenHeight(), 0x26000000);
        }
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        if (this.dragging == null) {
            this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        }
        float animProgress = this.openAnimation.getValue();
        if (this.dragging != null && this.showGuides) {
            this.renderAlignmentGuides(graphics);
        }
        for (DraggableModule dm : this.draggables) {
            this.renderDraggableModule(graphics, dm, pxMouseX, pxMouseY);
        }
        if (this.resizing != null) {
            this.renderPositionInfo(graphics, this.resizing);
        }
        if (this.dragging == null) {
            this.renderCenterBranding(graphics, pxMouseX, pxMouseY, animProgress);
            if (this.configPanelOpen && this.configTarget != null) {
                this.renderConfigPanel(graphics, pxMouseX, pxMouseY);
            }
            this.renderFooter(graphics, animProgress);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void renderCenterBranding(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float animProgress) {
        int centerX = this.screenWidth() / 2;
        int centerY = this.screenHeight() / 2;
        int animOffsetY = (int)((1.0f - animProgress) * 30.0f);
        int alpha = (int)(animProgress * 255.0f);
        int logoSize = HudOverlayScreen.centerLogoSize();
        int buttonWidth = HudOverlayScreen.centerButtonWidth();
        int buttonHeight = HudOverlayScreen.centerButtonHeight();
        int buttonSpacing = HudOverlayScreen.centerButtonSpacing();
        int logoX = centerX - logoSize / 2;
        int logoY = centerY - HudOverlayScreen.scaledCenter(86) + animOffsetY;
        int logoColor = (int)(animProgress * 255.0f) << 24 | 0xFFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(LOGO_TEXTURE), logoX, logoY, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize, logoColor);
        int btnX = centerX - buttonWidth / 2;
        int btnY = logoY + logoSize + buttonSpacing;
        this.settingsButtonHovered = mouseX >= btnX && mouseX <= btnX + buttonWidth && mouseY >= btnY && mouseY <= btnY + buttonHeight;
        this.buttonHoverAnimation.animateTo(this.settingsButtonHovered ? 1.0f : 0.0f);
        float hoverProgress = this.buttonHoverAnimation.getValue();
        this.drawFastSettingsButton(graphics, btnX, btnY, buttonWidth, buttonHeight, hoverProgress, alpha);
        int quickY = btnY + buttonHeight + HudOverlayScreen.scaledCenter(9);
        int iconSize = HudOverlayScreen.centerQuickButtonSize();
        int iconGap = HudOverlayScreen.scaledCenter(12);
        int totalW = iconSize * QUICK_BUTTONS.length + iconGap * (QUICK_BUTTONS.length - 1);
        int groupX = centerX - totalW / 2;
        for (int i = 0; i < QUICK_BUTTONS.length; ++i) {
            int qx = groupX + i * (iconSize + iconGap);
            String symbol = QUICK_BUTTONS[i][1];
            String hoverLabel = QUICK_BUTTONS[i][2];
            this.drawOverlayQuickButton(graphics, qx, quickY, iconSize, symbol, hoverLabel, alpha, mouseX, mouseY);
        }
    }

    private void drawOverlayQuickButton(GuiGraphicsExtractor graphics, int x, int y, int size, String symbol, String hoverLabel, int alpha, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        this.drawChromeIconButtonSurface(graphics, x, y, size, hovered, alpha);
        this.drawScaledText(graphics, FastClientFonts.filledMaterialSymbol(symbol), x + size / 2, y + size / 2 + Math.round(3.3f), 1.65f, FastClientUI.withAlpha(hovered ? -723724 : -7303024, alpha), false);
        if (hovered) {
            this.drawQuickButtonTooltip(graphics, hoverLabel, x + size / 2, y + size + 10, alpha);
        }
    }

    private void drawChromeIconButtonSurface(GuiGraphicsExtractor graphics, int x, int y, int size, boolean hovered, int alpha) {
        int radius = Math.max(3, Math.round((float)size * 0.09f));
        FastClientUI.roundedRect(graphics, x, y + Math.max(1, size / 30), size, size, radius, FastClientUI.withAlpha(-16777216, Math.round((float)alpha * 0.22f)));
        FastClientUI.roundedRect(graphics, x, y, size, size, radius, FastClientUI.withAlpha(hovered ? -13616829 : -14406863, alpha));
        int inset = Math.max(1, Math.round((float)size / 69.0f));
        int innerSize = size - inset * 2;
        int innerRadius = Math.max(2, radius - inset);
        if (hovered) {
            HudOverlayScreen.drawRoundedHorizontalGradient(graphics, x + inset, y + inset, innerSize, innerSize, innerRadius, FastClientUI.withAlpha(-12958640, alpha), FastClientUI.withAlpha(-15591911, alpha));
        } else {
            FastClientUI.roundedRect(graphics, x + inset, y + inset, innerSize, innerSize, innerRadius, FastClientUI.withAlpha(-15921133, alpha));
        }
    }

    private void drawFastSettingsButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float hoverProgress, int alpha) {
        int radius = Math.max(3, Math.round((float)height * 0.09f));
        FastClientUI.roundedRect(graphics, x, y + Math.max(1, height / 30), width, height, radius, FastClientUI.withAlpha(-16777216, Math.round((float)alpha * 0.22f)));
        int border = FastClientUI.blend(-14406863, -13616829, hoverProgress);
        FastClientUI.roundedRect(graphics, x, y, width, height, radius, FastClientUI.withAlpha(border, alpha));
        int inset = Math.max(1, Math.round((float)height / 69.0f));
        int left = FastClientUI.blend(-15921133, -12958640, hoverProgress);
        int right = FastClientUI.blend(-15921133, -15591911, hoverProgress);
        HudOverlayScreen.drawRoundedHorizontalGradient(graphics, x + inset, y + inset, width - inset * 2, height - inset * 2, Math.max(2, radius - inset), FastClientUI.withAlpha(left, alpha), FastClientUI.withAlpha(right, alpha));
        Component label = FastClientFonts.body("Fast Settings");
        float scale = FastClientFonts.bodyScale();
        int iconWidth = HudOverlayScreen.centerFastSettingsIconSize();
        int labelWidth = Math.max(1, Math.round((float)this.font.width((FormattedText)label) * scale));
        int gap = Math.max(5, Math.round((float)height * 0.08f));
        int contentWidth = iconWidth + gap + labelWidth;
        int contentX = x + (width - contentWidth) / 2;
        int centerY = y + height / 2;
        int color = FastClientUI.withAlpha(-723724, alpha);
        int iconY = centerY - iconWidth / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(FAST_SETTINGS_ICON), contentX, iconY, 0.0f, 0.0f, iconWidth, iconWidth, 96, 96, 96, 96, color);
        this.drawScaledText(graphics, label, contentX + iconWidth + gap + labelWidth / 2, centerY, scale, color, false);
    }

    private void drawQuickButtonTooltip(GuiGraphicsExtractor graphics, String labelText, int centerX, int y, int alpha) {
        Component label = FastClientFonts.body(labelText);
        float scale = FastClientFonts.bodyScale();
        int textWidth = Math.round((float)this.font.width((FormattedText)label) * scale);
        Objects.requireNonNull(this.font);
        int textHeight = Math.round(9.0f * scale);
        int padX = Math.max(12, Math.round(scale * 4.0f));
        int padY = Math.max(6, Math.round(scale * 2.0f));
        int width = textWidth + padX * 2;
        int height = textHeight + padY * 2;
        int x = centerX - width / 2;
        FastClientUI.roundedRect(graphics, x, y, width, height, Math.max(3, height / 8), FastClientUI.withAlpha(-15921133, Math.round((float)alpha * 0.95f)));
        FastClientUI.outline(graphics, x, y, width, height, FastClientUI.withAlpha(-12300198, Math.round((float)alpha * 0.63f)));
        this.drawScaledText(graphics, label, centerX, y + height / 2, scale, FastClientUI.withAlpha(-723724, alpha), false);
    }

    private static void drawRoundedHorizontalGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int leftColor, int rightColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int column = 0; column < width; ++column) {
            float position = width <= 1 ? 1.0f : (float)column / (float)(width - 1);
            float blend = Math.max(0.0f, Math.min(1.0f, (position - 0.14f) / 0.6f));
            int verticalInset = HudOverlayScreen.roundedColumnInset(column, width, r);
            graphics.fill(x + column, y + verticalInset, x + column + 1, y + height - verticalInset, FastClientUI.blend(leftColor, rightColor, blend));
        }
    }

    private static int roundedColumnInset(int column, int width, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int edgeDistance = Math.min(column, width - 1 - column);
        if (edgeDistance >= radius) {
            return 0;
        }
        double circleX = (double)(radius - edgeDistance) - 0.5;
        return Math.max(0, (int)Math.ceil((double)radius - Math.sqrt(Math.max(0.0, (double)(radius * radius) - circleX * circleX))));
    }

    private void drawScaledText(GuiGraphicsExtractor graphics, Component text, int centerX, int centerY, float scale, int color, boolean shadow) {
        float textX = (float)centerX - (float)this.font.width((FormattedText)text) * scale / 2.0f;
        float f = centerY;
        Objects.requireNonNull(this.font);
        float textY = f - 9.0f * scale / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-textX, -textY);
        graphics.text(this.font, text, (int)textX, (int)textY, color, shadow);
        graphics.pose().popMatrix();
    }

    private void renderAlignmentGuides(GuiGraphicsExtractor graphics) {
        int guideColor = FastClientUI.withAlpha(-39373, 95);
        for (int x : this.activeGuidesX) {
            graphics.fill(x, 0, x + 1, this.screenHeight(), guideColor);
        }
        for (int y : this.activeGuidesY) {
            graphics.fill(0, y, this.screenWidth(), y + 1, guideColor);
        }
    }

    private void renderDraggableModule(GuiGraphicsExtractor graphics, DraggableModule dm, int mouseX, int mouseY) {
        int bw;
        int by;
        int bx;
        boolean isHovered = this.isHovered(dm, mouseX, mouseY);
        boolean isSelected = dm == this.selected;
        boolean isDragging = dm == this.dragging;
        boolean isResizing = dm == this.resizing;
        boolean isResizeHandleHovered = this.isResizeHandleHovered(dm, mouseX, mouseY);
        if (!isDragging) {
            boolean hasOffsetLabel = !dm.module.getName().equals("Block Overlay");
            int padLabel = hasOffsetLabel ? (int)(5.0f * dm.scale) : 0;
            int labelBx = dm.x - padLabel;
            int labelBy = dm.y - padLabel;
            int labelBw = dm.width;
            Component moduleName = FastClientFonts.moduleName(dm.module.getDisplayName());
            int nameW = this.font.width((FormattedText)moduleName);
            int nameX = labelBx + labelBw / 2 - nameW / 2;
            int nameY = labelBy - 12;
            nameX = Math.max(2, Math.min(this.screenWidth() - nameW - 2, nameX));
            if (nameY < 2) {
                nameY = labelBy + dm.height + padLabel + 3;
            }
            int nameAlpha = isHovered || isSelected ? 230 : 170;
            FastClientUI.roundedRect(graphics, nameX - 5, nameY - 3, nameW + 10, 14, 3, FastClientUI.withAlpha(-435153640, nameAlpha));
            graphics.text(this.font, moduleName, nameX, nameY, FastClientUI.withAlpha(-723724, nameAlpha), false);
        }
        if (isDragging) {
            int bracketColor = FastClientUI.withAlpha(1725422816, 185);
            int[] bounds = this.getEditBounds(dm);
            bx = bounds[0];
            by = bounds[1];
            bw = bounds[2];
            int bh = bounds[3];
            graphics.fill(bx, by, bx + bw, by + 1, bracketColor);
            graphics.fill(bx, by + bh - 1, bx + bw, by + bh, bracketColor);
            graphics.fill(bx, by, bx + 1, by + bh, bracketColor);
            graphics.fill(bx + bw - 1, by, bx + bw, by + bh, bracketColor);
            return;
        }
        int borderColor = isResizing ? FastClientUI.withAlpha(-39373, 210) : (isSelected ? FastClientUI.withAlpha(1725422816, 220) : (isHovered ? FastClientUI.withAlpha(1725422816, 205) : FastClientUI.withAlpha(1725422816, 165)));
        int[] bounds = this.getEditBounds(dm);
        bx = bounds[0];
        by = bounds[1];
        bw = bounds[2];
        int bh = bounds[3];
        FastClientUI.roundedRect(graphics, bx, by, bw, bh, 3, FastClientUI.withAlpha(-653586413, 58));
        graphics.fill(bx, by, bx + bw, by + 1, borderColor);
        graphics.fill(bx, by + bh - 1, bx + bw, by + bh, borderColor);
        graphics.fill(bx, by, bx + 1, by + bh, borderColor);
        graphics.fill(bx + bw - 1, by, bx + bw, by + bh, borderColor);
        int chipSize = this.getModuleActionSize(dm);
        int removeSize = this.getModuleRemoveSize(dm);
        int handleSize = this.getResizeHandleSize(dm);
        int leftX = bx + 4;
        int chipY = by + bh - chipSize - 4;
        int rightX = bx + bw - handleSize - removeSize - 4 - 4;
        int removeY = by + bh - removeSize - 4;
        boolean hasSettings = this.hasSettings(dm);
        boolean settingsHovered = hasSettings && this.isSettingsActionHovered(dm, mouseX, mouseY);
        boolean disableHovered = this.isDisableActionHovered(dm, mouseX, mouseY);
        if (hasSettings) {
            FastClientUI.roundedRect(graphics, leftX, chipY, chipSize, chipSize, 3, FastClientUI.withAlpha(settingsHovered ? -266722777 : -435153640, 210));
            this.drawScaledText(graphics, FastClientFonts.filledMaterialSymbol("\ue8b8"), leftX + chipSize / 2, chipY + chipSize / 2 + 4, 1.35f, FastClientUI.withAlpha(settingsHovered ? -723724 : -7303024, 235), false);
        }
        FastClientUI.roundedRect(graphics, rightX, removeY, removeSize, removeSize, 3, FastClientUI.withAlpha(disableHovered ? -34227 : -39373, 220));
        this.drawScaledText(graphics, FastClientFonts.filledMaterialSymbol("\ue872"), rightX + removeSize / 2, removeY + removeSize / 2 + 4, 1.25f, FastClientUI.withAlpha(-723724, 245), false);
        if (isHovered || isSelected || isResizing) {
            int handleX = bx + bw - handleSize;
            int handleY = by + bh - handleSize;
            int handleColor = isResizeHandleHovered || isResizing ? -34227 : FastClientUI.withAlpha(-39373, 180);
            graphics.fill(handleX + 2, handleY + handleSize - 2, handleX + handleSize, handleY + handleSize, handleColor);
            graphics.fill(handleX + handleSize - 2, handleY + 2, handleX + handleSize, handleY + handleSize, handleColor);
            graphics.fill(handleX + 5, handleY + handleSize - 4, handleX + handleSize - 2, handleY + handleSize - 2, handleColor);
            graphics.fill(handleX + handleSize - 4, handleY + 5, handleX + handleSize - 2, handleY + handleSize - 2, handleColor);
            graphics.fill(handleX + 8, handleY + handleSize - 6, handleX + handleSize - 4, handleY + handleSize - 4, handleColor);
            graphics.fill(handleX + handleSize - 6, handleY + 8, handleX + handleSize - 4, handleY + handleSize - 4, handleColor);
        }
    }

    private void renderConfigPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float panelAnim = this.configPanelAnimation.getValue();
        if (panelAnim < 0.01f) {
            return;
        }
        int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 160 - 10);
        int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 200));
        int panelHeight = 150;
        int currentWidth = (int)(160.0f * panelAnim);
        FastClientUI.roundedRect(graphics, panelX, panelY, currentWidth, panelHeight, 5, -234156528);
        FastClientUI.outline(graphics, panelX, panelY, currentWidth, panelHeight, 1143616571);
        graphics.fill(panelX, panelY, panelX + currentWidth, panelY + 2, -39373);
        if (panelAnim > 0.5f) {
            Component title = FastClientFonts.moduleName(this.configTarget.module.getDisplayName());
            graphics.text(this.font, title, panelX + 10, panelY + 10, -723724, false);
            graphics.text(this.font, "Quick Actions", panelX + 10, panelY + 25, -9934744, false);
            int actionY = panelY + 45;
            graphics.text(this.font, FastClientFonts.filledMaterialSymbolLabel("\uf053", "Reset Position"), panelX + 10, actionY, -7303024, false);
            graphics.text(this.font, FastClientFonts.filledMaterialSymbolLabel("\ue00f", "Center Horizontal"), panelX + 10, actionY += 22, -7303024, false);
            graphics.text(this.font, FastClientFonts.filledMaterialSymbolLabel("\ue011", "Center Vertical"), panelX + 10, actionY += 22, -7303024, false);
            graphics.text(this.font, FastClientFonts.filledMaterialSymbolLabel("\ue8b8", "Full Settings..."), panelX + 10, actionY += 28, -39373, false);
        }
    }

    private void renderPositionInfo(GuiGraphicsExtractor graphics, DraggableModule dm) {
        String posText = String.format("%.0f%%", Float.valueOf(dm.scale * 100.0f));
        int textWidth = this.font.width(posText);
        int infoX = dm.x + dm.width - textWidth - 4;
        int infoY = dm.y + dm.height + 4;
        FastClientUI.roundedRect(graphics, infoX - 5, infoY - 3, textWidth + 10, 14, 3, -435153640);
        graphics.text(this.font, posText, infoX, infoY, -39373, false);
    }

    private void renderFooter(GuiGraphicsExtractor graphics, float animProgress) {
        int alpha = (int)(animProgress * 180.0f);
        String hints = "Drag modules to reposition \u2022 Right-click for options \u2022 Right Shift to close";
        int hintsWidth = this.font.width(hints);
        graphics.text(this.font, hints, (this.screenWidth() - hintsWidth) / 2, this.screenHeight() - 14, FastClientUI.withAlpha(-7303024, alpha), false);
    }

    private boolean isHovered(DraggableModule dm, int mouseX, int mouseY) {
        int padding = 6;
        return mouseX >= dm.x - padding && mouseX <= dm.x + dm.width + padding && mouseY >= dm.y - padding && mouseY <= dm.y + dm.height + padding;
    }

    private boolean isResizeHandleHovered(DraggableModule dm, int mouseX, int mouseY) {
        if (this.isSettingsActionHovered(dm, mouseX, mouseY) || this.isDisableActionHovered(dm, mouseX, mouseY)) {
            return false;
        }
        int[] bounds = this.getEditBounds(dm);
        int bx = bounds[0];
        int by = bounds[1];
        int bw = bounds[2];
        int bh = bounds[3];
        boolean nearRightEdge = mouseX >= bx + bw - 12 && mouseX <= bx + bw + 5 && mouseY >= by + 2 && mouseY <= by + bh + 5;
        boolean nearBottomEdge = mouseY >= by + bh - 12 && mouseY <= by + bh + 5 && mouseX >= bx + this.getModuleActionSize(dm) + 6 && mouseX <= bx + bw - this.getModuleActionSize(dm) - 6;
        return nearRightEdge || nearBottomEdge;
    }

    private boolean isSettingsActionHovered(DraggableModule dm, int mouseX, int mouseY) {
        if (!this.hasSettings(dm)) {
            return false;
        }
        int[] bounds = this.getEditBounds(dm);
        int size = this.getModuleActionSize(dm);
        int x = bounds[0] + 4;
        int y = bounds[1] + bounds[3] - size - 4;
        return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
    }

    private boolean isDisableActionHovered(DraggableModule dm, int mouseX, int mouseY) {
        int[] bounds = this.getEditBounds(dm);
        int removeSize = this.getModuleRemoveSize(dm);
        int hitSize = this.getModuleRemoveHitSize(dm);
        int x = bounds[0] + bounds[2] - this.getResizeHandleSize(dm) - removeSize - 4 - 4;
        int y = bounds[1] + bounds[3] - removeSize - 4;
        int hitX = x - (hitSize - removeSize) / 2;
        int hitY = y - (hitSize - removeSize) / 2;
        return mouseX >= hitX && mouseX <= hitX + hitSize && mouseY >= hitY && mouseY <= hitY + hitSize;
    }

    private int getModuleActionSize(DraggableModule dm) {
        return Math.max(16, Math.round(18.0f * Math.max(0.9f, Math.min(1.15f, dm.scale))));
    }

    private int getModuleRemoveSize(DraggableModule dm) {
        return Math.max(16, Math.round(18.0f * Math.max(0.9f, Math.min(1.15f, dm.scale))));
    }

    private int getModuleRemoveHitSize(DraggableModule dm) {
        return Math.max(20, Math.round(22.0f * Math.max(0.9f, Math.min(1.15f, dm.scale))));
    }

    private int getResizeHandleSize(DraggableModule dm) {
        return Math.max(11, Math.round(13.0f * Math.max(0.8f, Math.min(1.15f, dm.scale))));
    }

    private int[] getEditBounds(DraggableModule dm) {
        return new int[]{dm.x - this.getEditOffsetX(dm), dm.y - this.getEditOffsetY(dm), dm.width, dm.height};
    }

    private boolean hasSettings(DraggableModule dm) {
        return !dm.module.getSettings().isEmpty();
    }

    private int getEditOffsetX(DraggableModule dm) {
        return this.hasTextPanelOffset(dm) ? Math.round(5.0f * dm.scale) : 0;
    }

    private int getEditOffsetY(DraggableModule dm) {
        return this.hasTextPanelOffset(dm) ? Math.round(4.0f * dm.scale) : 0;
    }

    private boolean hasTextPanelOffset(DraggableModule dm) {
        return !dm.module.getName().equals("Block Overlay") && !dm.module.getName().equals("Keystrokes");
    }

    private boolean isInsideBranding(double mouseX, double mouseY) {
        int centerX = this.screenWidth() / 2;
        int centerY = this.screenHeight() / 2;
        int logoY = centerY - HudOverlayScreen.scaledCenter(86);
        int btnY = logoY + HudOverlayScreen.centerLogoSize() + HudOverlayScreen.centerButtonSpacing();
        int btnX = centerX - HudOverlayScreen.centerButtonWidth() / 2;
        return mouseX >= (double)btnX && mouseX <= (double)(btnX + HudOverlayScreen.centerButtonWidth()) && mouseY >= (double)btnY && mouseY <= (double)(btnY + HudOverlayScreen.centerButtonHeight());
    }

    private String getQuickButtonClicked(double mouseX, double mouseY) {
        int centerX = this.screenWidth() / 2;
        int centerY = this.screenHeight() / 2;
        int logoY = centerY - HudOverlayScreen.scaledCenter(86);
        int btnY = logoY + HudOverlayScreen.centerLogoSize() + HudOverlayScreen.centerButtonSpacing();
        int quickY = btnY + HudOverlayScreen.centerButtonHeight() + HudOverlayScreen.scaledCenter(9);
        int iconSize = HudOverlayScreen.centerQuickButtonSize();
        int iconGap = HudOverlayScreen.scaledCenter(12);
        int totalW = iconSize * QUICK_BUTTONS.length + iconGap * (QUICK_BUTTONS.length - 1);
        int groupX = centerX - totalW / 2;
        for (int i = 0; i < QUICK_BUTTONS.length; ++i) {
            int qx = groupX + i * (iconSize + iconGap);
            if (!(mouseX >= (double)qx) || !(mouseX <= (double)(qx + iconSize)) || !(mouseY >= (double)quickY) || !(mouseY <= (double)(quickY + iconSize))) continue;
            return QUICK_BUTTONS[i][0];
        }
        return null;
    }

    private static int scaledCenter(int value) {
        return Math.round((float)value * 1.3f);
    }

    private static int centerLogoSize() {
        return HudOverlayScreen.scaledCenter(58);
    }

    private static int centerButtonWidth() {
        Minecraft mc = Minecraft.getInstance();
        float scale = FastClientFonts.bodyScale();
        Component label = FastClientFonts.body("Fast Settings");
        int contentWidth = HudOverlayScreen.centerFastSettingsIconSize() + Math.round((float)mc.font.width((FormattedText)label) * scale) + Math.max(5, Math.round((float)HudOverlayScreen.centerButtonHeight() * 0.08f));
        return Math.max(HudOverlayScreen.scaledCenter(180), contentWidth + HudOverlayScreen.scaledCenter(24));
    }

    private static int centerFastSettingsIconSize() {
        return Math.max(18, Math.round((float)HudOverlayScreen.centerButtonHeight() * 0.5f));
    }

    private static int centerButtonHeight() {
        return HudOverlayScreen.scaledCenter(30);
    }

    private static int centerButtonSpacing() {
        return HudOverlayScreen.scaledCenter(14);
    }

    private static int centerQuickButtonSize() {
        return HudOverlayScreen.scaledCenter(32);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();
        if (this.configPanelOpen && this.configTarget != null && this.configPanelAnimation.getValue() > 0.5f) {
            int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 160 - 10);
            int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 200));
            int panelHeight = 150;
            int currentWidth = (int)(160.0f * this.configPanelAnimation.getValue());
            if (mouseX >= (double)panelX && mouseX <= (double)(panelX + currentWidth) && mouseY >= (double)panelY && mouseY <= (double)(panelY + panelHeight)) {
                return this.handleConfigPanelClick((int)mouseX, (int)mouseY, panelX, panelY);
            }
            this.configPanelAnimation.animateTo(0.0f);
            this.configPanelOpen = false;
            return true;
        }
        if (button == 0) {
            String quickBtn = this.getQuickButtonClicked(mouseX, mouseY);
            if ("overlay_store".equals(quickBtn) || "overlay_cosmetics".equals(quickBtn) || "overlay_social".equals(quickBtn)) {
                SystemToast.add((ToastManager)Minecraft.getInstance().gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"FastClient"), (Component)Component.literal((String)"Coming Soon"));
                return true;
            }
            if (quickBtn != null) {
                return true;
            }
            if (this.isInsideBranding(mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen((Screen)ClickGUIScreen.getInstance());
                return true;
            }
        }
        if (button == 1) {
            for (DraggableModule dm : this.draggables) {
                if (!this.isHovered(dm, (int)mouseX, (int)mouseY)) continue;
                this.configTarget = dm;
                this.configPanelOpen = true;
                this.configPanelAnimation.animateTo(1.0f);
                return true;
            }
        }
        if (button == 0) {
            if (this.configPanelOpen) {
                this.configPanelAnimation.animateTo(0.0f);
                this.configPanelOpen = false;
            }
            for (DraggableModule dm : new ArrayList<DraggableModule>(this.draggables)) {
                if (this.hasSettings(dm) && this.isSettingsActionHovered(dm, (int)mouseX, (int)mouseY)) {
                    this.selected = dm;
                    Minecraft.getInstance().gui.setScreen((Screen)new ModuleConfigScreen(dm.module, this));
                    return true;
                }
                if (!this.isDisableActionHovered(dm, (int)mouseX, (int)mouseY)) continue;
                dm.module.setEnabled(false);
                FastClientHUDClient.getInstance().getModuleManager().saveConfig();
                this.draggables.remove(dm);
                this.selected = null;
                return true;
            }
            for (DraggableModule dm : this.draggables) {
                if (!this.isResizeHandleHovered(dm, (int)mouseX, (int)mouseY)) continue;
                this.resizing = dm;
                this.selected = dm;
                this.resizeStartX = (int)mouseX;
                this.resizeStartY = (int)mouseY;
                this.resizeStartScale = dm.scale;
                return true;
            }
            for (DraggableModule dm : this.draggables) {
                if (!this.isHovered(dm, (int)mouseX, (int)mouseY)) continue;
                this.dragging = dm;
                this.selected = dm;
                this.dragOffsetX = (int)mouseX - dm.x;
                this.dragOffsetY = (int)mouseY - dm.y;
                return true;
            }
            this.selected = null;
        }
        return super.mouseClicked(event, bl);
    }

    private boolean handleConfigPanelClick(int mouseX, int mouseY, int panelX, int panelY) {
        if (this.configTarget == null) {
            return false;
        }
        int actionY = panelY + 45;
        int actionHeight = 16;
        if (mouseY >= actionY && mouseY <= actionY + actionHeight) {
            this.configTarget.x = 10;
            this.configTarget.y = 10;
            this.configTarget.module.setHudPosition(10, 10);
            return true;
        }
        if (mouseY >= (actionY += 22) && mouseY <= actionY + actionHeight) {
            this.configTarget.x = (this.screenWidth() - this.configTarget.width) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 22) && mouseY <= actionY + actionHeight) {
            this.configTarget.y = (this.screenHeight() - this.configTarget.height) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 28) && mouseY <= actionY + actionHeight) {
            Minecraft.getInstance().gui.setScreen((Screen)new ModuleConfigScreen(this.configTarget.module, this));
            return true;
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == 0) {
            if (this.resizing != null) {
                this.resizing.module.setHudScale(this.resizing.scale);
                this.resizing = null;
                return true;
            }
            if (this.dragging != null) {
                this.dragging.module.setHudPosition(this.dragging.x, this.dragging.y);
                this.dragging = null;
                this.activeGuidesX.clear();
                this.activeGuidesY.clear();
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        if (this.resizing != null) {
            int deltaFromStart = (int)mouseX - this.resizeStartX + (int)mouseY - this.resizeStartY;
            float scaleDelta = (float)deltaFromStart / 100.0f;
            this.resizing.setScale(this.resizeStartScale + scaleDelta);
            this.resizing.module.setHudScale(this.resizing.scale);
            return true;
        }
        if (this.dragging != null) {
            int newX = (int)mouseX - this.dragOffsetX;
            int newY = (int)mouseY - this.dragOffsetY;
            this.activeGuidesX.clear();
            this.activeGuidesY.clear();
            if (this.snapToGrid) {
                newX = Math.round((float)newX / (float)this.gridSize) * this.gridSize;
                newY = Math.round((float)newY / (float)this.gridSize) * this.gridSize;
            }
            if (this.showGuides && this.snapToGrid) {
                int draggingCenterX = newX + this.dragging.width / 2;
                int draggingCenterY = newY + this.dragging.height / 2;
                int draggingRight = newX + this.dragging.width;
                int draggingBottom = newY + this.dragging.height;
                int screenCenterX = this.screenWidth() / 2;
                int screenCenterY = this.screenHeight() / 2;
                if (Math.abs(draggingCenterX - screenCenterX) < 8) {
                    newX = screenCenterX - this.dragging.width / 2;
                    this.activeGuidesX.add(screenCenterX);
                }
                if (Math.abs(draggingCenterY - screenCenterY) < 8) {
                    newY = screenCenterY - this.dragging.height / 2;
                    this.activeGuidesY.add(screenCenterY);
                }
                if (Math.abs(newX) < 8) {
                    newX = 0;
                    this.activeGuidesX.add(0);
                }
                if (Math.abs(draggingRight - this.screenWidth()) < 8) {
                    newX = this.screenWidth() - this.dragging.width;
                    this.activeGuidesX.add(this.screenWidth());
                }
                if (Math.abs(newY) < 8) {
                    newY = 0;
                    this.activeGuidesY.add(0);
                }
                if (Math.abs(draggingBottom - this.screenHeight()) < 8) {
                    newY = this.screenHeight() - this.dragging.height;
                    this.activeGuidesY.add(this.screenHeight());
                }
                for (DraggableModule other : this.draggables) {
                    if (other == this.dragging) continue;
                    int otherCenterX = other.x + other.width / 2;
                    int otherCenterY = other.y + other.height / 2;
                    int otherRight = other.x + other.width;
                    int otherBottom = other.y + other.height;
                    if (Math.abs(newX - other.x) < 8) {
                        newX = other.x;
                        this.activeGuidesX.add(other.x);
                    }
                    if (Math.abs(draggingRight - otherRight) < 8) {
                        newX = otherRight - this.dragging.width;
                        this.activeGuidesX.add(otherRight);
                    }
                    if (Math.abs(newY - other.y) < 8) {
                        newY = other.y;
                        this.activeGuidesY.add(other.y);
                    }
                    if (Math.abs(draggingBottom - otherBottom) < 8) {
                        newY = otherBottom - this.dragging.height;
                        this.activeGuidesY.add(otherBottom);
                    }
                    if (Math.abs(draggingCenterX - otherCenterX) < 8) {
                        newX = otherCenterX - this.dragging.width / 2;
                        this.activeGuidesX.add(otherCenterX);
                    }
                    if (Math.abs(draggingCenterY - otherCenterY) >= 8) continue;
                    newY = otherCenterY - this.dragging.height / 2;
                    this.activeGuidesY.add(otherCenterY);
                }
            }
            int visualOffsetX = this.getEditOffsetX(this.dragging);
            int visualOffsetY = this.getEditOffsetY(this.dragging);
            this.dragging.x = Math.max(visualOffsetX, Math.min(this.screenWidth() - this.dragging.width + visualOffsetX, newX));
            this.dragging.y = Math.max(visualOffsetY, Math.min(this.screenHeight() - this.dragging.height + visualOffsetY, newY));
            this.dragging.module.setHudPosition(this.dragging.x, this.dragging.y);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        if (this.isShiftKeyDown()) {
            this.gridSize = vertAmount > 0.0 ? Math.min(50, this.gridSize + 5) : Math.max(5, this.gridSize - 5);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 344) {
            this.onClose();
            return true;
        }
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        if (keyCode == 83) {
            this.snapToGrid = !this.snapToGrid;
            return true;
        }
        if (keyCode == 65) {
            this.showGuides = !this.showGuides;
            return true;
        }
        if ((keyCode == 61 || keyCode == 334) && this.selected != null) {
            float step = this.isShiftKeyDown() ? 0.5f : 0.1f;
            this.selected.setScale(this.selected.scale + step);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if ((keyCode == 45 || keyCode == 333) && this.selected != null) {
            float step = this.isShiftKeyDown() ? 0.5f : 0.1f;
            this.selected.setScale(this.selected.scale - step);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if ((keyCode == 48 || keyCode == 320) && this.selected != null) {
            this.selected.setScale(2.0f);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if (this.selected != null) {
            int nudge = this.isShiftKeyDown() ? 10 : 1;
            boolean moved = false;
            switch (keyCode) {
                case 262: {
                    this.selected.x = Math.min(this.screenWidth() - this.selected.width, this.selected.x + nudge);
                    moved = true;
                    break;
                }
                case 263: {
                    this.selected.x = Math.max(0, this.selected.x - nudge);
                    moved = true;
                    break;
                }
                case 264: {
                    this.selected.y = Math.min(this.screenHeight() - this.selected.height, this.selected.y + nudge);
                    moved = true;
                    break;
                }
                case 265: {
                    this.selected.y = Math.max(0, this.selected.y - nudge);
                    moved = true;
                }
            }
            if (moved) {
                if (this.snapToGrid) {
                    this.selected.x = Math.round((float)this.selected.x / (float)this.gridSize) * this.gridSize;
                    this.selected.y = Math.round((float)this.selected.y / (float)this.gridSize) * this.gridSize;
                }
                this.selected.module.setHudPosition(this.selected.x, this.selected.y);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    public void onClose() {
        for (DraggableModule dm : this.draggables) {
            dm.module.setHudPosition(dm.x, dm.y);
            dm.module.setHudScale(dm.scale);
        }
        FastClientHUDClient.getInstance().getModuleManager().saveConfig();
        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }

    private static class DraggableModule {
        Module module;
        int x;
        int y;
        int width;
        int height;
        float scale;

        DraggableModule(Module module, int x, int y) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.scale = module.getHudScale();
            this.updateDimensions();
        }

        void updateDimensions() {
            this.width = (int)((float)this.module.getHudWidth() * this.scale);
            this.height = (int)((float)this.module.getHudHeight() * this.scale);
        }

        void setScale(float newScale) {
            this.scale = Math.max(0.5f, Math.min(10.0f, newScale));
            this.updateDimensions();
        }
    }
}

