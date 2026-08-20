/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.store;

import java.util.Locale;

public enum CosmeticCategory {
    FEATURED("Featured", "general", new String[0]),
    CAPES("Cape", "cape", "CLOAKS"),
    WINGS("Wing", "wing", "ELYTRA"),
    HEAD("Head", "head", new String[0]),
    HATS("Hat", "hat", new String[0]),
    HANDS("Hand", "hand", "ARMS"),
    BOOTS("Boot", "boots", "LEGS"),
    BACKPACKS("Backpack", "backpack", "BACK_BLING"),
    AURAS("Aura", "aura", new String[0]),
    PETS("Pets", "pets", new String[0]),
    NAMETAGS("Tags", "social", new String[0]),
    OWNED("Owned", "general", new String[0]);

    private final String displayName;
    private final String iconName;
    private final String[] aliases;

    private CosmeticCategory(String displayName, String iconName, String ... aliases) {
        this.displayName = displayName;
        this.iconName = iconName;
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getIconName() {
        return this.iconName;
    }

    public static CosmeticCategory fromSerialized(String value) {
        if (value == null || value.isBlank()) {
            return FEATURED;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
            for (String alias : category.aliases) {
                if (!alias.equals(normalized)) continue;
                return category;
            }
        }
        return FEATURED;
    }

    public static CosmeticCategory refine(CosmeticCategory category, String displayName, String payload) {
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String slot = CosmeticCategory.payloadSlot(payload);
        if (category == HATS) {
            if (lowerName.contains("crown") || lowerName.contains("brim") || lowerName.contains("hat") || lowerName.contains("wizard")) {
                return HATS;
            }
            return HEAD;
        }
        if (category == BACKPACKS) {
            if (lowerName.contains("wing")) {
                return WINGS;
            }
            if (lowerName.contains("sword") || lowerName.contains("tome") || lowerName.contains("quiver") || "arm".equals(slot) || "hand".equals(slot)) {
                return HANDS;
            }
        }
        return category;
    }

    private static String payloadSlot(String payload) {
        if (payload == null) {
            return "";
        }
        int separator = payload.indexOf(58);
        String slot = separator > 0 ? payload.substring(0, separator) : payload;
        return slot.toLowerCase(Locale.ROOT);
    }
}

