package com.vayuclient.hud.loader;

public enum ModLoader {
    FABRIC("Fabric", true),
    QUILT("Quilt", true),
    NEOFORGE("NeoForge", true),
    FORGE("Forge", false),
    VANILLA("Vanilla", false);

    private final String displayName;
    private final boolean isSupported;

    ModLoader(String displayName, boolean isSupported) {
        this.displayName = displayName;
        this.isSupported = isSupported;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSupported() {
        return isSupported;
    }

    public static ModLoader fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return FABRIC;
        }
        String clean = name.trim().toLowerCase();
        if (clean.contains("quilt")) return QUILT;
        if (clean.contains("neoforge")) return NEOFORGE;
        if (clean.contains("forge")) return FORGE;
        if (clean.contains("vanilla")) return VANILLA;
        return FABRIC;
    }
}
