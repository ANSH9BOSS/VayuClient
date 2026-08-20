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
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 */
package net.fastclient.hud.gui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.render.AnimationUtils;
import net.fastclient.hud.store.CosmeticCategory;
import net.fastclient.hud.store.StoreCosmetic;
import net.fastclient.hud.store.StoreManager;
import net.fastclient.hud.store.StoreState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class StoreScreen
extends Screen {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/title-fastclient-logo.png");
    private static final Identifier DIAMOND_TEXTURE = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/title/diamond.png");
    private static final Identifier SEARCH_ICON = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/title/fc_search.png");
    private static final int CLOSE_BUTTON_SIZE = 30;
    private static final int HEADER_ROW_GAP = 6;
    private static final int HEADER_PANEL_GAP = 6;
    private static final int TAB_HEIGHT = 30;
    private static final int CATEGORY_BAR_H = 126;
    private static final int CATEGORY_TAB_MAX_SIZE = 46;
    private static final int CATEGORY_TAB_MIN_SIZE = 34;
    private static final int CATEGORY_TAB_GAP = 6;
    private static final int CONTENT_PAD = 24;
    private static final int CONTROL_GAP = 8;
    private static final int CLAIM_BUTTON_W = 118;
    private static final int COIN_PILL_W = 112;
    private static final int OWNED_TOGGLE_W = 108;
    private static final int REMOVE_ALL_W = 104;
    private static final int CARD_W = 178;
    private static final int CARD_H = 218;
    private static final int CARD_SPACING = 12;
    private static final int GRID_PADDING = 10;
    private static final int SCROLLBAR_W = 5;
    private final Screen previousScreen;
    private final StoreManager storeManager;
    private final StoreState state;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private CosmeticCategory selectedCategory = CosmeticCategory.FEATURED;
    private String searchQuery = "";
    private boolean ownedOnly = false;
    private boolean searchFocused = false;
    private AnimationUtils.Animation openAnimation;
    private final float[] tabHoverProgress = new float[CosmeticCategory.values().length];
    private long lastUpdate = System.currentTimeMillis();
    private long cursorBlinkTime = 0L;
    private String statusMessage = "";
    private long statusMessageUntil = 0L;
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private double maxScroll = 0.0;
    private boolean scrollbarDragging = false;
    private double dragStartY = 0.0;
    private double dragStartScroll = 0.0;
    private final List<StoreCosmetic> visibleItems = new ArrayList<StoreCosmetic>();
    private final List<int[]> cardRects = new ArrayList<int[]>();

    public StoreScreen(Screen previousScreen) {
        super((Component)Component.literal((String)"FastClient Store"));
        this.previousScreen = previousScreen;
        this.storeManager = StoreManager.getInstance();
        this.state = this.storeManager.getState();
        this.selectedCategory = this.parseSavedCategory(this.state.getLastSelectedCategory());
    }

    protected void init() {
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        this.panelWidth = Math.min(Math.max(760, (int)((float)displayWidth * 0.7f)), Math.max(1, displayWidth - 48));
        this.panelHeight = Math.min(Math.max(480, (int)((float)displayHeight * 0.74f)), Math.max(1, displayHeight - 96));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = Math.max(74, (displayHeight - this.panelHeight) / 2 + 18);
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 200L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        this.targetScrollOffset = this.state.getScrollOffset();
        this.scrollOffset = this.state.getScrollOffset();
    }

    private CosmeticCategory parseSavedCategory(String savedCategory) {
        return CosmeticCategory.fromSerialized(savedCategory);
    }

    private int gridX() {
        return this.panelX + 4;
    }

    private int gridY() {
        return this.panelY + 126;
    }

    private int gridW() {
        return this.panelWidth - 8 - 5 - 6;
    }

    private int gridH() {
        return this.panelHeight - 126 - 12;
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), FastClientUI.withAlpha(-16777216, 55));
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        float animProgress = this.openAnimation.getValue();
        int alpha = (int)(animProgress * 255.0f);
        this.drawPanelBackground(graphics, alpha);
        this.drawHeader(graphics, alpha, pxMouseX, pxMouseY);
        this.drawCategoryTabs(graphics, pxMouseX, pxMouseY, alpha, dt);
        this.drawSearchBar(graphics, alpha, pxMouseX, pxMouseY);
        this.drawStatus(graphics, alpha);
        this.drawProductGrid(graphics, pxMouseX, pxMouseY, alpha, dt);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int alpha) {
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, FastClientUI.withAlpha(-234156528, alpha));
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 126, FastClientUI.withAlpha(-15986412, Math.min(alpha, 230)));
        graphics.fill(this.panelX, this.gridY() - 1, this.panelX + this.panelWidth, this.gridY(), FastClientUI.withAlpha(1143616571, alpha));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int alpha, int mouseX, int mouseY) {
        boolean claimHovered;
        int headerH = 32;
        int headerY = this.getHeaderNavY();
        int brandY = this.getHeaderBrandY();
        int logoH = 24;
        int logoW = Math.round((float)logoH * 3.167702f);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)this.panelX, (float)(brandY + 4));
        graphics.pose().scale((float)logoW / 510.0f, (float)logoH / 161.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(LOGO_TEXTURE), 0, 0, 0.0f, 0.0f, 510, 161, 510, 161, FastClientUI.withAlpha(-1, alpha));
        graphics.pose().popMatrix();
        int storeBtnW = this.panelWidth < 1050 ? 152 : 168;
        graphics.fill(this.panelX, headerY, this.panelX + storeBtnW, headerY + headerH, FastClientUI.withAlpha(-39373, alpha));
        graphics.text(this.font, "Cosmetic Store", this.panelX + 14, headerY + 12, FastClientUI.withAlpha(-723724, alpha), true);
        int coinX = this.panelX + storeBtnW + 8;
        graphics.fill(coinX, headerY, coinX + 112, headerY + headerH, FastClientUI.withAlpha(-435153640, alpha));
        int iconSize = 18;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(DIAMOND_TEXTURE), coinX + 11, headerY + 7, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize, FastClientUI.withAlpha(-13312, alpha));
        String coinText = String.valueOf(this.state.getCoinBalance());
        graphics.text(this.font, coinText, coinX + 36, headerY + 12, FastClientUI.withAlpha(-723724, alpha), true);
        int claimX = coinX + 112 + 8;
        boolean canClaim = this.state.canClaimDailyCoins();
        boolean bl = claimHovered = mouseX >= claimX && mouseX <= claimX + 118 && mouseY >= headerY && mouseY <= headerY + headerH;
        int claimBg = canClaim ? FastClientUI.withAlpha(claimHovered ? -34227 : -39373, alpha) : FastClientUI.withAlpha(claimHovered ? -266722777 : -435153640, alpha);
        graphics.fill(claimX, headerY, claimX + 118, headerY + headerH, claimBg);
        String claimText = canClaim ? "Claim +" + this.storeManager.getDailyCoinClaimAmount() : "Claimed";
        graphics.text(this.font, claimText, claimX + (118 - this.font.width(claimText)) / 2, headerY + 12, FastClientUI.withAlpha(canClaim ? -723724 : -9934744, alpha), true);
        int closeX = this.panelX + this.panelWidth - 30;
        int closeY = headerY + (headerH - 30) / 2;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 30 && mouseY >= closeY && mouseY <= closeY + 30;
        graphics.fill(closeX, closeY, closeX + 30, closeY + 30, FastClientUI.withAlpha(closeHovered ? -266722777 : -435153640, alpha));
        int n = closeX + (30 - this.font.width("X")) / 2;
        Objects.requireNonNull(this.font);
        graphics.text(this.font, "X", n, closeY + (30 - 9) / 2 + 1, FastClientUI.withAlpha(closeHovered ? -723724 : -7303024, alpha), true);
    }

    private void drawCategoryTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int alpha, float dt) {
        CosmeticCategory[] categories = CosmeticCategory.values();
        int tabSize = this.getCategoryTabSize();
        int tabGap = this.getCategoryTabGap();
        int tabY = this.getCategoryTabsY();
        int tabX = this.getCategoryTabsStartX(tabSize, tabGap);
        CosmeticCategory labelCategory = this.selectedCategory;
        for (int i = 0; i < categories.length; ++i) {
            boolean isHovered;
            CosmeticCategory cat = categories[i];
            boolean isSelected = cat == this.selectedCategory;
            boolean bl = isHovered = mouseX >= tabX && mouseX <= tabX + tabSize && mouseY >= tabY && mouseY <= tabY + tabSize;
            if (isHovered) {
                labelCategory = cat;
            }
            this.tabHoverProgress[i] = AnimationUtils.smoothDelta(this.tabHoverProgress[i], isHovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
            boolean drawHoverTexture = isSelected || this.tabHoverProgress[i] > 0.2f;
            Identifier categoryIcon = DisplaySpace.texture(this.categoryIcon(cat, drawHoverTexture));
            graphics.blit(RenderPipelines.GUI_TEXTURED, categoryIcon, tabX, tabY, 0.0f, 0.0f, tabSize, tabSize, tabSize, tabSize, FastClientUI.withAlpha(-1, alpha));
            if (isSelected) {
                graphics.fill(tabX + 4, tabY + tabSize - 3, tabX + tabSize - 4, tabY + tabSize - 1, FastClientUI.withAlpha(-34227, alpha));
            } else if (isHovered) {
                FastClientUI.outline(graphics, tabX, tabY, tabSize, tabSize, FastClientUI.withAlpha(1143616571, alpha));
            }
            tabX += tabSize + tabGap;
        }
        this.drawSelectedCategoryLabel(graphics, labelCategory, this.getCategoryLabelY(tabSize), alpha);
    }

    private void drawSearchBar(GuiGraphicsExtractor graphics, int alpha, int mouseX, int mouseY) {
        boolean ownedHovered;
        StoreControlsLayout layout = this.getStoreControlsLayout();
        int searchBarY = layout.searchY;
        int searchBarX = layout.searchX;
        int searchH = 30;
        int searchW = layout.searchW;
        int searchBg = this.searchFocused ? FastClientUI.withAlpha(-266722777, alpha) : FastClientUI.withAlpha(-435153640, alpha);
        graphics.fill(searchBarX, searchBarY, searchBarX + searchW, searchBarY + searchH, searchBg);
        FastClientUI.outline(graphics, searchBarX, searchBarY, searchW, searchH, this.searchFocused ? FastClientUI.withAlpha(-39373, alpha) : FastClientUI.withAlpha(1143616571, alpha));
        int iconSize = 14;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(SEARCH_ICON), searchBarX + 10, searchBarY + 8, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize, FastClientUI.withAlpha(-1, alpha));
        int textX = searchBarX + 32;
        int textY = searchBarY + 11;
        if (this.searchQuery.isEmpty() && !this.searchFocused) {
            graphics.text(this.font, "Search cosmetics...", textX, textY, FastClientUI.withAlpha(-7303024, alpha), false);
        } else {
            long time;
            String displayText = this.fitText(this.searchQuery, searchW - 44);
            graphics.text(this.font, displayText, textX, textY, FastClientUI.withAlpha(-723724, alpha), false);
            if (this.searchFocused && ((time = System.currentTimeMillis()) - this.cursorBlinkTime) % 1000L < 500L) {
                int cursorX = textX + this.font.width(this.searchQuery);
                cursorX = Math.min(cursorX, searchBarX + searchW - 8);
                graphics.fill(cursorX, textY - 2, cursorX + 1, textY + 14, FastClientUI.withAlpha(-723724, alpha));
            }
        }
        boolean bl = ownedHovered = mouseX >= layout.ownedX && mouseX <= layout.ownedX + 108 && mouseY >= searchBarY && mouseY <= searchBarY + searchH;
        int ownedBg = this.ownedOnly ? FastClientUI.withAlpha(-15511009, alpha) : FastClientUI.withAlpha(ownedHovered ? -266722777 : -435153640, alpha);
        graphics.fill(layout.ownedX, searchBarY, layout.ownedX + 108, searchBarY + searchH, ownedBg);
        String ownedText = this.ownedOnly ? "Owned ON" : "Owned";
        graphics.text(this.font, ownedText, layout.ownedX + (108 - this.font.width(ownedText)) / 2, searchBarY + 11, FastClientUI.withAlpha(this.ownedOnly ? -723724 : -7303024, alpha), false);
        boolean hasEquipped = !this.state.getEquippedItemIds().isEmpty();
        boolean removeHovered = mouseX >= layout.removeAllX && mouseX <= layout.removeAllX + 104 && mouseY >= searchBarY && mouseY <= searchBarY + searchH;
        int removeBg = FastClientUI.withAlpha(hasEquipped && removeHovered ? -39373 : -435153640, alpha);
        graphics.fill(layout.removeAllX, searchBarY, layout.removeAllX + 104, searchBarY + searchH, removeBg);
        String removeText = "Remove All";
        graphics.text(this.font, removeText, layout.removeAllX + (104 - this.font.width(removeText)) / 2, searchBarY + 11, FastClientUI.withAlpha(hasEquipped && removeHovered ? -723724 : -7303024, alpha), false);
    }

    private void drawStatus(GuiGraphicsExtractor graphics, int alpha) {
        if (this.statusMessage.isEmpty() || System.currentTimeMillis() > this.statusMessageUntil) {
            return;
        }
        StoreControlsLayout layout = this.getStoreControlsLayout();
        int statusX = layout.searchX + layout.searchW + 12;
        int statusY = layout.searchY + 11;
        int maxW = layout.ownedX - statusX - 12;
        if (maxW <= 40) {
            return;
        }
        String message = this.fitText(this.statusMessage, maxW);
        graphics.text(this.font, message, statusX, statusY, FastClientUI.withAlpha(-7303024, alpha), false);
    }

    private int getHeaderNavY() {
        return this.panelY - 6 - 32;
    }

    private int getHeaderBrandY() {
        return this.getHeaderNavY() - 6 - 32;
    }

    private void drawSelectedCategoryLabel(GuiGraphicsExtractor graphics, CosmeticCategory category, int y, int alpha) {
        String label = category.getDisplayName().toUpperCase(Locale.ROOT);
        int count = category == CosmeticCategory.OWNED ? this.storeManager.getOwnedCosmetics().size() : this.storeManager.getCosmeticsByCategory(category).size();
        String countText = count + (count == 1 ? " item" : " items");
        if (category == CosmeticCategory.NAMETAGS) {
            int equippedTags = this.storeManager.getEquippedChatTags().size();
            countText = countText + "  /  " + equippedTags + "/3 active";
        }
        int x = this.panelX + 24;
        graphics.text(this.font, label, x, y, FastClientUI.withAlpha(-723724, alpha), true);
        graphics.text(this.font, countText, x + this.font.width(label) + 10, y, FastClientUI.withAlpha(-7303024, alpha), false);
    }

    private int getCategoryTabsY() {
        return this.panelY + 14;
    }

    private int getCategoryLabelY(int tabSize) {
        return this.getCategoryTabsY() + tabSize + 8;
    }

    private int getCategoryTabGap() {
        return this.panelWidth < 720 ? 4 : 6;
    }

    private int getCategoryTabSize() {
        CosmeticCategory[] categories = CosmeticCategory.values();
        int gap = this.getCategoryTabGap();
        int available = this.panelWidth - 48;
        int size = (available - Math.max(0, categories.length - 1) * gap) / Math.max(1, categories.length);
        return Math.max(34, Math.min(46, size));
    }

    private int getCategoryTabsStartX(int tabSize, int tabGap) {
        return this.panelX + 24;
    }

    private Identifier categoryIcon(CosmeticCategory category, boolean hover) {
        String suffix = hover ? "_hover" : "";
        return Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)("textures/gui/store/categories/" + category.getIconName() + suffix + ".png"));
    }

    private int getSearchWidth(int availableWidth) {
        return Math.min(390, Math.max(160, availableWidth / 3));
    }

    private StoreControlsLayout getStoreControlsLayout() {
        int searchY = this.panelY + 126 - 30 - 12;
        int rightEdge = this.panelX + this.panelWidth - 24;
        int removeAllX = rightEdge - 104;
        int ownedX = removeAllX - 8 - 108;
        int searchX = this.panelX + 24;
        int maxSearchW = ownedX - searchX - 12;
        int searchW = Math.min(this.getSearchWidth(this.panelWidth), Math.max(120, maxSearchW));
        return new StoreControlsLayout(searchX, searchY, searchW, ownedX, removeAllX);
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        this.statusMessageUntil = System.currentTimeMillis() + 2400L;
    }

    private List<StoreCosmetic> getFilteredItems() {
        return this.storeManager.searchCosmetics(this.searchQuery, this.selectedCategory, this.ownedOnly);
    }

    private void recomputeCardRects(List<StoreCosmetic> items) {
        this.cardRects.clear();
        this.visibleItems.clear();
        this.visibleItems.addAll(items);
        int columns = Math.max(1, (this.gridW() + 12) / 190);
        int rows = items.isEmpty() ? 0 : (int)Math.ceil((double)items.size() / (double)columns);
        int totalH = rows == 0 ? 0 : 20 + rows * 218 + (rows - 1) * 12;
        this.maxScroll = Math.max(0, totalH - this.gridH());
        int startX = this.gridX() + 10;
        int startY = this.gridY() + 10 - (int)this.scrollOffset;
        for (int i = 0; i < items.size(); ++i) {
            int col = i % columns;
            int row = i / columns;
            int cardX = startX + col * 190;
            int cardY = startY + row * 230;
            this.cardRects.add(new int[]{cardX, cardY, 178, 218});
        }
    }

    private void drawProductGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int alpha, float dt) {
        List<StoreCosmetic> items = this.getFilteredItems();
        this.clampScroll();
        this.scrollOffset = AnimationUtils.smoothDelta((float)this.scrollOffset, (float)this.targetScrollOffset, 0.3f, dt * 60.0f);
        this.scrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.scrollOffset));
        this.state.setScrollOffset(this.targetScrollOffset);
        this.recomputeCardRects(items);
        DisplaySpace.enableScissor(graphics, this.gridX(), this.gridY(), this.gridX() + this.gridW(), this.gridY() + this.gridH());
        if (items.isEmpty()) {
            this.drawEmptyState(graphics, alpha);
        }
        for (int i = 0; i < items.size(); ++i) {
            int[] rect = this.cardRects.get(i);
            int cardX = rect[0];
            int cardY = rect[1];
            if (cardY + 218 <= this.gridY() || cardY >= this.gridY() + this.gridH()) continue;
            this.drawCard(graphics, items.get(i), cardX, cardY, 178, 218, mouseX, mouseY, alpha);
        }
        DisplaySpace.disableScissor(graphics);
        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics);
        }
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics, int alpha) {
        String title = this.selectedCategory == CosmeticCategory.OWNED || this.ownedOnly ? "No owned cosmetics here" : "No cosmetics found";
        String subtitle = "Try another category or search term.";
        int centerX = this.gridX() + this.gridW() / 2;
        int centerY = this.gridY() + this.gridH() / 2;
        graphics.text(this.font, title, centerX - this.font.width(title) / 2, centerY - 10, FastClientUI.withAlpha(-723724, alpha), true);
        graphics.text(this.font, subtitle, centerX - this.font.width(subtitle) / 2, centerY + 6, FastClientUI.withAlpha(-7303024, alpha), false);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        int scrollbarX = this.gridX() + this.gridW() + 2;
        int scrollbarTrackY = this.gridY() + 6;
        int scrollbarTrackH = Math.max(1, this.gridH() - 12);
        graphics.fill(scrollbarX, scrollbarTrackY, scrollbarX + 5, scrollbarTrackY + scrollbarTrackH, 1880760099);
        double visibleRatio = (double)this.gridH() / ((double)this.gridH() + this.maxScroll);
        int thumbHeight = Math.min(scrollbarTrackH, Math.max(24, (int)((double)scrollbarTrackH * visibleRatio)));
        int thumbY = scrollbarTrackY + (int)(this.scrollOffset / this.maxScroll * (double)(scrollbarTrackH - thumbHeight));
        int thumbColor = this.scrollbarDragging ? -39373 : -1722656160;
        graphics.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, thumbColor);
    }

    private void drawCard(GuiGraphicsExtractor graphics, StoreCosmetic cosmetic, int x, int y, int w, int h, int mouseX, int mouseY, int alpha) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int cardBg = FastClientUI.withAlpha(hovered ? -434955745 : -653586413, alpha);
        graphics.fill(x, y, x + w, y + h, cardBg);
        FastClientUI.outline(graphics, x, y, w, h, FastClientUI.withAlpha(hovered ? 1717331565 : 1143616571, alpha));
        if (hovered) {
            graphics.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, FastClientUI.withAlpha(-34227, alpha));
        }
        int rarityColor = FastClientUI.withAlpha(cosmetic.getRarity().getColor(), alpha);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, rarityColor);
        int previewSize = Math.min(w - 34, h - 92);
        int previewX = x + (w - previewSize) / 2;
        int previewY = y + 15;
        int framePad = 6;
        int frameColor = FastClientUI.blend(-435153640, cosmetic.getRarity().getColor(), hovered ? 0.18f : 0.08f);
        graphics.fill(previewX - framePad, previewY - framePad, previewX + previewSize + framePad, previewY + previewSize + framePad, FastClientUI.withAlpha(frameColor, alpha));
        FastClientUI.outline(graphics, previewX - framePad, previewY - framePad, previewSize + framePad * 2, previewSize + framePad * 2, FastClientUI.withAlpha(hovered ? cosmetic.getRarity().getColor() : 1143616571, alpha));
        String assetName = cosmetic.getPreviewAssetName();
        if (assetName != null && !assetName.isEmpty()) {
            Identifier previewId = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)("textures/gui/store/items/" + assetName + ".png"));
            if (this.hasTexture(previewId)) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, previewId, previewX, previewY, 0.0f, 0.0f, previewSize, previewSize, previewSize, previewSize, FastClientUI.withAlpha(-1, alpha));
            } else {
                this.drawPlaceholderPreview(graphics, cosmetic, previewX, previewY, previewSize, alpha);
            }
        } else {
            this.drawPlaceholderPreview(graphics, cosmetic, previewX, previewY, previewSize, alpha);
        }
        int nameY = y + h - 64;
        String displayName = this.fitText(cosmetic.getDisplayName(), w - 20);
        graphics.text(this.font, displayName, x + (w - this.font.width(displayName)) / 2, nameY, FastClientUI.withAlpha(-723724, alpha), true);
        int rarityY = nameY + 14;
        String rarityText = cosmetic.getRarity().getDisplayName();
        graphics.text(this.font, rarityText, x + (w - this.font.width(rarityText)) / 2, rarityY, FastClientUI.withAlpha(cosmetic.getRarity().getColor(), alpha), false);
        int btnY = y + h - 30;
        int btnH = 22;
        int btnW = w - 20;
        int btnX = x + 10;
        boolean owned = this.state.owns(cosmetic.getId());
        boolean equipped = this.state.isEquipped(cosmetic.getId());
        if (owned && equipped) {
            int btnGap = 6;
            int equippedBtnW = (btnW - btnGap) / 2;
            int removeBtnW = btnW - equippedBtnW - btnGap;
            int removeBtnX = btnX + equippedBtnW + btnGap;
            int eqBg = FastClientUI.withAlpha(-15511009, alpha);
            graphics.fill(btnX, btnY, btnX + equippedBtnW, btnY + btnH, eqBg);
            String eqText = "Equipped";
            String fittedEqText = this.fitText(eqText, equippedBtnW - 6);
            graphics.text(this.font, fittedEqText, btnX + (equippedBtnW - this.font.width(fittedEqText)) / 2, btnY + 5, FastClientUI.withAlpha(-723724, alpha), true);
            boolean removeHovered = mouseX >= removeBtnX && mouseX <= removeBtnX + removeBtnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int removeBg = FastClientUI.withAlpha(removeHovered ? -39373 : -435153640, alpha);
            graphics.fill(removeBtnX, btnY, removeBtnX + removeBtnW, btnY + btnH, removeBg);
            String removeText = "Remove";
            String fittedRemoveText = this.fitText(removeText, removeBtnW - 6);
            graphics.text(this.font, fittedRemoveText, removeBtnX + (removeBtnW - this.font.width(fittedRemoveText)) / 2, btnY + 5, FastClientUI.withAlpha(removeHovered ? -723724 : -7303024, alpha), false);
        } else if (owned) {
            int eqBg = FastClientUI.withAlpha(hovered ? -15243738 : -435153640, alpha);
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, eqBg);
            String eqText = "Equip";
            graphics.text(this.font, eqText, btnX + (btnW - this.font.width(eqText)) / 2, btnY + 5, FastClientUI.withAlpha(hovered ? -723724 : -7303024, alpha), true);
        } else {
            boolean canAfford;
            boolean bl = canAfford = this.state.getCoinBalance() >= cosmetic.getPrice();
            int buyBg = canAfford ? FastClientUI.withAlpha(hovered ? -34227 : -39373, alpha) : FastClientUI.withAlpha(-435153640, alpha);
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, buyBg);
            Object buyText = cosmetic.getPrice() <= 0 ? "Free" : (canAfford ? "Buy " + cosmetic.getPrice() : "Need " + (cosmetic.getPrice() - this.state.getCoinBalance()));
            this.drawPriceLabel(graphics, (String)buyText, cosmetic.getPrice() > 0, btnX, btnY, btnW, FastClientUI.withAlpha(canAfford ? -723724 : -9934744, alpha), alpha);
        }
    }

    private void drawPriceLabel(GuiGraphicsExtractor graphics, String text, boolean showDiamond, int x, int y, int w, int textColor, int alpha) {
        if (!showDiamond) {
            graphics.text(this.font, text, x + (w - this.font.width(text)) / 2, y + 6, textColor, true);
            return;
        }
        int iconSize = 11;
        int gap = 4;
        int totalW = iconSize + gap + this.font.width(text);
        int startX = x + (w - totalW) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(DIAMOND_TEXTURE), startX, y + 5, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize, FastClientUI.withAlpha(-13312, alpha));
        graphics.text(this.font, text, startX + iconSize + gap, y + 6, textColor, true);
    }

    private boolean hasTexture(Identifier id) {
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
    }

    private void drawPlaceholderPreview(GuiGraphicsExtractor graphics, StoreCosmetic cosmetic, int x, int y, int size, int alpha) {
        int bg = FastClientUI.blend(-435153640, cosmetic.getRarity().getColor(), 0.22f);
        graphics.fill(x, y, x + size, y + size, FastClientUI.withAlpha(bg, alpha));
        FastClientUI.outline(graphics, x, y, size, size, FastClientUI.withAlpha(cosmetic.getRarity().getColor(), alpha));
        int accent = FastClientUI.withAlpha(cosmetic.getRarity().getColor(), Math.min(alpha, 150));
        graphics.fill(x + 8, y + 8, x + size - 8, y + 10, accent);
        graphics.fill(x + 8, y + size - 10, x + size - 8, y + size - 8, accent);
        graphics.fill(x + size / 2 - 1, y + 16, x + size / 2 + 1, y + size - 16, accent);
        String glyph = this.categoryGlyph(cosmetic);
        float scale = size >= 92 ? 2.2f : 1.7f;
        this.drawScaledCenteredText(graphics, glyph, x + size / 2, y + size / 2, scale, FastClientUI.withAlpha(-723724, alpha), true);
    }

    private String categoryGlyph(StoreCosmetic cosmetic) {
        return switch (cosmetic.getSlot()) {
            case "cloak" -> "CL";
            case "elytra" -> "EL";
            case "hat" -> "HT";
            case "back" -> "BK";
            case "aura" -> "AU";
            case "nametag" -> "NT";
            case "arm" -> "AR";
            case "leg" -> "BT";
            case "pet" -> "PT";
            default -> "FC";
        };
    }

    private void drawScaledCenteredText(GuiGraphicsExtractor graphics, String text, int centerX, int centerY, float scale, int color, boolean shadow) {
        float textX = (float)centerX - (float)this.font.width(text) * scale / 2.0f;
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

    private String fitText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && this.font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void clampScroll() {
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset));
        this.scrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.scrollOffset));
    }

    private int findCardAt(int mouseX, int mouseY) {
        for (int i = 0; i < this.cardRects.size(); ++i) {
            int[] r = this.cardRects.get(i);
            if (mouseX < r[0] || mouseX > r[0] + r[2] || mouseY < r[1] || mouseY > r[1] + r[3]) continue;
            return i;
        }
        return -1;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int cardIdx;
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();
        if (button != 0) {
            return super.mouseClicked(event, bl);
        }
        int headerH = 32;
        int headerY = this.getHeaderNavY();
        int storeBtnW = this.panelWidth < 1050 ? 152 : 168;
        int claimX = this.panelX + storeBtnW + 8 + 112 + 8;
        int closeX = this.panelX + this.panelWidth - 30;
        int closeY = headerY + (headerH - 30) / 2;
        if (mouseX >= (double)closeX && mouseX <= (double)(closeX + 30) && mouseY >= (double)closeY && mouseY <= (double)(closeY + 30)) {
            this.onClose();
            return true;
        }
        if (mouseX >= (double)claimX && mouseX <= (double)(claimX + 118) && mouseY >= (double)headerY && mouseY <= (double)(headerY + headerH)) {
            if (this.storeManager.claimDailyCoins()) {
                this.setStatus("Claimed " + this.storeManager.getDailyCoinClaimAmount() + " coins");
            } else {
                this.setStatus("Daily coins already claimed");
            }
            this.searchFocused = false;
            return true;
        }
        CosmeticCategory[] categories = CosmeticCategory.values();
        int tabSize = this.getCategoryTabSize();
        int tabGap = this.getCategoryTabGap();
        int tabY = this.getCategoryTabsY();
        int tabX = this.getCategoryTabsStartX(tabSize, tabGap);
        for (int i = 0; i < categories.length; ++i) {
            CosmeticCategory cat = categories[i];
            if (mouseX >= (double)tabX && mouseX <= (double)(tabX + tabSize) && mouseY >= (double)tabY && mouseY <= (double)(tabY + tabSize)) {
                this.selectedCategory = cat;
                this.state.setLastSelectedCategory(cat.name());
                this.targetScrollOffset = 0.0;
                this.scrollOffset = 0.0;
                this.searchFocused = false;
                this.storeManager.saveState();
                return true;
            }
            tabX += tabSize + tabGap;
        }
        StoreControlsLayout controls = this.getStoreControlsLayout();
        if (mouseX >= (double)controls.searchX && mouseX <= (double)(controls.searchX + controls.searchW) && mouseY >= (double)controls.searchY && mouseY <= (double)(controls.searchY + 30)) {
            this.searchFocused = true;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }
        if (!this.isWithinGrid(mouseX, mouseY)) {
            this.searchFocused = false;
        }
        if (mouseX >= (double)controls.ownedX && mouseX <= (double)(controls.ownedX + 108) && mouseY >= (double)controls.searchY && mouseY <= (double)(controls.searchY + 30)) {
            this.ownedOnly = !this.ownedOnly;
            this.targetScrollOffset = 0.0;
            this.scrollOffset = 0.0;
            this.searchFocused = false;
            return true;
        }
        if (mouseX >= (double)controls.removeAllX && mouseX <= (double)(controls.removeAllX + 104) && mouseY >= (double)controls.searchY && mouseY <= (double)(controls.searchY + 30)) {
            if (this.state.getEquippedItemIds().isEmpty()) {
                this.setStatus("No equipped cosmetics to remove");
            } else {
                this.storeManager.removeAll();
                this.setStatus("Removed all equipped cosmetics");
            }
            this.searchFocused = false;
            return true;
        }
        if (this.maxScroll > 0.0 && mouseX >= (double)(this.gridX() + this.gridW()) && mouseX <= (double)(this.gridX() + this.gridW() + 5 + 8) && mouseY >= (double)this.gridY() && mouseY <= (double)(this.gridY() + this.gridH())) {
            this.scrollbarDragging = true;
            this.dragStartY = mouseY;
            this.dragStartScroll = this.targetScrollOffset;
            this.searchFocused = false;
            return true;
        }
        int n = cardIdx = this.isWithinGrid(mouseX, mouseY) ? this.findCardAt((int)mouseX, (int)mouseY) : -1;
        if (cardIdx >= 0 && cardIdx < this.visibleItems.size()) {
            StoreCosmetic cosmetic = this.visibleItems.get(cardIdx);
            int[] rect = this.cardRects.get(cardIdx);
            int cardX = rect[0];
            int cardY = rect[1];
            int cardW = rect[2];
            int cardH = rect[3];
            int btnY = cardY + cardH - 30;
            int btnH = 22;
            int btnW = cardW - 20;
            int btnX = cardX + 10;
            boolean owned = this.state.owns(cosmetic.getId());
            boolean equipped = this.state.isEquipped(cosmetic.getId());
            if (cardY + cardH <= this.gridY() || cardY >= this.gridY() + this.gridH()) {
                return true;
            }
            if (owned && equipped) {
                int btnGap = 6;
                int equippedBtnW = (btnW - btnGap) / 2;
                int removeBtnW = btnW - equippedBtnW - btnGap;
                int removeBtnX = btnX + equippedBtnW + btnGap;
                if (mouseX >= (double)removeBtnX && mouseX <= (double)(removeBtnX + removeBtnW) && mouseY >= (double)btnY && mouseY <= (double)(btnY + btnH)) {
                    this.storeManager.unequip(cosmetic.getId());
                    this.setStatus("Removed " + cosmetic.getDisplayName());
                    this.searchFocused = false;
                    return true;
                }
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + equippedBtnW) && mouseY >= (double)btnY && mouseY <= (double)(btnY + btnH)) {
                    this.searchFocused = false;
                    return true;
                }
            } else if (owned) {
                if (mouseX >= (double)btnX && mouseX <= (double)(btnX + btnW) && mouseY >= (double)btnY && mouseY <= (double)(btnY + btnH)) {
                    this.storeManager.equip(cosmetic.getId());
                    this.setStatus("Equipped " + cosmetic.getDisplayName());
                    this.searchFocused = false;
                    return true;
                }
            } else if (mouseX >= (double)btnX && mouseX <= (double)(btnX + btnW) && mouseY >= (double)btnY && mouseY <= (double)(btnY + btnH)) {
                if (this.state.getCoinBalance() >= cosmetic.getPrice()) {
                    this.storeManager.purchase(cosmetic.getId());
                    this.storeManager.equip(cosmetic.getId());
                    this.setStatus("Purchased and equipped " + cosmetic.getDisplayName());
                } else {
                    this.setStatus("Not enough coins for " + cosmetic.getDisplayName());
                }
                this.searchFocused = false;
                return true;
            }
            this.searchFocused = false;
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    private boolean isWithinGrid(double mouseX, double mouseY) {
        return mouseX >= (double)this.gridX() && mouseX <= (double)(this.gridX() + this.gridW()) && mouseY >= (double)this.gridY() && mouseY <= (double)(this.gridY() + this.gridH());
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.scrollbarDragging = false;
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.scrollbarDragging && this.maxScroll > 0.0) {
            int scrollbarTrackH = Math.max(1, this.gridH() - 12);
            double mouseY = DisplaySpace.mouseY(event.y());
            double scrollRatio = (mouseY - this.dragStartY) / (double)scrollbarTrackH;
            this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.dragStartScroll + scrollRatio * this.maxScroll));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        if (pxMouseX >= this.gridX() && pxMouseX <= this.gridX() + this.gridW() && pxMouseY >= this.gridY() && pxMouseY <= this.gridY() + this.gridH()) {
            this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 34.0));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 256) {
            if (this.searchFocused && !this.searchQuery.isEmpty()) {
                this.searchQuery = "";
                return true;
            }
            if (!this.searchFocused) {
                this.onClose();
                return true;
            }
            this.searchFocused = false;
            return true;
        }
        if (this.searchFocused) {
            if (keyCode == 259) {
                if (!this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                }
                return true;
            }
            if (keyCode == 257) {
                this.searchFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        char c;
        if (this.searchFocused && (c = (char)event.codepoint()) >= ' ') {
            this.searchQuery = this.searchQuery + c;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        this.state.setLastSelectedCategory(this.selectedCategory.name());
        this.state.setScrollOffset(this.targetScrollOffset);
        this.storeManager.saveState();
        if (this.previousScreen != null) {
            Minecraft.getInstance().gui.setScreen(this.previousScreen);
        } else {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class StoreControlsLayout {
        final int searchX;
        final int searchY;
        final int searchW;
        final int ownedX;
        final int removeAllX;

        StoreControlsLayout(int searchX, int searchY, int searchW, int ownedX, int removeAllX) {
            this.searchX = searchX;
            this.searchY = searchY;
            this.searchW = searchW;
            this.ownedX = ownedX;
            this.removeAllX = removeAllX;
        }
    }
}

