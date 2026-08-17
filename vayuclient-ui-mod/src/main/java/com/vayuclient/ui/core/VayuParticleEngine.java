package com.vayuclient.ui.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Random;

public final class VayuParticleEngine {
    private static final int MAX_PARTICLES = 45;
    private final float[] posX = new float[MAX_PARTICLES];
    private final float[] posY = new float[MAX_PARTICLES];
    private final float[] velX = new float[MAX_PARTICLES];
    private final float[] velY = new float[MAX_PARTICLES];
    private final float[] size = new float[MAX_PARTICLES];
    private final float[] alpha = new float[MAX_PARTICLES];
    private final int[] color = new int[MAX_PARTICLES];
    private boolean initialized = false;
    private final Random rand = new Random(42);

    public void init(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            posX[i] = rand.nextFloat() * screenWidth;
            posY[i] = rand.nextFloat() * screenHeight;
            velX[i] = (rand.nextFloat() - 0.5f) * 0.4f;
            velY[i] = -0.2f - rand.nextFloat() * 0.5f; // Gentle upward drift
            size[i] = 1.0f + rand.nextFloat() * 2.5f;
            alpha[i] = 0.2f + rand.nextFloat() * 0.6f;
            // Electric blue or subtle cyan
            color[i] = (rand.nextBoolean()) ? 0x00D2FF : 0x38BDF8;
        }
        initialized = true;
    }

    public void renderAndTick(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, float delta) {
        if (!initialized) {
            init(screenWidth, screenHeight);
            return;
        }

        for (int i = 0; i < MAX_PARTICLES; i++) {
            posX[i] += velX[i] * (delta > 0 ? delta : 1.0f);
            posY[i] += velY[i] * (delta > 0 ? delta : 1.0f);

            // Wrap bounds
            if (posY[i] < -10) {
                posY[i] = screenHeight + 5;
                posX[i] = rand.nextFloat() * screenWidth;
            }
            if (posX[i] < -10) posX[i] = screenWidth + 5;
            if (posX[i] > screenWidth + 10) posX[i] = -5;

            int a = (int)(alpha[i] * 180);
            int argb = (a << 24) | (color[i] & 0x00FFFFFF);
            int sz = (int)size[i];
            if (sz < 1) sz = 1;

            int px = (int)posX[i];
            int py = (int)posY[i];

            graphics.fill(px, py, px + sz, py + sz, argb);
        }
    }
}
