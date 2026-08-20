/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules;

public enum Category {
    HUD("HUD", "hud", "Display elements on your screen", "\ud83d\udcca"),
    RENDER("Render", "render", "Visual effects and graphics", "\ud83c\udfa8"),
    MOVEMENT("Movement", "movement", "Speed and mobility options", "\ud83c\udfc3"),
    PLAYER("Player", "player", "Character customization", "\ud83d\udc64"),
    UTILITY("Utility", "utility", "Helpful tools and features", "\ud83d\udd27");

    private final String displayName;
    private final String id;
    private final String description;
    private final String icon;

    private Category(String displayName, String id, String description, String icon) {
        this.displayName = displayName;
        this.id = id;
        this.description = description;
        this.icon = icon;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getId() {
        return this.id;
    }

    public String getDescription() {
        return this.description;
    }

    public String getIcon() {
        return this.icon;
    }

    public String getFormattedName() {
        return this.icon + " " + this.displayName;
    }
}

