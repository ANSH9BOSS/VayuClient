/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package net.fastclient.hud.modules.impl.utility;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Notifications
extends Module {
    private final BooleanSetting moduleToggle = this.register(new BooleanSetting("module_toggle", "Show notification when modules are toggled", true));
    private final BooleanSetting showEnabled = this.register(new BooleanSetting("show_enabled", "Show when modules are enabled", true));
    private final BooleanSetting showDisabled = this.register(new BooleanSetting("show_disabled", "Show when modules are disabled", true));
    private final NumberSetting duration = this.register(new NumberSetting("duration", "How long notifications stay on screen", 3.0, 1.0, 10.0, 0.5));
    private static final List<Notification> notifications = new ArrayList<Notification>();

    public Notifications() {
        super("Notifications", "Shows popup notifications for module toggles", Category.UTILITY);
    }

    public static void addNotification(String title, String message, NotificationType type) {
        notifications.add(new Notification(title, message, type, System.currentTimeMillis()));
    }

    public void onModuleToggle(String moduleName, boolean enabled) {
        if (!this.isEnabled() || !((Boolean)this.moduleToggle.getValue()).booleanValue()) {
            return;
        }
        if (enabled && !((Boolean)this.showEnabled.getValue()).booleanValue()) {
            return;
        }
        if (!enabled && !((Boolean)this.showDisabled.getValue()).booleanValue()) {
            return;
        }
        String title = enabled ? "Module Enabled" : "Module Disabled";
        NotificationType type = enabled ? NotificationType.INFO : NotificationType.WARNING;
        Notifications.addNotification(title, moduleName, type);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        long durationMs = (long)((Double)this.duration.getValue() * 1000.0);
        Iterator<Notification> iter = notifications.iterator();
        while (iter.hasNext()) {
            Notification n = iter.next();
            if (now - n.timestamp <= durationMs) continue;
            iter.remove();
        }
        int screenWidth = DisplaySpace.width();
        int screenHeight = DisplaySpace.height();
        int notifWidth = 160;
        int notifHeight = 30;
        int padding = 8;
        int y = screenHeight - padding - notifHeight;
        for (int i = 0; i < Math.min(notifications.size(), 5); ++i) {
            Notification n = notifications.get(i);
            float age = (float)(now - n.timestamp) / (float)durationMs;
            float alpha = 1.0f;
            if (age < 0.1f) {
                alpha = age / 0.1f;
            } else if (age > 0.8f) {
                alpha = (1.0f - age) / 0.2f;
            }
            int x = screenWidth - notifWidth - padding;
            if (age < 0.1f) {
                x += (int)((1.0f - age / 0.1f) * (float)(notifWidth + padding));
            }
            int accentColor = switch (n.type.ordinal()) {
                case 1 -> -13721533;
                case 2 -> -1854655;
                case 3 -> -39373;
                default -> 1725422816;
            };
            int panelColor = FastClientUI.withAlpha(-1475275496, (int)(alpha * 220.0f));
            int borderColor = FastClientUI.withAlpha(1725422816, (int)(alpha * 160.0f));
            int textColor = 0xFFFFFF | (int)(alpha * 255.0f) << 24;
            int subtextColor = 0xD1D5D8 | (int)(alpha * 255.0f) << 24;
            FastClientUI.roundedRect(graphics, x, y, notifWidth, notifHeight, 4, panelColor);
            FastClientUI.outline(graphics, x, y, notifWidth, notifHeight, borderColor);
            graphics.fill(x + 1, y + 1, x + 4, y + notifHeight - 1, FastClientUI.withAlpha(accentColor, (int)(alpha * 255.0f)));
            graphics.text(Notifications.mc.font, n.title, x + 8, y + 4, textColor, true);
            graphics.text(Notifications.mc.font, n.message, x + 8, y + 16, subtextColor, true);
            y -= notifHeight + 4;
        }
    }

    private static class Notification {
        String title;
        String message;
        NotificationType type;
        long timestamp;

        Notification(String title, String message, NotificationType type, long timestamp) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    public static enum NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR;

    }
}

