/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 */
package net.fastclient.hud.gui.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fastclient.hud.FastClientHUDClient;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientFonts;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.gui.screens.HudOverlayScreen;
import net.fastclient.hud.gui.screens.ModuleConfigScreen;
import net.fastclient.hud.gui.widgets.ModuleCard;
import net.fastclient.hud.gui.widgets.ScrollablePanel;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ClickGUIScreen
extends Screen {
    private static ClickGUIScreen INSTANCE;
    private ScrollablePanel modulePanel;
    private static Category savedCategory;
    private static final Map<Category, Double> savedScrollPositions;
    private static double savedAllScrollPosition;
    private Category selectedCategory = null;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private ActionDialog actionDialog = ActionDialog.NONE;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int layoutDisplayWidth = -1;
    private int layoutDisplayHeight = -1;
    private float layoutTextScale = -1.0f;
    private static final int HEADER_ROW_GAP = 12;
    private static final int HEADER_PANEL_GAP = 12;
    private static final int NAV_BUTTON_H = 52;
    private static final int NAV_BUTTON_W = 58;
    private static final int CATEGORY_BAR_HEIGHT = 62;
    private static final int MODULE_GRID_TOP_GAP = 12;
    private static final int CONTENT_PAD = 24;
    private static final int CATEGORY_TAB_HEIGHT = 41;
    private static final int TAB_SPACING = 10;
    private static final int SEARCH_WIDTH = 320;
    private static final int CLOSE_BUTTON_SIZE = 48;
    private static final float MATERIAL_SYMBOL_Y_OFFSET = 2.0f;
    private static final String[] HEADER_NAV_SYMBOLS;
    private static final int PRESET_ROW_H = 58;
    private static final int PRESET_ROW_GAP = 10;
    private static final int PRESET_CONTENT_TOP = 76;
    private static final int RESET_DIALOG_W = 420;
    private static final int RESET_DIALOG_H = 156;
    private static final int RESET_DIALOG_PAD = 24;
    private static final int RESET_BUTTON_TOP = 76;
    private static final int RESET_BUTTON_H = 54;
    private static final int RESET_BUTTON_GAP = 18;
    private AnimationUtils.Animation openAnimation;
    private final float[] tabHoverProgress = new float[Category.values().length + 1];
    private long lastUpdate = System.currentTimeMillis();
    private long cursorBlinkTime = 0L;

    public ClickGUIScreen() {
        super((Component)Component.literal((String)"FastClientHUD"));
        INSTANCE = this;
    }

    public static ClickGUIScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClickGUIScreen();
        }
        return INSTANCE;
    }

    protected void init() {
        this.selectedCategory = savedCategory;
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 200L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        this.rebuildPhysicalLayout(this.modulePanel == null);
    }

    private void rebuildPhysicalLayout(boolean restoreSavedScroll) {
        double currentScroll = this.modulePanel != null ? this.modulePanel.getScrollOffset() : 0.0;
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        int preferredPanelWidth = Math.max(1, (int)((float)displayWidth * 0.6f));
        int requiredPanelWidth = this.minimumPanelWidthForControls();
        this.panelWidth = Math.min(Math.max(1, displayWidth - 24), Math.max(preferredPanelWidth, requiredPanelWidth));
        this.panelHeight = Math.max(1, (int)((float)displayHeight * 0.7f));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = Math.max(88, (displayHeight - this.panelHeight) / 2 + 54);
        int modulePanelY = this.panelY + 62 + 12;
        int modulePanelHeight = this.panelHeight - 62 - 12 - 24;
        this.modulePanel = new ScrollablePanel(this.panelX + 24, modulePanelY, this.panelWidth - 48, modulePanelHeight);
        this.updateModuleList();
        if (!restoreSavedScroll) {
            this.modulePanel.setScrollOffset(currentScroll);
        } else if (this.selectedCategory == null) {
            this.modulePanel.setScrollOffset(savedAllScrollPosition);
        } else if (savedScrollPositions.containsKey((Object)this.selectedCategory)) {
            this.modulePanel.setScrollOffset(savedScrollPositions.get((Object)this.selectedCategory));
        }
        this.layoutDisplayWidth = displayWidth;
        this.layoutDisplayHeight = displayHeight;
        this.layoutTextScale = FastClientFonts.bodyScale();
    }

    private void ensurePhysicalLayoutCurrent() {
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        float textScale = FastClientFonts.bodyScale();
        if (displayWidth != this.layoutDisplayWidth || displayHeight != this.layoutDisplayHeight || Float.compare(textScale, this.layoutTextScale) != 0) {
            this.rebuildPhysicalLayout(false);
        }
    }

    private void selectCategory(Category category) {
        if (this.selectedCategory != category) {
            if (this.modulePanel != null) {
                if (this.selectedCategory == null) {
                    savedAllScrollPosition = this.modulePanel.getScrollOffset();
                } else {
                    savedScrollPositions.put(this.selectedCategory, this.modulePanel.getScrollOffset());
                }
            }
            this.selectedCategory = category;
            savedCategory = category;
            this.updateModuleList();
            if (this.modulePanel != null) {
                if (category == null && savedAllScrollPosition > 0.0) {
                    this.modulePanel.setScrollOffset(savedAllScrollPosition);
                } else if (category != null && savedScrollPositions.containsKey((Object)category)) {
                    this.modulePanel.setScrollOffset(savedScrollPositions.get((Object)category));
                } else {
                    this.modulePanel.setScrollOffset(0.0);
                }
            }
        }
    }

    private void updateModuleList() {
        ArrayList<ModuleCard> cards = new ArrayList<ModuleCard>();
        List<Module> modules = this.selectedCategory == null ? FastClientHUDClient.getInstance().getModuleManager().getModules() : FastClientHUDClient.getInstance().getModuleManager().getModulesByCategory(this.selectedCategory);
        for (Module module : modules) {
            ModuleCard card = new ModuleCard(module, 0, 0, 232, 186, m -> {
                FastClientHUDClient.getInstance().getModuleManager().toggleModule((Module)m);
                FastClientHUDClient.getInstance().getModuleManager().saveConfig();
            }, m -> this.minecraft.gui.setScreen((Screen)new ModuleConfigScreen((Module)m, this)));
            if (!card.matchesSearch(this.searchQuery)) continue;
            cards.add(card);
        }
        if (this.modulePanel != null) {
            this.modulePanel.setCards(cards);
        }
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), FastClientUI.withAlpha(-16777216, 55));
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.ensurePhysicalLayoutCurrent();
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int)((float)this.panelY + (1.0f - animProgress) * 18.0f);
        int alpha = (int)(animProgress * 255.0f);
        this.drawPanelBackground(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, alpha);
        this.drawHeader(graphics, alpha, pxMouseX, pxMouseY);
        this.drawCategoryTabs(graphics, this.panelX, animatedPanelY, pxMouseX, pxMouseY, alpha, dt);
        this.drawSearchBar(graphics, this.panelX, animatedPanelY, this.panelWidth, pxMouseX, pxMouseY, alpha);
        if (this.modulePanel != null) {
            int originalY = this.modulePanel.getY();
            this.modulePanel.setY(originalY + (animatedPanelY - this.panelY));
            this.modulePanel.render(graphics, pxMouseX, pxMouseY);
            this.modulePanel.setY(originalY);
        }
        this.drawVersionFooter(graphics, animatedPanelY, alpha);
        if (this.actionDialog != ActionDialog.NONE) {
            this.drawActionDialog(graphics, pxMouseX, pxMouseY, alpha);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        graphics.fill(x, y, x + w, y + h, FastClientUI.withAlpha(-234156528, alpha));
    }

    private void drawVersionFooter(GuiGraphicsExtractor graphics, int panelY, int alpha) {
        int footerY = panelY + this.panelHeight + 12;
        this.drawCenteredComponent(graphics, FastClientFonts.body("Fastclient 26.2 (release/ca786cd3)"), this.panelX + this.panelWidth / 2, footerY + this.uiLineHeight() / 2, FastClientFonts.bodyScale(), FastClientUI.withAlpha(-9934744, alpha));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int alpha, int mouseX, int mouseY) {
        int x = this.panelX;
        int y = this.getHeaderNavY();
        int navButtonH = 52;
        int navButtonW = this.getNavButtonWidth();
        int navGap = this.getNavGap();
        int modLogoW = 144;
        FastClientUI.roundedRect(graphics, x, y, modLogoW, navButtonH, 6, FastClientUI.withAlpha(-39373, alpha));
        this.drawIconLabelCentered(graphics, "\ue871", "MOD MENU", x, y, modLogoW, navButtonH, 1.5f, 8, FastClientUI.withAlpha(-723724, alpha));
        int iconX = x + modLogoW + navGap;
        for (int i = 0; i < HEADER_NAV_SYMBOLS.length; ++i) {
            this.drawNavIcon(graphics, iconX + (navButtonW + navGap) * i, y, navButtonW, navButtonH, HEADER_NAV_SYMBOLS[i], alpha, mouseX, mouseY);
        }
        int closeSize = 48;
        int closeX = this.panelX + this.panelWidth - closeSize;
        int closeY = y + (navButtonH - closeSize) / 2;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
        int closeFillAlpha = Math.round((float)alpha * (closeHovered ? 0.25f : 0.15f));
        FastClientUI.roundedRect(graphics, closeX, closeY, closeSize, closeSize, 3, FastClientUI.withAlpha(-39373, closeFillAlpha));
        FastClientUI.roundedOutline(graphics, closeX, closeY, closeSize, closeSize, 3, FastClientUI.withAlpha(-39373, alpha));
        this.drawCenteredComponent(graphics, FastClientFonts.strong("\u00d7"), closeX + closeSize / 2, closeY + closeSize / 2, 2.0f, FastClientUI.withAlpha(closeHovered ? -34227 : -39373, alpha));
    }

    private void drawNavIcon(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String symbol, int alpha, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        FastClientUI.borderedRoundedRect(graphics, x, y, width, height, 6, FastClientUI.withAlpha(hovered ? -266722777 : -435153640, alpha), FastClientUI.withAlpha(hovered ? -39373 : 1143616571, alpha));
        this.drawCenteredMaterialSymbol(graphics, symbol, x + width / 2, y + height / 2 + 2, 2.0f, FastClientUI.withAlpha(hovered ? -723724 : -7303024, alpha));
    }

    private void drawCategoryTabs(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, int alpha, float dt) {
        int tabX = x + 24;
        int tabY = y + 16;
        int tabSpacing = this.getTabSpacing();
        Category[] categories = Category.values();
        int totalTabs = categories.length + 1;
        for (int i = 0; i < totalTabs; ++i) {
            String label = this.categoryLabel(i, categories);
            int tabWidth = this.getCategoryTabWidth(label);
            boolean isSelected = i == 0 && this.selectedCategory == null || i > 0 && this.selectedCategory == categories[i - 1];
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + 41;
            this.tabHoverProgress[i] = AnimationUtils.smoothDelta(this.tabHoverProgress[i], isHovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
            int background = isSelected ? -39373 : FastClientUI.blend(-435153640, -266722777, this.tabHoverProgress[i]);
            FastClientUI.roundedRect(graphics, tabX, tabY, tabWidth, 41, 3, FastClientUI.withAlpha(background, alpha));
            int textColor = FastClientUI.withAlpha(isSelected ? -723724 : FastClientUI.blend(-7303024, -723724, this.tabHoverProgress[i]), alpha);
            this.drawUiText(graphics, label, tabX + (tabWidth - this.uiTextWidth(label)) / 2, tabY + (41 - this.uiLineHeight()) / 2 + 1, textColor);
            tabX += tabWidth + tabSpacing;
        }
    }

    private String categoryLabel(int index, Category[] categories) {
        return index == 0 ? "All" : categories[index - 1].getDisplayName();
    }

    private int getCategoryTabWidth(String label) {
        return Math.max(62, this.uiTextWidth(label) + 24);
    }

    private int getActionButtonWidth(String label) {
        return Math.max(62, this.uiTextWidth(label) + 24);
    }

    private void drawSearchBar(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha) {
        TopControlsLayout layout = this.getTopControlsLayout(x, y, w);
        int searchHeight = 41;
        this.drawActionButton(graphics, layout.resetX, layout.searchY, layout.resetW, searchHeight, "Reset", this.actionDialog == ActionDialog.RESET, mouseX, mouseY, alpha);
        this.drawActionButton(graphics, layout.presetX, layout.searchY, layout.presetW, searchHeight, "Presets", this.actionDialog == ActionDialog.PRESETS, mouseX, mouseY, alpha);
        int searchBg = this.searchFocused ? FastClientUI.withAlpha(-266722777, alpha) : FastClientUI.withAlpha(-435153640, alpha);
        FastClientUI.borderedRoundedRect(graphics, layout.searchX, layout.searchY, layout.searchW, searchHeight, 3, searchBg, this.searchFocused ? FastClientUI.withAlpha(-39373, alpha) : FastClientUI.withAlpha(1143616571, alpha));
        this.drawCenteredMaterialSymbol(graphics, "\uef7a", layout.searchX + 14, layout.searchY + searchHeight / 2, 1.0f, FastClientUI.withAlpha(-7303024, alpha));
        int textX = layout.searchX + 27;
        int textY = layout.searchY + (searchHeight - this.uiLineHeight()) / 2 + 1;
        if (this.searchQuery.isEmpty() && !this.searchFocused) {
            this.drawUiText(graphics, "Search", textX, textY, FastClientUI.withAlpha(-7303024, alpha));
        } else {
            long time;
            Object displayText = this.searchQuery;
            int maxTextWidth = layout.searchW - 38;
            if (this.uiTextWidth((String)displayText) > maxTextWidth) {
                while (this.uiTextWidth((String)displayText + "...") > maxTextWidth && ((String)displayText).length() > 0) {
                    displayText = ((String)displayText).substring(1);
                }
                displayText = "..." + (String)displayText;
            }
            this.drawUiText(graphics, (String)displayText, textX, textY, FastClientUI.withAlpha(-723724, alpha));
            if (this.searchFocused && ((time = System.currentTimeMillis()) - this.cursorBlinkTime) % 1000L < 500L) {
                int cursorX = textX + this.uiTextWidth(this.searchQuery);
                cursorX = Math.min(cursorX, layout.searchX + layout.searchW - 8);
                graphics.fill(cursorX, textY - 2, cursorX + 1, textY + this.uiLineHeight() + 2, FastClientUI.withAlpha(-723724, alpha));
            }
        }
    }

    private void drawActionButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean selected, int mouseX, int mouseY, int alpha) {
        boolean hovered;
        boolean bl = hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int background = selected ? -39373 : (hovered ? -266722777 : -435153640);
        FastClientUI.roundedRect(graphics, x, y, w, h, 3, FastClientUI.withAlpha(background, alpha));
        this.drawUiText(graphics, label, x + (w - this.uiTextWidth(label)) / 2, y + (h - this.uiLineHeight()) / 2 + 1, FastClientUI.withAlpha(-723724, alpha));
    }

    private void drawActionDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int alpha) {
        int dialogW = this.getActionDialogWidth();
        int dialogH = this.getActionDialogHeight();
        int x = this.getActionDialogX(dialogW);
        int y = this.getActionDialogY(dialogH);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, FastClientUI.withAlpha(-16777216, 100));
        graphics.fill(x, y, x + dialogW, y + dialogH, FastClientUI.withAlpha(-234156528, alpha));
        FastClientUI.outline(graphics, x, y, dialogW, dialogH, FastClientUI.withAlpha(1143616571, alpha));
        if (this.actionDialog == ActionDialog.RESET) {
            this.drawUiText(graphics, "RESET ALL", x + 24, y + 20, -723724);
            String resetDescription = this.fitText("Choose how much of your HUD setup should be reset.", dialogW - 48);
            this.drawUiText(graphics, resetDescription, x + 24, y + 43, -7303024);
            int buttonW = (dialogW - 48 - 18) / 2;
            int leftX = x + 24;
            int rightX = leftX + buttonW + 18;
            this.drawDialogButton(graphics, leftX, y + 76, buttonW, 54, "DEFAULTS", "Restore original setup", mouseX, mouseY, alpha, false);
            this.drawDialogButton(graphics, rightX, y + 76, buttonW, 54, "REMOVE ALL", "Disable every module", mouseX, mouseY, alpha, true);
        } else {
            List<ModuleManager.HudPreset> presets = FastClientHUDClient.getInstance().getModuleManager().getHudPresets();
            int headerX = x + 24;
            this.drawUiText(graphics, "HUD PRESETS", headerX, y + 14, FastClientUI.withAlpha(-723724, alpha));
            String countText = presets.size() + " layouts";
            int countX = x + dialogW - 24 - this.uiTextWidth(countText);
            String headerDescription = this.fitText("Apply a full-screen layout tuned for a specific play style.", countX - headerX - 20);
            this.drawUiText(graphics, headerDescription, headerX, y + 40, FastClientUI.withAlpha(-7303024, alpha));
            this.drawUiText(graphics, countText, countX, y + 40, FastClientUI.withAlpha(-9934744, alpha));
            int rowY = y + 76;
            int rowX = x + 24;
            int rowW = dialogW - 48;
            for (int i = 0; i < presets.size(); ++i) {
                ModuleManager.HudPreset preset = presets.get(i);
                boolean hovered = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + 58;
                int bg = hovered ? -266722777 : -435153640;
                graphics.fill(rowX, rowY, rowX + rowW, rowY + 58, FastClientUI.withAlpha(bg, alpha));
                FastClientUI.outline(graphics, rowX, rowY, rowW, 58, FastClientUI.withAlpha(hovered ? 1717331565 : 1143616571, alpha));
                int accent = this.presetAccent(i);
                graphics.fill(rowX + 1, rowY + 1, rowX + 5, rowY + 58 - 1, FastClientUI.withAlpha(accent, alpha));
                String number = String.format("%02d", i + 1);
                int badgeX = rowX + 14;
                int badgeY = rowY + 14;
                int badgeW = 38;
                int badgeH = 30;
                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, FastClientUI.withAlpha(-15722982, alpha));
                this.drawUiText(graphics, number, badgeX + (badgeW - this.uiTextWidth(number)) / 2, badgeY + (badgeH - this.uiLineHeight()) / 2 + 1, FastClientUI.withAlpha(accent, alpha));
                String applyText = "APPLY";
                int applyW = Math.max(88, this.uiTextWidth(applyText) + 28);
                int applyH = 32;
                int applyX = rowX + rowW - applyW - 14;
                int applyY = rowY + (58 - applyH) / 2;
                int textX = rowX + 64;
                this.drawUiText(graphics, preset.name(), textX, rowY + 8, FastClientUI.withAlpha(hovered ? -723724 : -7303024, alpha));
                String description = this.fitText(preset.description(), applyX - textX - 12);
                this.drawUiText(graphics, description, textX, rowY + 34, FastClientUI.withAlpha(-9934744, alpha));
                int applyBg = hovered ? -39373 : -266722777;
                graphics.fill(applyX, applyY, applyX + applyW, applyY + applyH, FastClientUI.withAlpha(applyBg, alpha));
                this.drawUiText(graphics, applyText, applyX + (applyW - this.uiTextWidth(applyText)) / 2, applyY + (applyH - this.uiLineHeight()) / 2 + 1, FastClientUI.withAlpha(hovered ? -723724 : -7303024, alpha));
                rowY += 68;
            }
        }
        int closeSize = 22;
        int closeMargin = this.actionDialog == ActionDialog.RESET ? 14 : 10;
        boolean closeHovered = mouseX >= x + dialogW - closeSize - closeMargin && mouseX <= x + dialogW - closeMargin && mouseY >= y + closeMargin && mouseY <= y + closeSize + closeMargin;
        FastClientUI.roundedRect(graphics, x + dialogW - closeSize - closeMargin, y + closeMargin, closeSize, closeSize, 3, closeHovered ? -266722777 : -435153640);
        this.drawCenteredMaterialSymbol(graphics, "\ue5cd", x + dialogW - closeSize / 2 - closeMargin, y + closeMargin + closeSize / 2, 1.1f, closeHovered ? -723724 : -7303024);
    }

    private int presetAccent(int index) {
        int[] accents = new int[]{-39373, -11020659, -9193473, -11930, -3704321, -2564638};
        return accents[index % accents.length];
    }

    private void drawDialogButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title, String subtitle, int mouseX, int mouseY, int alpha, boolean danger) {
        boolean hovered;
        boolean bl = hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = danger && hovered ? -39373 : (hovered ? -266722777 : -435153640);
        graphics.fill(x, y, x + w, y + h, FastClientUI.withAlpha(bg, alpha));
        int textPadding = 14;
        this.drawUiText(graphics, title, x + textPadding, y + 11, FastClientUI.withAlpha(-723724, alpha));
        this.drawUiText(graphics, this.fitText(subtitle, w - textPadding * 2), x + textPadding, y + 31, FastClientUI.withAlpha(danger ? -723724 : -9934744, alpha));
    }

    private int getSearchWidth(int availableWidth) {
        return Math.min(320, Math.max(88, availableWidth / 5));
    }

    private int getHeaderNavY() {
        return this.panelY - 12 - 52;
    }

    private int getTabSpacing() {
        return this.panelWidth < 1050 ? 5 : 10;
    }

    private int getNavButtonWidth() {
        return 58;
    }

    private int getNavGap() {
        return 12;
    }

    private int getTabsTotalWidth() {
        int tabSpacing = this.getTabSpacing();
        Category[] categories = Category.values();
        int total = tabSpacing * categories.length;
        for (int i = 0; i < categories.length + 1; ++i) {
            total += this.getCategoryTabWidth(this.categoryLabel(i, categories));
        }
        return total;
    }

    private int minimumPanelWidthForControls() {
        int categoryCount = Category.values().length + 1;
        int tabsWidth = 0;
        Category[] categories = Category.values();
        for (int i = 0; i < categoryCount; ++i) {
            tabsWidth += this.getCategoryTabWidth(this.categoryLabel(i, categories));
        }
        int actionsWidth = this.getActionButtonWidth("Reset") + this.getActionButtonWidth("Presets") + 20;
        return 48 + (tabsWidth += 10 * (categoryCount - 1)) + 12 + actionsWidth + 88;
    }

    private TopControlsLayout getTopControlsLayout(int x, int y, int w) {
        int contentPad = 24;
        int resetW = this.getActionButtonWidth("Reset");
        int presetW = this.getActionButtonWidth("Presets");
        int actionGap = this.getTabSpacing();
        int rightEdge = x + w - contentPad;
        int searchWidth = this.getSearchWidth(w);
        int searchY = y + 16;
        int searchX = rightEdge - searchWidth;
        int actionTotalW = resetW + presetW + actionGap;
        int resetX = searchX - actionTotalW - actionGap;
        int minActionX = x + contentPad + this.getTabsTotalWidth() + 12;
        if (resetX < minActionX) {
            int shortage = minActionX - resetX;
            searchWidth = Math.max(88, searchWidth - shortage);
            searchX = rightEdge - searchWidth;
            resetX = searchX - actionTotalW - actionGap;
        }
        int presetX = resetX + resetW + actionGap;
        return new TopControlsLayout(searchX, searchY, searchWidth, resetX, resetW, presetX, presetW);
    }

    private void drawCenteredComponent(GuiGraphicsExtractor graphics, Component text, int centerX, int centerY, float scale, int color) {
        float textX = (float)centerX - (float)this.font.width((FormattedText)text) * scale / 2.0f;
        float f = centerY;
        Objects.requireNonNull(this.font);
        float textY = f - 9.0f * scale / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-textX, -textY);
        graphics.text(this.font, text, Math.round(textX), Math.round(textY), color, false);
        graphics.pose().popMatrix();
    }

    private void drawIconLabelCentered(GuiGraphicsExtractor graphics, String symbol, String label, int x, int y, int w, int h, float iconScale, int gap, int color) {
        Component icon = FastClientFonts.filledMaterialSymbol(symbol);
        Component labelText = FastClientFonts.strong(label);
        int iconWidth = Math.max(1, Math.round((float)this.font.width((FormattedText)icon) * iconScale));
        float labelScale = FastClientFonts.strongScale();
        int labelWidth = Math.round((float)this.font.width((FormattedText)labelText) * labelScale);
        int contentWidth = iconWidth + gap + labelWidth;
        int contentX = x + (w - contentWidth) / 2;
        this.drawCenteredMaterialSymbol(graphics, symbol, contentX + iconWidth / 2, y + h / 2, iconScale, color);
        this.drawCenteredComponent(graphics, labelText, contentX + iconWidth + gap + labelWidth / 2, y + h / 2, labelScale, color);
    }

    private void drawCenteredMaterialSymbol(GuiGraphicsExtractor graphics, String symbol, int centerX, int centerY, float scale, int color) {
        int correctedCenterY = centerY + Math.round(2.0f * scale);
        this.drawCenteredComponent(graphics, FastClientFonts.filledMaterialSymbol(symbol), centerX, correctedCenterY, scale, color);
    }

    private void drawUiText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = FastClientFonts.bodyScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, FastClientFonts.body(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private int uiTextWidth(String text) {
        return Math.round((float)this.font.width((FormattedText)FastClientFonts.body(text)) * FastClientFonts.bodyScale());
    }

    private int uiLineHeight() {
        Objects.requireNonNull(this.font);
        return Math.round(9.0f * FastClientFonts.bodyScale());
    }

    private String fitText(String text, int maxWidth) {
        if (this.uiTextWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && this.uiTextWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private int getActionDialogWidth() {
        if (this.actionDialog == ActionDialog.PRESETS) {
            return Math.min(620, Math.max(480, this.panelWidth - 96));
        }
        int widestText = Math.max(this.uiTextWidth("Restore original setup"), this.uiTextWidth("Disable every module"));
        int buttonW = widestText + 28;
        int requiredWidth = 66 + buttonW * 2;
        return Math.min(Math.max(420, requiredWidth), Math.max(320, this.panelWidth - 48));
    }

    private int getActionDialogHeight() {
        if (this.actionDialog == ActionDialog.PRESETS) {
            int count = FastClientHUDClient.getInstance().getModuleManager().getHudPresets().size();
            return 76 + count * 58 + Math.max(0, count - 1) * 10 + 24;
        }
        return 156;
    }

    private int getActionDialogX(int dialogW) {
        return this.panelX + (this.panelWidth - dialogW) / 2;
    }

    private int getActionDialogY(int dialogH) {
        return Math.max(this.panelY + 34, this.panelY + (this.panelHeight - dialogH) / 2);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        this.ensurePhysicalLayoutCurrent();
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();
        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int)((float)this.panelY + (1.0f - animProgress) * 18.0f);
        if (this.actionDialog != ActionDialog.NONE) {
            return this.handleActionDialogClick(mouseX, mouseY);
        }
        int closeSize = 48;
        int closeX = this.panelX + this.panelWidth - closeSize;
        int closeY = this.getHeaderNavY() + (52 - closeSize) / 2;
        if (mouseX >= (double)closeX && mouseX <= (double)(closeX + closeSize) && mouseY >= (double)closeY && mouseY <= (double)(closeY + closeSize)) {
            this.closeToGame();
            return true;
        }
        TopControlsLayout controls = this.getTopControlsLayout(this.panelX, animatedPanelY, this.panelWidth);
        if (button == 0 && mouseY >= (double)controls.searchY && mouseY <= (double)(controls.searchY + 41)) {
            if (mouseX >= (double)controls.resetX && mouseX <= (double)(controls.resetX + controls.resetW)) {
                this.actionDialog = ActionDialog.RESET;
                this.searchFocused = false;
                return true;
            }
            if (mouseX >= (double)controls.presetX && mouseX <= (double)(controls.presetX + controls.presetW)) {
                this.actionDialog = ActionDialog.PRESETS;
                this.searchFocused = false;
                return true;
            }
        }
        if (mouseX >= (double)controls.searchX && mouseX <= (double)(controls.searchX + controls.searchW) && mouseY >= (double)controls.searchY && mouseY <= (double)(controls.searchY + 41)) {
            this.searchFocused = true;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }
        this.searchFocused = false;
        if (button == 0) {
            int tabX = this.panelX + 24;
            int tabY = animatedPanelY + 16;
            Category[] categories = Category.values();
            int tabSpacing = this.getTabSpacing();
            for (int i = 0; i < categories.length + 1; ++i) {
                int tabWidth = this.getCategoryTabWidth(this.categoryLabel(i, categories));
                if (mouseX >= (double)tabX && mouseX <= (double)(tabX + tabWidth) && mouseY >= (double)tabY && mouseY <= (double)(tabY + 41)) {
                    if (i == 0) {
                        this.selectCategory(null);
                    } else {
                        this.selectCategory(categories[i - 1]);
                    }
                    return true;
                }
                tabX += tabWidth + tabSpacing;
            }
        }
        if (this.modulePanel != null && this.modulePanel.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl)) {
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    private boolean handleActionDialogClick(double mouseX, double mouseY) {
        int closeMargin;
        int dialogW = this.getActionDialogWidth();
        int dialogH = this.getActionDialogHeight();
        int x = this.getActionDialogX(dialogW);
        int y = this.getActionDialogY(dialogH);
        int closeSize = 22;
        int n = closeMargin = this.actionDialog == ActionDialog.RESET ? 14 : 10;
        if (mouseX >= (double)(x + dialogW - closeSize - closeMargin) && mouseX <= (double)(x + dialogW - closeMargin) && mouseY >= (double)(y + closeMargin) && mouseY <= (double)(y + closeSize + closeMargin)) {
            this.actionDialog = ActionDialog.NONE;
            return true;
        }
        ModuleManager manager = FastClientHUDClient.getInstance().getModuleManager();
        if (this.actionDialog == ActionDialog.RESET) {
            int buttonW = (dialogW - 48 - 18) / 2;
            int leftX = x + 24;
            int rightX = leftX + buttonW + 18;
            if (mouseY >= (double)(y + 76) && mouseY <= (double)(y + 76 + 54)) {
                if (mouseX >= (double)leftX && mouseX <= (double)(leftX + buttonW)) {
                    manager.resetToDefaults();
                    this.actionDialog = ActionDialog.NONE;
                    this.updateModuleList();
                    return true;
                }
                if (mouseX >= (double)rightX && mouseX <= (double)(rightX + buttonW)) {
                    manager.resetAllRemoved();
                    this.actionDialog = ActionDialog.NONE;
                    this.updateModuleList();
                    return true;
                }
            }
        } else if (this.actionDialog == ActionDialog.PRESETS) {
            int rowY = y + 76;
            int rowX = x + 24;
            int rowW = dialogW - 48;
            for (ModuleManager.HudPreset preset : manager.getHudPresets()) {
                if (mouseX >= (double)rowX && mouseX <= (double)(rowX + rowW) && mouseY >= (double)rowY && mouseY <= (double)(rowY + 58)) {
                    manager.applyHudPreset(preset.id());
                    this.actionDialog = ActionDialog.NONE;
                    this.updateModuleList();
                    return true;
                }
                rowY += 68;
            }
        }
        if (mouseX < (double)x || mouseX > (double)(x + dialogW) || mouseY < (double)y || mouseY > (double)(y + dialogH)) {
            this.actionDialog = ActionDialog.NONE;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.ensurePhysicalLayoutCurrent();
        if (this.modulePanel != null && this.modulePanel.mouseScrolled(DisplaySpace.mouseX(mouseX), DisplaySpace.mouseY(mouseY), horizAmount, vertAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.actionDialog != ActionDialog.NONE && keyCode == 256) {
            this.actionDialog = ActionDialog.NONE;
            return true;
        }
        if (keyCode == 344) {
            Minecraft.getInstance().gui.setScreen((Screen)new HudOverlayScreen());
            return true;
        }
        if (this.searchFocused) {
            if (keyCode == 256) {
                if (!this.searchQuery.isEmpty()) {
                    this.searchQuery = "";
                    this.updateModuleList();
                } else {
                    this.searchFocused = false;
                }
                return true;
            }
            if (keyCode == 259) {
                if (!this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    this.updateModuleList();
                }
                return true;
            }
            if (keyCode == 257) {
                this.searchFocused = false;
                return true;
            }
            return true;
        }
        if (keyCode == 256) {
            Minecraft.getInstance().gui.setScreen((Screen)new HudOverlayScreen());
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        char c;
        if (this.searchFocused && (c = (char)event.codepoint()) >= ' ') {
            this.searchQuery = this.searchQuery + c;
            this.cursorBlinkTime = System.currentTimeMillis();
            this.updateModuleList();
            return true;
        }
        return super.charTyped(event);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        this.saveScrollState();
        super.onClose();
    }

    private void closeToGame() {
        this.saveScrollState();
        Minecraft.getInstance().gui.setScreen(null);
    }

    private void saveScrollState() {
        if (this.modulePanel != null) {
            if (this.selectedCategory == null) {
                savedAllScrollPosition = this.modulePanel.getScrollOffset();
            } else {
                savedScrollPositions.put(this.selectedCategory, this.modulePanel.getScrollOffset());
            }
        }
    }

    static {
        savedCategory = null;
        savedScrollPositions = new HashMap<Category, Double>();
        savedAllScrollPosition = 0.0;
        HEADER_NAV_SYMBOLS = new String[]{"\ue8b8", "\ue30c", "\uf19e", "\ue9e4", "\ue8af", "\uf02e"};
    }

    private static enum ActionDialog {
        NONE,
        RESET,
        PRESETS;

    }

    private static final class TopControlsLayout {
        final int searchX;
        final int searchY;
        final int searchW;
        final int resetX;
        final int resetW;
        final int presetX;
        final int presetW;

        TopControlsLayout(int searchX, int searchY, int searchW, int resetX, int resetW, int presetX, int presetW) {
            this.searchX = searchX;
            this.searchY = searchY;
            this.searchW = searchW;
            this.resetX = resetX;
            this.resetW = resetW;
            this.presetX = presetX;
            this.presetW = presetW;
        }
    }
}

