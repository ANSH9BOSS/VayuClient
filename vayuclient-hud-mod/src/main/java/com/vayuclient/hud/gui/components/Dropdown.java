package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class Dropdown extends UIComponent {
    private final String label;
    private final String[] options;
    private int selectedIndex;
    private final Consumer<String> onChange;
    private boolean expanded = false;
    private float expandProgress = 0.0f;
    private float hoverProgress = 0.0f;
    private int hoveredOption = -1;
    private long lastUpdate = System.currentTimeMillis();

    public Dropdown(int x, int y, int width, int height, String label, String[] options, String selected, Consumer<String> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.options = options;
        this.onChange = onChange;
        this.selectedIndex = 0;
        for (int i = 0; i < options.length; ++i) {
            if (!options[i].equalsIgnoreCase(selected)) continue;
            this.selectedIndex = i;
            break;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.expandProgress = AnimationUtils.smoothDelta(this.expandProgress, this.expanded ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();

        // 1. Setting Label
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(0xFFCBD5E1, 0xFFFFFFFF, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);

        // 2. Dropdown Selector Button
        int dropWidth = 120;
        int dropX = this.x + this.width - dropWidth - 12;
        int dropY = this.y + 2;
        int dropHeight = this.height - 4;

        int btnBg = this.expanded ? 0xE6142338 : (this.hovered ? 0xD0111D2E : 0xC00B1320);
        int btnBorder = this.expanded ? 0xFF38BDF8 : (this.hovered ? 0x8838BDF8 : 0x3338BDF8);

        VayuHUDUI.roundedRect(graphics, dropX, dropY, dropWidth, dropHeight, 6, btnBg);
        VayuHUDUI.roundedOutline(graphics, dropX, dropY, dropWidth, dropHeight, 6, btnBorder);

        String selectedText = this.options[this.selectedIndex];
        int textX = dropX + 10;
        this.drawUiText(graphics, mc, selectedText, textX, this.centeredTextY(mc, dropY, dropHeight), 0xFF38BDF8);

        String arrow = this.expanded ? "▲" : "▼";
        this.drawUiText(graphics, mc, arrow, dropX + dropWidth - 16, this.centeredTextY(mc, dropY, dropHeight), 0xFF94A3B8);

        // 3. Expanded Popup Menu
        if (this.expandProgress > 0.01f) {
            int optionHeight = 24;
            int totalOptionsHeight = (int)((float)(this.options.length * optionHeight) * this.expandProgress);
            int optionsY = this.y + this.height + 4;

            VayuHUDUI.roundedRect(graphics, dropX, optionsY, dropWidth, totalOptionsHeight, 6, 0xF5060D17);
            VayuHUDUI.roundedOutline(graphics, dropX, optionsY, dropWidth, totalOptionsHeight, 6, 0x6638BDF8);

            this.hoveredOption = -1;
            if (this.expandProgress > 0.85f) {
                for (int i = 0; i < this.options.length; ++i) {
                    int optY = optionsY + i * optionHeight;
                    if (mouseX >= dropX && mouseX <= dropX + dropWidth && mouseY >= optY && mouseY < optY + optionHeight) {
                        this.hoveredOption = i;
                        break;
                    }
                }
            }

            if (this.expandProgress > 0.5f) {
                for (int i = 0; i < this.options.length; ++i) {
                    int optY = optionsY + i * optionHeight;
                    if (optY + optionHeight > optionsY + totalOptionsHeight) break;

                    boolean isSelected = i == this.selectedIndex;
                    boolean isHover = i == this.hoveredOption;

                    if (isHover) {
                        VayuHUDUI.roundedRect(graphics, dropX + 2, optY + 2, dropWidth - 4, optionHeight - 4, 4, 0xFF0284C7);
                    }

                    int optTextColor = isHover ? 0xFFFFFFFF : (isSelected ? 0xFF38BDF8 : 0xFFCBD5E1);
                    this.drawUiText(graphics, mc, this.options[i], dropX + 8, this.centeredTextY(mc, optY, optionHeight), optTextColor);

                    if (isSelected) {
                        this.drawUiText(graphics, mc, "✓", dropX + dropWidth - 14, this.centeredTextY(mc, optY, optionHeight), isHover ? 0xFFFFFFFF : 0xFF38BDF8);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int dropWidth = 120;
        int dropX = this.x + this.width - dropWidth - 12;
        int dropY = this.y + 2;
        int dropHeight = this.height - 4;

        if (event.x() >= (double)dropX && event.x() <= (double)(dropX + dropWidth) && event.y() >= (double)dropY && event.y() <= (double)(dropY + dropHeight)) {
            this.expanded = !this.expanded;
            return true;
        }

        if (this.expanded && this.hoveredOption >= 0 && this.hoveredOption < this.options.length) {
            this.selectedIndex = this.hoveredOption;
            this.expanded = false;
            if (this.onChange != null) {
                this.onChange.accept(this.options[this.selectedIndex]);
            }
            return true;
        }

        if (this.expanded) {
            this.expanded = false;
            return true;
        }

        return false;
    }

    public boolean isExpanded() {
        return this.expanded;
    }
}
