package com.vayuclient.ui.gui;

import com.vayuclient.ui.core.IClientUIAdapter;
import com.vayuclient.ui.core.VayuParticleEngine;
import com.vayuclient.ui.core.VayuUIAdapterFactory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class VayuScreen extends Screen {
    protected final VayuParticleEngine particles = new VayuParticleEngine();
    protected float transitionAlpha = 0.0f;
    protected IClientUIAdapter adapter;

    protected VayuScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.adapter = VayuUIAdapterFactory.getActiveAdapter();
        this.particles.init(this.width, this.height);
        this.transitionAlpha = 0.0f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        try {
            // 1. Smooth Transition Alpha (easeOutCubic)
            if (transitionAlpha < 1.0f) {
                transitionAlpha = Math.min(1.0f, transitionAlpha + (0.08f * (delta > 0 ? delta : 1.0f)));
            }

            // 2. Render Backdrop via Adapter
            if (adapter != null) {
                adapter.renderBackground(graphics, this.width, this.height, delta);
            } else {
                graphics.fill(0, 0, this.width, this.height, 0xFF080C14);
            }

            // 3. Render Floating Ambient Cyber Particles
            particles.renderAndTick(graphics, this.width, this.height, delta);

            // 4. Render Child Widgets / Buttons
            super.extractRenderState(graphics, mouseX, mouseY, delta);

            // 5. Render Screen Specific Foreground Overlays
            renderVayuForeground(graphics, mouseX, mouseY, delta);

        } catch (Throwable t) {
            System.err.println("[VayuClient UI] Screen render warning: " + t.getMessage());
            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    protected abstract void renderVayuForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
}
