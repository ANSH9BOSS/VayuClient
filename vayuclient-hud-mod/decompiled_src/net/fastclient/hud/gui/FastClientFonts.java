/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FontDescription
 *  net.minecraft.network.chat.FontDescription$Resource
 *  net.minecraft.network.chat.Style
 *  net.minecraft.resources.Identifier
 */
package net.fastclient.hud.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class FastClientFonts {
    private static final Typeface ACTIVE_TYPEFACE = Typeface.MINECRAFT_DEFAULT;
    private static final float FIXED_MINECRAFT_UI_SCALE = 2.0f;
    private static final FontDescription.Resource INTER_SEMIBOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"inter-semibold"));
    private static final FontDescription.Resource INTER_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"inter-bold"));
    private static final FontDescription.Resource INTER_BOLD_14 = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"inter-bold-14"));
    private static final FontDescription.Resource MINECRAFT_UNIFORM = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"minecraft", (String)"uniform"));
    private static final FontDescription.Resource MATERIAL_SYMBOLS_ROUNDED = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"material-symbols-rounded"));
    private static final FontDescription.Resource MATERIAL_SYMBOLS_ROUNDED_FILLED = new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"material-symbols-rounded-filled"));

    private FastClientFonts() {
    }

    public static Component body(String text) {
        return FastClientFonts.styled(text, TextRole.BODY);
    }

    public static Component strong(String text) {
        return FastClientFonts.styled(text, TextRole.STRONG);
    }

    public static Component title(String text) {
        return FastClientFonts.styled(text, TextRole.TITLE);
    }

    public static Component moduleName(String text) {
        return FastClientFonts.body(text);
    }

    public static Typeface activeTypeface() {
        return ACTIVE_TYPEFACE;
    }

    public static float bodyScale() {
        return FastClientFonts.configuredUiScale();
    }

    public static float strongScale() {
        return FastClientFonts.configuredUiScale();
    }

    public static float titleScale() {
        return FastClientFonts.configuredUiScale();
    }

    private static float configuredUiScale() {
        return ACTIVE_TYPEFACE == Typeface.MINECRAFT_DEFAULT ? 2.0f : 1.0f;
    }

    private static Component styled(String text, TextRole role) {
        Style style;
        if (ACTIVE_TYPEFACE == Typeface.MINECRAFT_DEFAULT) {
            style = Style.EMPTY;
        } else if (ACTIVE_TYPEFACE == Typeface.MINECRAFT_UNIFORM) {
            style = Style.EMPTY.withFont((FontDescription)MINECRAFT_UNIFORM);
            if (role != TextRole.BODY) {
                style = style.withBold(Boolean.valueOf(true));
            }
        } else {
            FontDescription.Resource font = switch (role.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> INTER_SEMIBOLD;
                case 1 -> INTER_BOLD;
                case 2 -> INTER_BOLD_14;
            };
            style = Style.EMPTY.withFont((FontDescription)font);
        }
        return Component.literal((String)text).withStyle(style);
    }

    public static Component materialSymbol(String symbol) {
        return Component.literal((String)symbol).withStyle(Style.EMPTY.withFont((FontDescription)MATERIAL_SYMBOLS_ROUNDED));
    }

    public static Component filledMaterialSymbol(String symbol) {
        return Component.literal((String)symbol).withStyle(Style.EMPTY.withFont((FontDescription)MATERIAL_SYMBOLS_ROUNDED_FILLED));
    }

    public static Component materialSymbolLabel(String symbol, String label) {
        return Component.empty().append(FastClientFonts.materialSymbol(symbol)).append((Component)Component.literal((String)(" " + label)));
    }

    public static Component filledMaterialSymbolLabel(String symbol, String label) {
        return Component.empty().append(FastClientFonts.filledMaterialSymbol(symbol)).append((Component)Component.literal((String)(" " + label)));
    }

    private static enum TextRole {
        BODY,
        STRONG,
        TITLE;

    }

    public static enum Typeface {
        INTER,
        MINECRAFT_DEFAULT,
        MINECRAFT_UNIFORM;

    }

    public static final class Symbols {
        public static final String ALIGN_HORIZONTAL_CENTER = "\ue00f";
        public static final String ALIGN_VERTICAL_CENTER = "\ue011";
        public static final String APPS = "\ue5c3";
        public static final String ARROW_BACK = "\ue5c4";
        public static final String CHECKROOM = "\uf19e";
        public static final String CLOSE = "\ue5cd";
        public static final String DASHBOARD = "\ue871";
        public static final String DASHBOARD_CUSTOMIZE = "\ue99b";
        public static final String DELETE = "\ue872";
        public static final String DIAMOND = "\uead5";
        public static final String DESKTOP_WINDOWS = "\ue30c";
        public static final String DIRECTIONS_RUN = "\ue566";
        public static final String FAVORITE = "\ue87e";
        public static final String FOLDER_OPEN = "\ue2c8";
        public static final String FORUM = "\ue8af";
        public static final String GRID_VIEW = "\ue9b0";
        public static final String GROUP = "\uea21";
        public static final String HANDYMAN = "\uf10b";
        public static final String LOGOUT = "\ue9ba";
        public static final String MANAGE_ACCOUNTS = "\uf02e";
        public static final String PERSON = "\uf0d3";
        public static final String PHOTO_CAMERA = "\ue412";
        public static final String REPORT = "\ue160";
        public static final String REMOVE = "\ue15b";
        public static final String RESTART_ALT = "\uf053";
        public static final String SEARCH = "\uef7a";
        public static final String SETTINGS = "\ue8b8";
        public static final String SPACE_DASHBOARD = "\ue66b";
        public static final String SPEED = "\ue9e4";
        public static final String BAR_CHART = "\ue26b";
        public static final String EMOJI_EVENTS = "\uea23";
        public static final String STOREFRONT = "\uea12";
        public static final String VISIBILITY = "\ue8f4";

        private Symbols() {
        }
    }
}

