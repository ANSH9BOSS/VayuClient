package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.NametagIconModule;
import com.vayuclient.hud.modules.impl.render.PingOverlay;
import com.vayuclient.hud.network.VayuUserCache;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerTabOverlay.class})
public class PlayerTabOverlayMixin {
    private static final String VAYU_ICON_CHAR = "\ue000";
    private static final Style VAYU_ICON_STYLE = Style.EMPTY.withFont((FontDescription)new FontDescription.Resource(Identifier.fromNamespaceAndPath("vayuclient-hud", "icon")));

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

    @Inject(method={"getNameForDisplay"}, at={@At(value="RETURN")}, cancellable=true)
    private void onGetNameForDisplay(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (!NametagIconModule.shouldShowInTabList()) {
            return;
        }
        String name = com.vayuclient.hud.utils.PlayerUtils.getProfileName(playerInfo.getProfile());
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && com.vayuclient.hud.utils.PlayerUtils.getProfileName(mc.player.getGameProfile()).equals(name);
        if (!isSelf && !VayuUserCache.getInstance().isVayuClientUser(name)) {
            return;
        }
        Component original = cir.getReturnValue();
        if (original == null) {
            original = Component.literal(name);
        }
        MutableComponent icon = Component.literal(VAYU_ICON_CHAR).withStyle(VAYU_ICON_STYLE);
        MutableComponent prefix = Component.literal("").append((Component)icon).append((Component)Component.literal(" "));
        cir.setReturnValue(prefix.append(original));
    }
}
