package com.vayuclient.hud.gui.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.widgets.ModuleCard;
import com.vayuclient.hud.gui.widgets.ScrollablePanel;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ClickGUIScreen extends Screen {
    private static ClickGUIScreen INSTANCE;
    private static Category savedCategory;
    private static Map<Category, Double> savedScrollPositions;
    private static double savedAllScrollPosition;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private Category selectedCategory;
    private ScrollablePanel modulePanel;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private ActionDialog actionDialog = ActionDialog.NONE;
    private int layoutDisplayWidth = -1;
    private int layoutDisplayHeight = -1;

    private AnimationUtils.Animation openAnimation;
    private final float[] tabHoverProgress = new float[Category.values().length + 1];
    private long lastUpdate = System.currentTimeMillis();
    private long cursorBlinkTime = 0L;

    public ClickGUIScreen() {
        super(Component.literal("VayuHUD"));
        INSTANCE = this;
    }

    public static ClickGUIScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClickGUIScreen();
        }
        return INSTANCE;
    }

    @Override
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

        this.panelWidth = Math.min(Math.max(340, displayWidth - 40), 580);
        this.panelHeight = Math.min(Math.max(220, displayHeight - 48), 340);
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = (displayHeight - this.panelHeight) / 2;

        int modulePanelY = this.panelY + 68;
        int modulePanelHeight = this.panelHeight - 76;
        this.modulePanel = new ScrollablePanel(this.panelX + 12, modulePanelY, this.panelWidth - 24, modulePanelHeight);
        this.updateModuleList();

        if (!restoreSavedScroll) {
            this.modulePanel.setScrollOffset(currentScroll);
        } else if (this.selectedCategory == null) {
            this.modulePanel.setScrollOffset(savedAllScrollPosition);
        } else if (savedScrollPositions.containsKey(this.selectedCategory)) {
            this.modulePanel.setScrollOffset(savedScrollPositions.get(this.selectedCategory));
        }

        this.layoutDisplayWidth = displayWidth;
        this.layoutDisplayHeight = displayHeight;
    }

    private void ensurePhysicalLayoutCurrent() {
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        if (displayWidth != this.layoutDisplayWidth || displayHeight != this.layoutDisplayHeight) {
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
                } else if (category != null && savedScrollPositions.containsKey(category)) {
                    this.modulePanel.setScrollOffset(savedScrollPositions.get(category));
                } else {
                    this.modulePanel.setScrollOffset(0.0);
                }
            }
        }
    }

    private void updateModuleList() {
        List<ModuleCard> cards = new ArrayList<>();
        List<Module> modules = this.selectedCategory == null
            ? VayuHUDClient.getInstance().getModuleManager().getModules()
            : VayuHUDClient.getInstance().getModuleManager().getModulesByCategory(this.selectedCategory);

        for (Module module : modules) {
            ModuleCard card = new ModuleCard(module, 0, 0, ModuleCard.CARD_WIDTH, ModuleCard.CARD_HEIGHT, m -> {
                VayuHUDClient.getInstance().getModuleManager().toggleModule(m);
                VayuHUDClient.getInstance().getModuleManager().saveConfig();
            }, m -> this.minecraft.gui.setScreen(new ModuleConfigScreen(m, this)));

            if (!card.matchesSearch(this.searchQuery)) continue;
            cards.add(card);
        }

        if (this.modulePanel != null) {
            this.modulePanel.setCards(cards);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), 0x99050A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.ensurePhysicalLayoutCurrent();
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);

        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);

        long now = System.currentTimeMillis();
        float dt = (float) (now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;

        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int) ((float) this.panelY + (1.0f - animProgress) * 12.0f);
        int alpha = (int) (animProgress * 255.0f);

        // Panel Body
        VayuHUDUI.roundedRect(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0xE60A111A, alpha));
        VayuHUDUI.roundedOutline(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0x3338BDF8, alpha));

        // Header Toolbar
        this.drawHeader(graphics, this.panelX, animatedPanelY, this.panelWidth, pxMouseX, pxMouseY, alpha);

        // Category Tabs & Search
        this.drawCategoryTabsAndSearch(graphics, this.panelX, animatedPanelY, this.panelWidth, pxMouseX, pxMouseY, alpha, dt);

        // Module Card Panel
        if (this.modulePanel != null) {
            int originalY = this.modulePanel.getY();
            this.modulePanel.setY(originalY + (animatedPanelY - this.panelY));
            this.modulePanel.render(graphics, pxMouseX, pxMouseY);
            this.modulePanel.setY(originalY);
        }

        // Action Dialogs (Reset / Presets)
        if (this.actionDialog != ActionDialog.NONE) {
            this.drawActionDialog(graphics, pxMouseX, pxMouseY, alpha);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha) {
        int headY = y + 10;

        // Left Emblem + Title
        int iconX = x + 12;
        int iconY = headY;
        VayuHUDUI.roundedRect(graphics, iconX, iconY, 20, 20, 4, 0xFF0284C7);
        VayuHUDUI.drawModsVector(graphics, iconX + 10, iconY + 10, 10, 0xFFFFFFFF);

        graphics.text(this.font, "VAYU HUD", iconX + 26, headY + 3, VayuTheme.PRIMARY, true);
        graphics.text(this.font, "MODULE MANAGER", iconX + 26, headY + 12, VayuTheme.TEXT_MUTED, false);

        // Right Action Toolbar Buttons
        int btnH = 20;
        int curX = x + w - 12;

        // 1. Close Button
        int closeW = 20;
        curX -= closeW;
        boolean ch = mouseX >= curX && mouseX <= curX + closeW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, closeW, btnH, 4, ch ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, curX, headY, closeW, btnH, 4, ch ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(graphics, curX + closeW / 2, headY + btnH / 2, 8, 0xFFFFFFFF);

        // 2. Installed Mods Manager Button
        int modsBtnW = 86;
        curX -= (modsBtnW + 6);
        boolean modH = mouseX >= curX && mouseX <= curX + modsBtnW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, modsBtnW, btnH, 4, modH ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, curX, headY, modsBtnW, btnH, 4, modH ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "Installed Mods", curX + 8, headY + 6, modH ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);

        // 3. Canvas Editor Button
        int canvasW = 56;
        curX -= (canvasW + 6);
        boolean canvasH = mouseX >= curX && mouseX <= curX + canvasW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, canvasW, btnH, 4, canvasH ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, curX, headY, canvasW, btnH, 4, canvasH ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "Canvas", curX + 10, headY + 6, canvasH ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);

        // 4. Presets Button
        int presetW = 50;
        curX -= (presetW + 6);
        boolean presH = mouseX >= curX && mouseX <= curX + presetW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, presetW, btnH, 4, presH ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, curX, headY, presetW, btnH, 4, presH ? VayuTheme.PRIMARY : 0x2A38BDF8);
        graphics.text(this.font, "Presets", curX + 8, headY + 6, presH ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);

        // 5. Reset Button
        int resetW = 44;
        curX -= (resetW + 6);
        boolean resH = mouseX >= curX && mouseX <= curX + resetW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, resetW, btnH, 4, resH ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, curX, headY, resetW, btnH, 4, resH ? VayuTheme.PRIMARY : 0x2A38BDF8);
        graphics.text(this.font, "Reset", curX + 9, headY + 6, resH ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);
    }

    private void drawCategoryTabsAndSearch(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha, float dt) {
        int tabY = y + 38;
        int tabX = x + 12;
        int tabH = 20;

        Category[] categories = Category.values();
        int totalTabs = categories.length + 1;

        for (int i = 0; i < totalTabs; ++i) {
            String label = (i == 0) ? "All" : categories[i - 1].getDisplayName();
            int tabW = this.font.width(label) + 14;
            boolean isSelected = (i == 0 && this.selectedCategory == null) || (i > 0 && this.selectedCategory == categories[i - 1]);
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            this.tabHoverProgress[i] = AnimationUtils.smoothDelta(this.tabHoverProgress[i], isHovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
            int bg = isSelected ? VayuTheme.PRIMARY : VayuHUDUI.blend(0xD00A111A, 0xE6141E2D, this.tabHoverProgress[i]);
            VayuHUDUI.roundedRect(graphics, tabX, tabY, tabW, tabH, 4, VayuHUDUI.withAlpha(bg, alpha));
            VayuHUDUI.roundedOutline(graphics, tabX, tabY, tabW, tabH, 4, isSelected ? VayuTheme.PRIMARY : 0x2238BDF8);

            int textColor = isSelected ? 0xFF050A10 : (isHovered ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED);
            graphics.text(this.font, label, tabX + 7, tabY + 6, textColor, isSelected);

            tabX += tabW + 5;
        }

        // Search Bar on the Right
        int searchW = Math.min(130, Math.max(80, (x + w - 12) - tabX));
        if (searchW > 60) {
            int searchX = x + w - 12 - searchW;
            int searchBg = this.searchFocused ? 0xE6141E2D : 0xD00A111A;
            VayuHUDUI.roundedRect(graphics, searchX, tabY, searchW, tabH, 4, searchBg);
            VayuHUDUI.roundedOutline(graphics, searchX, tabY, searchW, tabH, 4, this.searchFocused ? VayuTheme.PRIMARY : 0x2238BDF8);

            int textX = searchX + 8;
            int textY = tabY + 6;

            if (this.searchQuery.isEmpty() && !this.searchFocused) {
                graphics.text(this.font, "Search...", textX, textY, VayuTheme.TEXT_MUTED, false);
            } else {
                graphics.text(this.font, this.searchQuery, textX, textY, 0xFFFFFFFF, false);
                if (this.searchFocused && (System.currentTimeMillis() - this.cursorBlinkTime) % 1000L < 500L) {
                    int cursorX = textX + this.font.width(this.searchQuery);
                    graphics.fill(cursorX, textY, cursorX + 1, textY + 8, 0xFF38BDF8);
                }
            }
        }
    }

    private void drawActionDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int alpha) {
        int dialogW = Math.min(360, this.panelWidth - 32);
        int dialogH = this.actionDialog == ActionDialog.PRESETS ? 180 : 110;
        int x = this.panelX + (this.panelWidth - dialogW) / 2;
        int y = this.panelY + (this.panelHeight - dialogH) / 2;

        VayuHUDUI.roundedRect(graphics, x, y, dialogW, dialogH, 8, 0xF2050A10);
        VayuHUDUI.roundedOutline(graphics, x, y, dialogW, dialogH, 8, 0x5538BDF8);

        if (this.actionDialog == ActionDialog.RESET) {
            graphics.text(this.font, "Reset All Mod Settings?", x + 16, y + 16, VayuTheme.PRIMARY, true);
            graphics.text(this.font, "This will restore default positions and toggles.", x + 16, y + 30, VayuTheme.TEXT_MUTED, false);

            int btnW = (dialogW - 40) / 2;
            int btnH = 26;
            int btnY = y + 60;
            int resetX = x + 16;
            int cancelX = resetX + btnW + 8;

            boolean rh = mouseX >= resetX && mouseX <= resetX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            VayuHUDUI.roundedRect(graphics, resetX, btnY, btnW, btnH, 4, rh ? 0xFFDC2626 : 0xD01F1212);
            VayuHUDUI.roundedOutline(graphics, resetX, btnY, btnW, btnH, 4, rh ? 0xFFEF4444 : 0x44EF4444);
            graphics.text(this.font, "Reset Defaults", resetX + 12, btnY + 9, 0xFFFFFFFF, true);

            boolean ch = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            VayuHUDUI.roundedRect(graphics, cancelX, btnY, btnW, btnH, 4, ch ? 0xE6141E2D : 0xD00A111A);
            VayuHUDUI.roundedOutline(graphics, cancelX, btnY, btnW, btnH, 4, ch ? VayuTheme.PRIMARY : 0x2A38BDF8);
            graphics.text(this.font, "Cancel", cancelX + 16, btnY + 9, VayuTheme.TEXT_MUTED, false);
        } else if (this.actionDialog == ActionDialog.PRESETS) {
            graphics.text(this.font, "Configuration Presets", x + 16, y + 14, VayuTheme.PRIMARY, true);
            int rowY = y + 34;
            int rowW = dialogW - 32;

            for (ModuleManager.HudPreset preset : VayuHUDClient.getInstance().getModuleManager().getHudPresets()) {
                boolean hovered = mouseX >= (x + 16) && mouseX <= (x + 16 + rowW) && mouseY >= rowY && mouseY <= rowY + 34;
                VayuHUDUI.roundedRect(graphics, x + 16, rowY, rowW, 34, 4, hovered ? 0xE6141E2D : 0xD00A111A);
                VayuHUDUI.roundedOutline(graphics, x + 16, rowY, rowW, 34, 4, hovered ? VayuTheme.PRIMARY : 0x2238BDF8);

                graphics.text(this.font, preset.name(), x + 24, rowY + 6, hovered ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);
                graphics.text(this.font, preset.description(), x + 24, rowY + 18, VayuTheme.TEXT_MUTED, false);

                int applyW = 46;
                int applyH = 20;
                int applyX = x + 16 + rowW - applyW - 8;
                int applyY = rowY + 7;
                VayuHUDUI.roundedRect(graphics, applyX, applyY, applyW, applyH, 4, hovered ? 0xFF0284C7 : 0xD00F1722);
                graphics.text(this.font, "Apply", applyX + 9, applyY + 6, 0xFFFFFFFF, true);

                rowY += 40;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        this.ensurePhysicalLayoutCurrent();
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();

        if (this.actionDialog != ActionDialog.NONE) {
            return this.handleActionDialogClick(mouseX, mouseY);
        }

        int headY = this.panelY + 10;
        int btnH = 20;
        int curX = this.panelX + this.panelWidth - 12;

        // 1. Close Button
        int closeW = 20;
        curX -= closeW;
        if (mouseX >= curX && mouseX <= curX + closeW && mouseY >= headY && mouseY <= headY + btnH) {
            this.closeToGame();
            return true;
        }

        // 2. Installed Mods Manager Button
        int modsBtnW = 86;
        curX -= (modsBtnW + 6);
        if (mouseX >= curX && mouseX <= curX + modsBtnW && mouseY >= headY && mouseY <= headY + btnH) {
            this.minecraft.gui.setScreen(new VayuModsScreen(this));
            return true;
        }

        // 3. Canvas Editor Button
        int canvasW = 56;
        curX -= (canvasW + 6);
        if (mouseX >= curX && mouseX <= curX + canvasW && mouseY >= headY && mouseY <= headY + btnH) {
            this.minecraft.gui.setScreen(new HudOverlayScreen());
            return true;
        }

        // 4. Presets Button
        int presetW = 50;
        curX -= (presetW + 6);
        if (mouseX >= curX && mouseX <= curX + presetW && mouseY >= headY && mouseY <= headY + btnH) {
            this.actionDialog = ActionDialog.PRESETS;
            return true;
        }

        // 5. Reset Button
        int resetW = 44;
        curX -= (resetW + 6);
        if (mouseX >= curX && mouseX <= curX + resetW && mouseY >= headY && mouseY <= headY + btnH) {
            this.actionDialog = ActionDialog.RESET;
            return true;
        }

        // Category Tabs
        int tabY = this.panelY + 38;
        int tabX = this.panelX + 12;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length + 1; ++i) {
            String label = (i == 0) ? "All" : categories[i - 1].getDisplayName();
            int tabW = this.font.width(label) + 14;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 20) {
                this.selectCategory(i == 0 ? null : categories[i - 1]);
                return true;
            }
            tabX += tabW + 5;
        }

        // Search Bar Focus
        int searchW = Math.min(130, Math.max(80, (this.panelX + this.panelWidth - 12) - tabX));
        if (searchW > 60) {
            int searchX = this.panelX + this.panelWidth - 12 - searchW;
            if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= tabY && mouseY <= tabY + 20) {
                this.searchFocused = true;
                this.cursorBlinkTime = System.currentTimeMillis();
                return true;
            }
        }
        this.searchFocused = false;

        if (this.modulePanel != null && this.modulePanel.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl)) {
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    private boolean handleActionDialogClick(double mouseX, double mouseY) {
        int dialogW = Math.min(360, this.panelWidth - 32);
        int dialogH = this.actionDialog == ActionDialog.PRESETS ? 180 : 110;
        int x = this.panelX + (this.panelWidth - dialogW) / 2;
        int y = this.panelY + (this.panelHeight - dialogH) / 2;

        ModuleManager manager = VayuHUDClient.getInstance().getModuleManager();

        if (this.actionDialog == ActionDialog.RESET) {
            int btnW = (dialogW - 40) / 2;
            int btnH = 26;
            int btnY = y + 60;
            int resetX = x + 16;
            int cancelX = resetX + btnW + 8;

            if (mouseY >= btnY && mouseY <= btnY + btnH) {
                if (mouseX >= resetX && mouseX <= resetX + btnW) {
                    manager.resetToDefaults();
                    this.actionDialog = ActionDialog.NONE;
                    this.updateModuleList();
                    return true;
                }
                if (mouseX >= cancelX && mouseX <= cancelX + btnW) {
                    this.actionDialog = ActionDialog.NONE;
                    return true;
                }
            }
        } else if (this.actionDialog == ActionDialog.PRESETS) {
            int rowY = y + 34;
            int rowW = dialogW - 32;

            for (ModuleManager.HudPreset preset : manager.getHudPresets()) {
                if (mouseX >= (x + 16) && mouseX <= (x + 16 + rowW) && mouseY >= rowY && mouseY <= rowY + 34) {
                    manager.applyHudPreset(preset.id());
                    this.actionDialog = ActionDialog.NONE;
                    this.updateModuleList();
                    return true;
                }
                rowY += 40;
            }
        }

        if (mouseX < (double) x || mouseX > (double) (x + dialogW) || mouseY < (double) y || mouseY > (double) (y + dialogH)) {
            this.actionDialog = ActionDialog.NONE;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.ensurePhysicalLayoutCurrent();
        if (this.modulePanel != null && this.modulePanel.mouseScrolled(DisplaySpace.mouseX(mouseX), DisplaySpace.mouseY(mouseY), horizAmount, vertAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.actionDialog != ActionDialog.NONE && keyCode == 256) {
            this.actionDialog = ActionDialog.NONE;
            return true;
        }
        if (keyCode == 344) { // Right Shift
            this.minecraft.gui.setScreen(new HudOverlayScreen());
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
            if (keyCode == 259) { // Backspace
                if (!this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    this.updateModuleList();
                }
                return true;
            }
            if (keyCode == 257) { // Enter
                this.searchFocused = false;
                return true;
            }
            return true;
        }
        if (keyCode == 256) { // Escape
            this.minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (this.searchFocused && c >= ' ') {
            this.searchQuery = this.searchQuery + c;
            this.cursorBlinkTime = System.currentTimeMillis();
            this.updateModuleList();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
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
        savedScrollPositions = new HashMap<>();
        savedAllScrollPosition = 0.0;
    }

    private enum ActionDialog {
        NONE,
        RESET,
        PRESETS
    }
}
