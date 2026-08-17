package com.vayuclient.ui.mixin;

import com.vayuclient.ui.VayuClientUI;
import com.vayuclient.ui.core.IClientUIAdapter;
import com.vayuclient.ui.core.VayuUIAdapterFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {LoadingOverlay.class})
public class LoadingOverlayMixin {

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!VayuClientUI.isEnabled()) return;

        try {
            int width = this.minecraft.getWindow().getGuiScaledWidth();
            int height = this.minecraft.getWindow().getGuiScaledHeight();

            float progress = this.reload != null ? this.reload.getActualProgress() : 0.5f;
            String status = "Initializing Minecraft Engine...";
            if (progress > 0.8f) {
                status = "Finalizing VayuClient Setup...";
            } else if (progress > 0.5f) {
                status = "Loading Textures & Assets...";
            } else if (progress > 0.2f) {
                status = "Loading Mods & Shaders...";
            }

            IClientUIAdapter adapter = VayuUIAdapterFactory.getActiveAdapter();
            if (adapter != null) {
                adapter.renderLoadingScreen(graphics, width, height, progress, status, delta);
                ci.cancel();
            }
        } catch (Throwable t) {
            // Fall back to vanilla rendering if anything goes wrong
        }
    }
}
