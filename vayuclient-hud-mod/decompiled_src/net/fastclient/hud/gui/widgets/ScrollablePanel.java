/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 */
package net.fastclient.hud.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.widgets.ModuleCard;
import net.fastclient.hud.render.AnimationUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ScrollablePanel {
    private int x;
    private int y;
    private final int width;
    private final int height;
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private double maxScroll = 0.0;
    private final List<ModuleCard> cards = new ArrayList<ModuleCard>();
    private int columns = 4;
    private int cardWidth = 270;
    private int cardHeight = 190;
    private int cardSpacing = 14;
    private static final int COMPACT_CARD_SPACING = 8;
    private static final int THREE_COLUMN_SPACING = 12;
    private static final int FOUR_COLUMN_SPACING = 18;
    private static final int PADDING = 10;
    private static final int SCROLLBAR_SPACE = 12;
    private static final int COMPACT_CARD_WIDTH = 170;
    private static final int PREFERRED_CARD_WIDTH = 236;
    private static final int MAX_CARD_WIDTH = 280;
    private static final int MIN_CARD_HEIGHT = 186;
    private boolean scrollbarHovered;
    private boolean scrollbarDragging;
    private double dragStartY;
    private double dragStartScroll;
    private long lastUpdate = System.currentTimeMillis();

    public ScrollablePanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.calculateGrid();
    }

    private void calculateGrid() {
        boolean hasPreferredFourColumnWidth;
        int availableWidth = Math.max(1, this.width - 20 - 12);
        this.columns = availableWidth >= 704 ? 4 : (availableWidth >= 526 ? 3 : (availableWidth >= 348 ? 2 : 1));
        boolean bl = hasPreferredFourColumnWidth = availableWidth >= 998;
        this.cardSpacing = this.columns >= 4 ? (hasPreferredFourColumnWidth ? 18 : 8) : (this.columns > 1 ? 12 : 0);
        int availableForCards = availableWidth - this.cardSpacing * Math.max(0, this.columns - 1);
        this.cardWidth = Math.max(170, Math.min(280, availableForCards / this.columns));
        this.cardHeight = 186;
        this.updateMaxScroll();
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        float delta = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.clampScroll();
        this.scrollOffset = AnimationUtils.smoothDelta((float)this.scrollOffset, (float)this.targetScrollOffset, 0.3f, delta * 60.0f);
        this.scrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.scrollOffset));
        DisplaySpace.enableScissor(graphics, this.x, this.y, this.x + this.width, this.y + this.height);
        int startX = this.getGridStartX();
        int startY = this.y + 10 - (int)this.scrollOffset;
        for (int i = 0; i < this.cards.size(); ++i) {
            int col = i % this.columns;
            int row = i / this.columns;
            int cardX = startX + col * (this.cardWidth + this.cardSpacing);
            int cardY = startY + row * (this.cardHeight + this.cardSpacing);
            ModuleCard card = this.cards.get(i);
            card.setX(cardX);
            card.setY(cardY);
            card.setSize(this.cardWidth, this.cardHeight);
            if (!this.isCardVisible(cardY)) continue;
            card.render(graphics, mouseX, mouseY);
        }
        DisplaySpace.disableScissor(graphics);
        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int scrollbarWidth = 5;
        int scrollbarX = this.x + this.width - scrollbarWidth - 4;
        int scrollbarTrackY = this.y + 6;
        int scrollbarTrackHeight = Math.max(1, this.height - 12);
        this.scrollbarHovered = mouseX >= scrollbarX - 4 && mouseX <= scrollbarX + scrollbarWidth + 4 && mouseY >= this.y && mouseY <= this.y + this.height;
        graphics.fill(scrollbarX, scrollbarTrackY, scrollbarX + scrollbarWidth, scrollbarTrackY + scrollbarTrackHeight, 1880760099);
        double visibleRatio = (double)this.height / ((double)this.height + this.maxScroll);
        int thumbHeight = Math.min(scrollbarTrackHeight, Math.max(24, (int)((double)scrollbarTrackHeight * visibleRatio)));
        int thumbY = scrollbarTrackY + (int)(this.scrollOffset / this.maxScroll * (double)(scrollbarTrackHeight - thumbHeight));
        int thumbColor = this.scrollbarDragging ? -1493956 : (this.scrollbarHovered ? -865438854 : -1722656160);
        graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int scrollbarX;
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX < (double)this.x || mouseX > (double)(this.x + this.width) || mouseY < (double)this.y || mouseY > (double)(this.y + this.height)) {
            return false;
        }
        if (event.button() == 0 && this.maxScroll > 0.0 && mouseX >= (double)((scrollbarX = this.x + this.width - 8) - 4) && mouseX <= (double)(scrollbarX + 8)) {
            this.scrollbarDragging = true;
            this.dragStartY = mouseY;
            this.dragStartScroll = this.targetScrollOffset;
            return true;
        }
        for (ModuleCard card : this.cards) {
            if (!this.isCardVisible(card.getY()) || !card.mouseClicked(event, bl)) continue;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.scrollbarDragging = false;
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.scrollbarDragging && this.maxScroll > 0.0) {
            int scrollbarTrackHeight = Math.max(1, this.height - 12);
            double scrollRatio = (event.y() - this.dragStartY) / (double)scrollbarTrackHeight;
            this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.dragStartScroll + scrollRatio * this.maxScroll));
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        if (mouseX < (double)this.x || mouseX > (double)(this.x + this.width) || mouseY < (double)this.y || mouseY > (double)(this.y + this.height)) {
            return false;
        }
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 34.0));
        return true;
    }

    public void setCards(List<ModuleCard> cards) {
        this.cards.clear();
        this.cards.addAll(cards);
        this.calculateGrid();
        if (this.targetScrollOffset > this.maxScroll) {
            this.targetScrollOffset = this.maxScroll;
            this.scrollOffset = this.maxScroll;
        }
    }

    public void clearCards() {
        this.cards.clear();
        this.scrollOffset = 0.0;
        this.targetScrollOffset = 0.0;
        this.maxScroll = 0.0;
    }

    private void updateMaxScroll() {
        int rows = this.cards.isEmpty() ? 0 : (int)Math.ceil((double)this.cards.size() / (double)this.columns);
        int totalHeight = rows == 0 ? 0 : 20 + rows * this.cardHeight + (rows - 1) * this.cardSpacing;
        this.maxScroll = Math.max(0, totalHeight - this.height);
        this.clampScroll();
    }

    private void clampScroll() {
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset));
        this.scrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.scrollOffset));
    }

    private boolean isCardVisible(int cardY) {
        return cardY + this.cardHeight > this.y && cardY < this.y + this.height;
    }

    private int getGridStartX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public double getScrollOffset() {
        return this.targetScrollOffset;
    }

    public void setScrollOffset(double offset) {
        this.scrollOffset = this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, offset));
    }
}

