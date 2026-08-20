/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.FormattedText
 */
package com.vayuclient.hud.gui.screens;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.components.ColorPicker;
import com.vayuclient.hud.gui.components.Dropdown;
import com.vayuclient.hud.gui.components.KeybindButton;
import com.vayuclient.hud.gui.components.Slider;
import com.vayuclient.hud.gui.components.TextInput;
import com.vayuclient.hud.gui.components.ToggleButton;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.KeybindSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import com.vayuclient.hud.modules.settings.Setting;
import com.vayuclient.hud.modules.settings.TextSetting;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FormattedText;

public class ModuleConfigScreen
extends Screen {
    private final Module module;
    private final Screen parent;
    private final List<UIComponent> components = new ArrayList<UIComponent>();
    private final IdentityHashMap<UIComponent, SettingRow> rows = new IdentityHashMap();
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private double maxScroll = 0.0;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private AnimationUtils.Animation openAnimation;
    private long lastUpdate = System.currentTimeMillis();
    private int backBtnX;
    private int backBtnY;
    private int backBtnSize = 30;

    public ModuleConfigScreen(Module module, Screen parent) {
        super(VayuFonts.moduleName(module.getDisplayName() + " Settings"));
        this.module = module;
        this.parent = parent;
    }

    protected void init() {
        this.components.clear();
        this.rows.clear();
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        this.panelWidth = Math.max(1, Math.min(displayWidth - 48, (int)((double)displayWidth * 0.48)));
        this.panelHeight = Math.max(1, Math.min(displayHeight - 72, (int)((double)displayHeight * 0.72)));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = (displayHeight - this.panelHeight) / 2;
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 200L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        int settingWidth = this.panelWidth - 72;
        int centerX = this.panelX + 36;
        int y = this.panelY + 112;
        int rowGap = 14;
        KeybindButton moduleKeybind = new KeybindButton(centerX, y, settingWidth, 28, "toggle_keybind", this.module.getKeyBinding(), this.module.getKeyModifiers(), (keyCode, modifiers) -> {
            this.module.setKeyBinding((int)keyCode, (int)modifiers);
            this.saveAndUpdate();
        });
        this.addRow(moduleKeybind, this.module.getName().equals("Waypoints") ? "Key used to open the waypoint manager while the module is enabled." : "Key used to enable or disable this module.", 28);
        y += this.rowHeight(28) + rowGap;
        for (Setting<?> setting : this.module.getSettings()) {
            if (!setting.isVisible()) continue;
            UIComponent component = null;
            int controlHeight = 28;
            if (setting instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting)setting;
                component = new ToggleButton(centerX, y, settingWidth, 28, bs.getName(), (Boolean)bs.getValue(), v -> {
                    bs.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting)setting;
                controlHeight = 52;
                component = new Slider(centerX, y, settingWidth, controlHeight, ns.getName(), (Double)ns.getValue(), ns.getMin(), ns.getMax(), ns.getStep(), v -> {
                    ns.setValue((Double)v);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting)setting;
                String[] modes = ms.getModes();
                component = new Dropdown(centerX, y, settingWidth, 28, ms.getName(), modes, (String)ms.getValue(), v -> {
                    ms.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting)setting;
                component = new ColorPicker(centerX, y, settingWidth, 28, cs.getName(), (Color)cs.getValue(), color -> {
                    cs.setValue(color);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof TextSetting) {
                TextSetting ts = (TextSetting)setting;
                component = new TextInput(centerX, y, settingWidth, 28, ts.getName(), (String)ts.getValue(), value -> {
                    ts.setValue(value);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof KeybindSetting) {
                KeybindSetting ks = (KeybindSetting)setting;
                component = new KeybindButton(centerX, y, settingWidth, 28, ks.getName(), ks.getKey(), value -> {
                    ks.setKey((int)value);
                    this.saveAndUpdate();
                });
            }
            if (component == null) continue;
            this.addRow(component, setting.getDescription(), controlHeight);
            y += this.rowHeight(controlHeight) + rowGap;
        }
        this.maxScroll = Math.max(0, y - (this.panelY + this.panelHeight - 40));
    }

    private void addRow(UIComponent component, String description, int controlHeight) {
        this.components.add(component);
        this.rows.put(component, new SettingRow(description == null ? "" : description, controlHeight));
    }

    private int rowHeight(int controlHeight) {
        return controlHeight + 36;
    }

    private void rebuildComponents() {
        double savedScroll = this.targetScrollOffset;
        this.init();
        this.targetScrollOffset = Math.min(savedScroll, this.maxScroll);
    }

    private void saveAndUpdate() {
        VayuHUDClient.getInstance().getModuleManager().saveConfig();
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), VayuHUDUI.withAlpha(-16777216, 170));
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean isExpandedOverlay;
        int adjustedY;
        int originalY;
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.scrollOffset = AnimationUtils.smoothDelta((float)this.scrollOffset, (float)this.targetScrollOffset, 0.3f, dt * 60.0f);
        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int)((float)this.panelY + (1.0f - animProgress) * 30.0f);
        int alpha = (int)(animProgress * 255.0f);
        this.drawPanelBackground(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, alpha);
        this.drawHeader(graphics, this.panelX, animatedPanelY, this.panelWidth, alpha);
        int contentY = animatedPanelY + 98;
        int contentHeight = this.panelHeight - 134;
        DisplaySpace.enableScissor(graphics, this.panelX + 12, contentY, this.panelX + this.panelWidth - 12, contentY + contentHeight);
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            originalY = component.getY();
            adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);
            boolean bl = isExpandedOverlay = component instanceof Dropdown && ((Dropdown)component).getHeight() > 32 || component instanceof ColorPicker && ((ColorPicker)component).isExpanded();
            if (!isExpandedOverlay && adjustedY + component.getHeight() > contentY - 10 && adjustedY < contentY + contentHeight + 10) {
                this.drawSettingRowBackground(graphics, component, adjustedY);
                component.render(graphics, pxMouseX, pxMouseY, delta);
                this.drawSettingDescription(graphics, component, adjustedY, alpha);
            }
            component.setY(originalY);
        }
        DisplaySpace.disableScissor(graphics);
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            originalY = component.getY();
            adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);
            boolean bl = isExpandedOverlay = component instanceof Dropdown && ((Dropdown)component).getHeight() > 32 || component instanceof ColorPicker && ((ColorPicker)component).isExpanded();
            if (isExpandedOverlay) {
                component.render(graphics, pxMouseX, pxMouseY, delta);
            }
            component.setY(originalY);
        }
        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics, this.panelX + this.panelWidth - 11, contentY, 4, contentHeight, alpha);
        }
        this.drawFooter(graphics, this.panelX, animatedPanelY + this.panelHeight - 32, this.panelWidth, alpha);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 7, VayuHUDUI.withAlpha(-234156528, alpha));
        VayuHUDUI.outline(graphics, x, y, w, h, VayuHUDUI.withAlpha(1143616571, alpha));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha) {
        int statusColor;
        String status;
        this.backBtnX = x + 14;
        this.backBtnY = y + 18;
        VayuHUDUI.roundedRect(graphics, this.backBtnX, this.backBtnY, this.backBtnSize, this.backBtnSize, 6, VayuHUDUI.withAlpha(-435153640, alpha));
        VayuHUDUI.roundedOutline(graphics, this.backBtnX, this.backBtnY, this.backBtnSize, this.backBtnSize, 6, VayuHUDUI.withAlpha(1143616571, alpha));
        String backLabel = "<";
        this.drawUiText(graphics, backLabel, this.backBtnX + (this.backBtnSize - this.uiTextWidth(backLabel)) / 2, this.backBtnY + (this.backBtnSize - this.uiLineHeight()) / 2 + 1, VayuHUDUI.withAlpha(VayuTheme.PRIMARY, alpha));
        int iconSize = 32;
        int iconX = x + 56;
        int iconY = y + 17;
        VayuHUDUI.roundedRect(graphics, iconX, iconY, iconSize, iconSize, 6, VayuHUDUI.withAlpha(-435153640, alpha));
        VayuHUDUI.roundedOutline(graphics, iconX, iconY, iconSize, iconSize, 6, VayuHUDUI.withAlpha(1143616571, alpha));
        graphics.blit(RenderPipelines.GUI_TEXTURED, VayuHUDUI.icon(this.module), iconX + 5, iconY + 5, 0.0f, 0.0f, 22, 22, 22, 22, VayuHUDUI.withAlpha(-1, alpha));
        if (this.module.isHotkeyOnly()) {
            boolean hasKey = this.module.getKeyBinding() != 0;
            int dotColor = hasKey ? VayuTheme.SUCCESS : VayuTheme.WARNING;
            status = hasKey ? "Hotkey Configured" : "Set a keybind to toggle";
            statusColor = dotColor;
        } else {
            status = this.module.isEnabled() ? "[ENABLED]" : "[DISABLED]";
            statusColor = this.module.isEnabled() ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED;
        }
        graphics.fill(x, y, x + w, y + 2, VayuHUDUI.withAlpha(this.module.isEnabled() ? VayuTheme.PRIMARY : 1143616571, alpha));
        this.drawTitleText(graphics, this.module.getDisplayName(), x + 100, y + 14, VayuHUDUI.withAlpha(VayuTheme.TEXT_PRIMARY, alpha));
        this.drawUiText(graphics, status, x + 100, y + 39, VayuHUDUI.withAlpha(statusColor, alpha));
        String desc = this.module.getDescription();
        desc = this.fitBodyText(desc != null ? desc : "", w - 116);
        this.drawUiText(graphics, desc, x + 100, y + 64, VayuHUDUI.withAlpha(VayuTheme.TEXT_MUTED, alpha));
        graphics.fill(x + 14, y + 92, x + w - 14, y + 93, VayuHUDUI.withAlpha(1143616571, alpha));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha) {
        String hint = "ESC to go back - changes save automatically";
        int hintWidth = this.uiTextWidth(hint);
        this.drawUiText(graphics, hint, x + (w - hintWidth) / 2, y + 8, VayuHUDUI.withAlpha(-7303024, alpha));
    }

    private void drawSettingRowBackground(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY) {
        SettingRow row = this.rows.get(component);
        if (row == null) {
            return;
        }
        VayuHUDUI.roundedRect(graphics, component.getX() - 10, adjustedY - 6, component.getWidth() + 20, this.rowHeight(row.controlHeight()) + 3, 5, VayuHUDUI.withAlpha(-653586413, 150));
        VayuHUDUI.outline(graphics, component.getX() - 10, adjustedY - 6, component.getWidth() + 20, this.rowHeight(row.controlHeight()) + 3, VayuHUDUI.withAlpha(1143616571, 130));
    }

    private void drawSettingDescription(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY, int alpha) {
        SettingRow row = this.rows.get(component);
        if (row == null || row.description().isEmpty()) {
            return;
        }
        String description = this.fitBodyText(row.description(), component.getWidth() - 24);
        this.drawUiText(graphics, description, component.getX() + 12, adjustedY + row.controlHeight() + 10, VayuHUDUI.withAlpha(-9934744, alpha));
    }

    private String fitBodyText(String text, int maxWidth) {
        if (this.uiTextWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && this.uiTextWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void drawUiText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = VayuFonts.bodyScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, VayuFonts.body(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private int uiTextWidth(String text) {
        return Math.round((float)this.font.width((FormattedText)VayuFonts.body(text)) * VayuFonts.bodyScale());
    }

    private int uiLineHeight() {
        Objects.requireNonNull(this.font);
        return Math.round(9.0f * VayuFonts.bodyScale());
    }

    private void drawTitleText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = VayuFonts.titleScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, VayuFonts.title(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        graphics.fill(x, y, x + w, y + h, VayuHUDUI.withAlpha(-435153640, 90));
        double visibleRatio = (double)h / ((double)h + this.maxScroll);
        int thumbHeight = Math.max(20, (int)((double)h * visibleRatio));
        int thumbY = y + (int)(this.scrollOffset / this.maxScroll * (double)(h - thumbHeight));
        graphics.fill(x, thumbY, x + w, thumbY + thumbHeight, VayuHUDUI.withAlpha(-7303024, alpha));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        if (mouseX >= (double)this.backBtnX && mouseX <= (double)(this.backBtnX + this.backBtnSize) && mouseY >= (double)this.backBtnY && mouseY <= (double)(this.backBtnY + this.backBtnSize)) {
            this.onClose();
            return true;
        }
        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl);
            component.setY(originalY);
            if (!result) continue;
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            component.mouseReleased(pxEvent);
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseDragged(pxEvent, DisplaySpace.mouseDelta(deltaX), DisplaySpace.mouseDelta(deltaY));
            component.setY(originalY);
            if (!result) continue;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 25.0));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 344) {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(null);
            }
            return true;
        }
        for (UIComponent component : this.components) {
            if (!component.keyPressed(event)) continue;
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        for (UIComponent component : this.components) {
            if (!component.charTyped(event)) continue;
            return true;
        }
        return super.charTyped(event);
    }

    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private record SettingRow(String description, int controlHeight) {
    }
}

