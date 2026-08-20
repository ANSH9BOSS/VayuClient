/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.SkinTextureDownloader
 *  net.minecraft.core.ClientAsset$Texture
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.entity.player.PlayerModelType
 *  net.minecraft.world.entity.player.PlayerSkin
 */
package net.fastclient.hud.appearance;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.loader.api.FabricLoader;
import net.fastclient.hud.appearance.PlayerAppearanceCache;
import net.fastclient.hud.network.FastClientUserCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public final class PlayerAppearanceService {
    private static final PlayerAppearanceService INSTANCE = new PlayerAppearanceService();
    private final PlayerAppearanceCache<ClientAsset.Texture> cache;

    public static PlayerAppearanceService getInstance() {
        return INSTANCE;
    }

    private PlayerAppearanceService() {
        Path cacheDirectory = FabricLoader.getInstance().getGameDir().resolve("fastclient-appearance-cache");
        this.cache = new PlayerAppearanceCache(cacheDirectory, username -> FastClientUserCache.getInstance().isFastClientUser(username), this::registerTexture);
    }

    public PlayerSkin resolve(PlayerSkin mojang, String exactUsername) {
        ClientAsset.Texture elytra;
        PlayerAppearanceCache.Appearance<ClientAsset.Texture> appearance = this.cache.resolve(exactUsername);
        if (appearance.skin() == null && appearance.cape() == null) {
            return mojang;
        }
        ClientAsset.Texture skin = appearance.skin() != null ? appearance.skin() : mojang.body();
        ClientAsset.Texture cape = appearance.cape() != null ? appearance.cape() : mojang.cape();
        ClientAsset.Texture texture = elytra = appearance.cape() != null ? appearance.cape() : mojang.elytra();
        PlayerModelType model = appearance.skin() == null ? mojang.model() : (appearance.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        return new PlayerSkin(skin, cape, elytra, model, mojang.secure());
    }

    public void setEnabled(boolean enabled) {
        this.cache.setEnabled(enabled);
    }

    private CompletableFuture<ClientAsset.Texture> registerTexture(PlayerAppearanceCache.TextureKind kind, String cacheKey, Path file, String sourceUrl) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier id = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)("appearance/" + kind.name().toLowerCase(Locale.ROOT) + "/" + cacheKey));
        SkinTextureDownloader downloader = new SkinTextureDownloader(minecraft.getProxy(), minecraft.getTextureManager(), (Executor)minecraft);
        return downloader.downloadAndRegisterSkin(id, file, sourceUrl, kind == PlayerAppearanceCache.TextureKind.SKIN);
    }
}

