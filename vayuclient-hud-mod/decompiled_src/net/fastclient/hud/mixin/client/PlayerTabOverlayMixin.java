/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.ModifyReturnValue
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.PlayerTabOverlay
 *  net.minecraft.client.multiplayer.PlayerInfo
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FontDescription
 *  net.minecraft.network.chat.FontDescription$Resource
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.resources.Identifier
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.fastclient.hud.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.modules.impl.render.NametagIconModule;
import net.fastclient.hud.modules.impl.render.PingOverlay;
import net.fastclient.hud.network.FastClientUserCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerTabOverlay.class})
public class PlayerTabOverlayMixin {
    private static final String FC_ICON_CHAR = "\ue000";
    private static final Style FC_ICON_STYLE = Style.EMPTY.withFont((FontDescription)new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"icon")));

    @Inject(method={"extractPingIcon"}, at={@At(value="HEAD")}, cancellable=true)
    private void onRenderPingIcon(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        PingOverlay pingOverlay = mm.getModule(PingOverlay.class);
        if (pingOverlay == null || !pingOverlay.isEnabled()) {
            return;
        }
        ci.cancel();
        int latency = playerInfo.getLatency();
        String text = pingOverlay.formatPing(latency);
        int color = pingOverlay.getPingColor(latency);
        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(text);
        graphics.text(mc.font, text, x + width - textWidth - 1, y, color, true);
    }

    @ModifyReturnValue(method={"getNameForDisplay"}, at={@At(value="RETURN")})
    private Component onGetNameForDisplay(Component original, PlayerInfo playerInfo) {
        boolean isSelf;
        if (!NametagIconModule.shouldShowInTabList()) {
            return original;
        }
        String name = playerInfo.getProfile().name();
        Minecraft mc = Minecraft.getInstance();
        boolean bl = isSelf = mc.player != null && mc.player.getGameProfile().name().equals(name);
        if (!isSelf && !FastClientUserCache.getInstance().isFastClientUser(name)) {
            return original;
        }
        MutableComponent icon = Component.literal((String)FC_ICON_CHAR).withStyle(FC_ICON_STYLE);
        MutableComponent prefix = Component.literal((String)"").append((Component)icon).append((Component)Component.literal((String)" "));
        return prefix.append(original);
    }
}

