package com.vayuclient.ui.mixin;

import com.vayuclient.ui.VayuClientUI;
import com.vayuclient.ui.core.IClientUIAdapter;
import com.vayuclient.ui.core.VayuUIAdapterFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
    "net.minecraft.class_425",
    "net.minecraft.client.gui.screens.LoadingOverlay",
    "net.minecraft.client.gui.screen.SplashOverlay"
}, remap = false)
public class LoadingOverlayMixin {

    @Inject(method = {"extractRenderState", "render", "method_25394", "method_49601"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onExtractRenderState(CallbackInfo ci) {
        if (!VayuClientUI.isEnabled()) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            IClientUIAdapter adapter = VayuUIAdapterFactory.getActiveAdapter();
            if (adapter != null) {
                // Render VayuClient custom splash screen
                ci.cancel();
            }
        } catch (Throwable t) {
            // Fall back to vanilla rendering safely
        }
    }
}
