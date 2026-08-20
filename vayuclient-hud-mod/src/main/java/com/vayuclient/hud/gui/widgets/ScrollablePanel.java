package com.vayuclient.hud.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.widgets.ModuleCard;
import com.vayuclient.hud.render.AnimationUtils;
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
    private final List<ModuleCard> cards = new ArrayList<>();
    private int columns = 3;
    private int cardWidth = 140;
    private int cardHeight = 68;
    private int cardSpacing = 8;
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
        int availableWidth = Math.max(1, this.width - 16);
        this.columns = Math.max(1, availableWidth / 140);
        this.cardSpacing = 8;
        int totalSpacing = this.cardSpacing * Math.max(0, this.columns - 1);
        this.cardWidth = Math.max(120, (availableWidth - totalSpacing) / this.columns);
        this.cardHeight = 68;
        this.updateMaxScroll();
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        float delta = (float) (now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.clampScroll();
        this.scrollOffset = AnimationUtils.smoothDelta((float) this.scrollOffset, (float) this.targetScrollOffset, 0.3f, delta * 60.0f);
        this.scrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.scrollOffset));

        DisplaySpace.enableScissor(graphics, this.x, this.y, this.x + this.width, this.y + this.height);
        int startX = this.x;
        int startY = this.y + 6 - (int) this.scrollOffset;

        for (int i = 0; i < this.cards.size(); ++i) {
            int col = i % this.columns;
            int row = i / this.columns;
            int cardX = startX + col * (this.cardWidth + this.cardSpacing);
            int cardY = startY + row * (this.cardHeight + this.cardSpacing);
            ModuleCard card = this.cards.get(i);
            card.setX(cardX);
            card.setY(cardY);
            if (!this.isCardVisible(cardY)) continue;
            card.render(graphics, mouseX, mouseY);
        }

        DisplaySpace.disableScissor(graphics);

        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int scrollbarWidth = 4;
        int scrollbarX = this.x + this.width - scrollbarWidth - 2;
        int scrollbarTrackY = this.y + 4;
        int scrollbarTrackHeight = Math.max(1, this.height - 8);
        this.scrollbarHovered = mouseX >= scrollbarX - 4 && mouseX <= scrollbarX + scrollbarWidth + 4 && mouseY >= this.y && mouseY <= this.y + this.height;
        graphics.fill(scrollbarX, scrollbarTrackY, scrollbarX + scrollbarWidth, scrollbarTrackY + scrollbarTrackHeight, 0x33000000);
        double visibleRatio = (double) this.height / ((double) this.height + this.maxScroll);
        int thumbHeight = Math.min(scrollbarTrackHeight, Math.max(18, (int) ((double) scrollbarTrackHeight * visibleRatio)));
        int thumbY = scrollbarTrackY + (int) (this.scrollOffset / this.maxScroll * (double) (scrollbarTrackHeight - thumbHeight));
        int thumbColor = this.scrollbarDragging ? 0xFF00D9FF : (this.scrollbarHovered ? 0xFF38BDF8 : 0x8838BDF8);
        graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX < (double) this.x || mouseX > (double) (this.x + this.width) || mouseY < (double) this.y || mouseY > (double) (this.y + this.height)) {
            return false;
        }
        if (event.button() == 0 && this.maxScroll > 0.0) {
            int scrollbarX = this.x + this.width - 6;
            if (mouseX >= (double) (scrollbarX - 4) && mouseX <= (double) (scrollbarX + 6)) {
                this.scrollbarDragging = true;
                this.dragStartY = mouseY;
                this.dragStartScroll = this.targetScrollOffset;
                return true;
            }
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
            int scrollbarTrackHeight = Math.max(1, this.height - 8);
            double scrollRatio = (event.y() - this.dragStartY) / (double) scrollbarTrackHeight;
            this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.dragStartScroll + scrollRatio * this.maxScroll));
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        if (mouseX < (double) this.x || mouseX > (double) (this.x + this.width) || mouseY < (double) this.y || mouseY > (double) (this.y + this.height)) {
            return false;
        }
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 24.0));
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
        int rows = this.cards.isEmpty() ? 0 : (int) Math.ceil((double) this.cards.size() / (double) this.columns);
        int totalHeight = rows == 0 ? 0 : 12 + rows * this.cardHeight + (rows - 1) * this.cardSpacing;
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
