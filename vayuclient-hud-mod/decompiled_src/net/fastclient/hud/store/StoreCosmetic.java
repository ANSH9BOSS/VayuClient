/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonObject
 */
package net.fastclient.hud.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.fastclient.hud.store.CosmeticCategory;

public class StoreCosmetic {
    private final String id;
    private final String displayName;
    private final CosmeticCategory category;
    private final Rarity rarity;
    private final int price;
    private final String description;
    private final String previewAssetName;
    private final String previewUrl;
    private final String assetUrl;
    private final String textureUrl;
    private final String modelUrl;
    private final String attachment;
    private final double[] offset;
    private final int frames;
    private final int ticksPerFrame;
    private final int flags;
    private final boolean usesUvRotations;
    private final String cosmeticaPayload;
    private final boolean supportsMultipleEquip;

    public StoreCosmetic(String id, String displayName, CosmeticCategory category, Rarity rarity, int price, String description, String previewAssetName, String cosmeticaPayload, boolean supportsMultipleEquip) {
        this(id, displayName, category, rarity, price, description, previewAssetName, null, null, cosmeticaPayload, supportsMultipleEquip);
    }

    public StoreCosmetic(String id, String displayName, CosmeticCategory category, Rarity rarity, int price, String description, String previewAssetName, String previewUrl, String assetUrl, String cosmeticaPayload, boolean supportsMultipleEquip) {
        this(id, displayName, category, rarity, price, description, previewAssetName, previewUrl, assetUrl, null, null, null, new double[0], 1, 5, 0, false, cosmeticaPayload, supportsMultipleEquip);
    }

    public StoreCosmetic(String id, String displayName, CosmeticCategory category, Rarity rarity, int price, String description, String previewAssetName, String previewUrl, String assetUrl, String textureUrl, String modelUrl, String attachment, double[] offset, int frames, int ticksPerFrame, int flags, boolean usesUvRotations, String cosmeticaPayload, boolean supportsMultipleEquip) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.rarity = rarity;
        this.price = price;
        this.description = description;
        this.previewAssetName = previewAssetName;
        this.previewUrl = previewUrl;
        this.assetUrl = assetUrl;
        this.textureUrl = textureUrl;
        this.modelUrl = modelUrl;
        this.attachment = attachment;
        this.offset = offset == null ? new double[]{} : (double[])offset.clone();
        this.frames = frames;
        this.ticksPerFrame = ticksPerFrame;
        this.flags = flags;
        this.usesUvRotations = usesUvRotations;
        this.cosmeticaPayload = cosmeticaPayload;
        this.supportsMultipleEquip = supportsMultipleEquip;
    }

    public static StoreCosmetic fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String displayName = obj.get("displayName").getAsString();
        String payload = obj.get("payload").getAsString();
        CosmeticCategory category = CosmeticCategory.refine(CosmeticCategory.fromSerialized(obj.get("category").getAsString()), displayName, payload);
        Rarity rarity = Rarity.valueOf(obj.get("rarity").getAsString());
        int price = obj.get("price").getAsInt();
        String description = obj.get("description").getAsString();
        String previewUrl = StoreCosmetic.jsonStringOrNull(obj, "previewUrl");
        String assetUrl = StoreCosmetic.jsonStringOrNull(obj, "assetUrl");
        String textureUrl = StoreCosmetic.jsonStringOrNull(obj, "textureUrl");
        String modelUrl = StoreCosmetic.jsonStringOrNull(obj, "modelUrl");
        String attachment = StoreCosmetic.jsonStringOrNull(obj, "attachment");
        double[] offset = StoreCosmetic.jsonDoubleArray(obj, "offset");
        int frames = StoreCosmetic.jsonIntOrDefault(obj, "frames", 1);
        int ticksPerFrame = StoreCosmetic.jsonIntOrDefault(obj, "ticksPerFrame", 5);
        int flags = StoreCosmetic.jsonIntOrDefault(obj, "flags", 0);
        boolean usesUvRotations = obj.has("usesUvRotations") && obj.get("usesUvRotations").getAsBoolean();
        boolean supportsMultiple = obj.has("supportsMultipleEquip") && obj.get("supportsMultipleEquip").getAsBoolean();
        String previewAssetName = StoreCosmetic.extractPreviewAssetName(id, previewUrl);
        return new StoreCosmetic(id, displayName, category, rarity, price, description, previewAssetName, previewUrl, assetUrl, textureUrl, modelUrl, attachment, offset, frames, ticksPerFrame, flags, usesUvRotations, payload, supportsMultiple);
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public CosmeticCategory getCategory() {
        return this.category;
    }

    public Rarity getRarity() {
        return this.rarity;
    }

    public int getPrice() {
        return this.price;
    }

    public String getDescription() {
        return this.description;
    }

    public String getPreviewAssetName() {
        return this.previewAssetName;
    }

    public String getPreviewUrl() {
        return this.previewUrl;
    }

    public String getAssetUrl() {
        return this.assetUrl;
    }

    public String getTextureUrl() {
        return this.textureUrl;
    }

    public String getModelUrl() {
        return this.modelUrl;
    }

    public String getAttachment() {
        return this.attachment;
    }

    public double[] getOffset() {
        return (double[])this.offset.clone();
    }

    public int getFrames() {
        return this.frames;
    }

    public int getTicksPerFrame() {
        return this.ticksPerFrame;
    }

    public int getFlags() {
        return this.flags;
    }

    public boolean usesUvRotations() {
        return this.usesUvRotations;
    }

    public String getCosmeticaPayload() {
        return this.cosmeticaPayload;
    }

    public boolean supportsMultipleEquip() {
        return this.supportsMultipleEquip;
    }

    public String getSlot() {
        int separator = this.cosmeticaPayload.indexOf(58);
        String slot = separator <= 0 ? this.category.name().toLowerCase(Locale.ROOT) : this.cosmeticaPayload.substring(0, separator);
        return StoreCosmetic.normalizeSlot(slot, this.category);
    }

    public String getPayloadId() {
        int separator = this.cosmeticaPayload.indexOf(58);
        if (separator < 0 || separator + 1 >= this.cosmeticaPayload.length()) {
            return this.cosmeticaPayload;
        }
        return this.cosmeticaPayload.substring(separator + 1);
    }

    public boolean hasAccessoryMetadata() {
        return this.textureUrl != null && !this.textureUrl.isBlank() && this.modelUrl != null && !this.modelUrl.isBlank() && this.attachment != null && !this.attachment.isBlank();
    }

    public boolean conflictsWith(StoreCosmetic other) {
        if (other == null || this.id.equals(other.id)) {
            return true;
        }
        if ("nametag".equals(this.getSlot()) && "nametag".equals(other.getSlot())) {
            return false;
        }
        if (this.supportsMultipleEquip && other.supportsMultipleEquip()) {
            return false;
        }
        return this.getSlot().equals(other.getSlot());
    }

    private static String extractPreviewAssetName(String id, String previewUrl) {
        if (previewUrl != null && !previewUrl.isEmpty()) {
            String name = previewUrl.substring(previewUrl.lastIndexOf(47) + 1);
            int dot = name.lastIndexOf(46);
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            return name.toLowerCase(Locale.ROOT);
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private static String normalizeSlot(String slot, CosmeticCategory category) {
        String normalized = slot.toLowerCase(Locale.ROOT);
        if (category == CosmeticCategory.HANDS && "back".equals(normalized)) {
            return "arm";
        }
        if (category == CosmeticCategory.PETS) {
            return "pet";
        }
        return switch (normalized) {
            case "cape", "capes" -> "cloak";
            case "wing", "wings" -> "elytra";
            case "head", "hats" -> "hat";
            case "hand", "hands" -> "arm";
            case "boot", "boots" -> "leg";
            case "backpack", "backpacks" -> "back";
            case "auras" -> "aura";
            case "nametags", "tags" -> "nametag";
            case "pet", "pets" -> "pet";
            default -> normalized;
        };
    }

    private static String jsonStringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static int jsonIntOrDefault(JsonObject obj, String key, int fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsInt();
    }

    private static double[] jsonDoubleArray(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonArray()) {
            return new double[0];
        }
        JsonArray arr = obj.getAsJsonArray(key);
        double[] values = new double[arr.size()];
        for (int i = 0; i < arr.size(); ++i) {
            values[i] = arr.get(i).getAsDouble();
        }
        return values;
    }

    public static enum Rarity {
        COMMON("Common", -7303024),
        UNCOMMON("Uncommon", -11141291),
        RARE("Rare", -11184641),
        EPIC("Epic", -5614081),
        LEGENDARY("Legendary", -22016),
        FOUNDER("Founder", -39373);

        private final String displayName;
        private final int color;

        private Rarity(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getColor() {
            return this.color;
        }
    }
}

