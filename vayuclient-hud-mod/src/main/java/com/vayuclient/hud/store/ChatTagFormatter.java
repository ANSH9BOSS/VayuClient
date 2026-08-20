/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.store;

import java.util.List;
import java.util.Locale;
import com.vayuclient.hud.store.StoreCosmetic;
import com.vayuclient.hud.store.StoreManager;

public final class ChatTagFormatter {
    private static final int MAX_CHAT_LENGTH = 256;

    private ChatTagFormatter() {
    }

    public static String decorateOutgoingMessage(String message) {
        if (message == null || message.isBlank() || message.startsWith("/")) {
            return message;
        }
        List<StoreCosmetic> tags = StoreManager.getInstance().getEquippedChatTags();
        if (tags.isEmpty()) {
            return message;
        }
        String prefix = ChatTagFormatter.buildPrefix(tags);
        if (prefix.isEmpty() || message.startsWith(prefix)) {
            return message;
        }
        int maxBodyLength = 256 - prefix.length();
        if (maxBodyLength <= 0) {
            return ChatTagFormatter.trimToChatLength(prefix.stripTrailing());
        }
        String body = message.length() > maxBodyLength ? message.substring(0, maxBodyLength) : message;
        return prefix + body;
    }

    private static String buildPrefix(List<StoreCosmetic> tags) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (StoreCosmetic tag : tags) {
            if (count >= 3) break;
            String label = ChatTagFormatter.tagLabel(tag);
            if (label.isBlank()) continue;
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append('[').append(label).append(']');
            ++count;
        }
        if (builder.isEmpty()) {
            return "";
        }
        return builder.append(' ').toString();
    }

    private static String tagLabel(StoreCosmetic cosmetic) {
        String key;
        return switch (key = cosmetic.getPayloadId().toLowerCase(Locale.ROOT)) {
            case "founder" -> "FOUNDER";
            case "vip_glow", "vip" -> "VIP";
            case "champion" -> "CHAMPION";
            case "builder" -> "BUILDER";
            case "creator" -> "CREATOR";
            case "speedrunner" -> "SPEEDRUN";
            case "duelist" -> "DUELIST";
            case "explorer" -> "EXPLORER";
            case "mythic" -> "MYTHIC";
            case "minimal" -> "MIN";
            case "crystal" -> "CRYSTAL";
            default -> ChatTagFormatter.cleanLabel(cosmetic.getDisplayName());
        };
    }

    private static String cleanLabel(String displayName) {
        String cleaned = displayName == null ? "" : displayName;
        cleaned = cleaned.replace("Nameplate", "").replace("nameplate", "");
        cleaned = cleaned.replace("Tag", "").replace("tag", "");
        cleaned = cleaned.replace("Glow", "").replace("glow", "");
        if ((cleaned = cleaned.replaceAll("[^A-Za-z0-9 ]", "").trim().replaceAll("\\s+", " ")).isBlank()) {
            return "";
        }
        String upper = cleaned.toUpperCase(Locale.ROOT);
        return upper.length() > 12 ? upper.substring(0, 12) : upper;
    }

    private static String trimToChatLength(String message) {
        return message.length() > 256 ? message.substring(0, 256) : message;
    }
}

