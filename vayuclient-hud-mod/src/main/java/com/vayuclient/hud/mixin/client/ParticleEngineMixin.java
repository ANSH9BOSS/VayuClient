/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.particle.Particle
 *  net.minecraft.client.particle.ParticleEngine
 *  net.minecraft.client.particle.SingleQuadParticle
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleType
 *  net.minecraft.core.particles.ParticleTypes
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.vayuclient.hud.mixin.client;

import java.util.Random;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.Particles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ParticleEngine.class})
public class ParticleEngineMixin {
    @Unique
    private static final Random vayuclient_random = new Random();
    @Unique
    private static final ThreadLocal<Boolean> vayuclient_isSpawningExtra = ThreadLocal.withInitial(() -> false);

    @Inject(method={"createParticle"}, at={@At(value="HEAD")}, cancellable=true)
    private <T extends ParticleOptions> void onCreateParticle(T particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        Particles module = mm.getModule(Particles.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        ParticleType type = particleData.getType();
        float multiplier = module.getMultiplier();
        if (multiplier < 1.0f && !vayuclient_isSpawningExtra.get().booleanValue() && vayuclient_random.nextFloat() > multiplier) {
            cir.setReturnValue(null);
            return;
        }
        if (this.shouldBlockParticle(module, type)) {
            cir.setReturnValue(null);
            return;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"createParticle"}, at={@At(value="RETURN")})
    private <T extends ParticleOptions> void afterCreateParticle(T particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        float multiplier;
        if (vayuclient_isSpawningExtra.get().booleanValue()) {
            return;
        }
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return;
        }
        Particles module = mm.getModule(Particles.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        Particle particle = (Particle)cir.getReturnValue();
        if (particle == null) {
            return;
        }
        ParticleType type = particleData.getType();
        if (module.useCustomCritColor() && (type == ParticleTypes.CRIT || type == ParticleTypes.DAMAGE_INDICATOR)) {
            int color = module.getCritColor();
            float r = (float)(color >> 16 & 0xFF) / 255.0f;
            float g = (float)(color >> 8 & 0xFF) / 255.0f;
            float b = (float)(color & 0xFF) / 255.0f;
            if (particle instanceof SingleQuadParticle) {
                SingleQuadParticle singleQuadParticle = (SingleQuadParticle)particle;
                singleQuadParticle.setColor(r, g, b);
            }
        }
        if ((multiplier = module.getMultiplier()) > 1.0f) {
            int extraCount = (int)((multiplier - 1.0f) * 2.0f);
            vayuclient_isSpawningExtra.set(true);
            try {
                ParticleEngine engine = (ParticleEngine)(Object)this;
                for (int i = 0; i < extraCount; ++i) {
                    double offsetX = (vayuclient_random.nextDouble() - 0.5) * 0.3;
                    double offsetY = (vayuclient_random.nextDouble() - 0.5) * 0.3;
                    double offsetZ = (vayuclient_random.nextDouble() - 0.5) * 0.3;
                    double velOffsetX = (vayuclient_random.nextDouble() - 0.5) * 0.1;
                    double velOffsetY = (vayuclient_random.nextDouble() - 0.5) * 0.1;
                    double velOffsetZ = (vayuclient_random.nextDouble() - 0.5) * 0.1;
                    engine.createParticle(particleData, x + offsetX, y + offsetY, z + offsetZ, xSpeed + velOffsetX, ySpeed + velOffsetY, zSpeed + velOffsetZ);
                }
            }
            finally {
                vayuclient_isSpawningExtra.set(false);
            }
        }
    }

    @Unique
    private boolean shouldBlockParticle(Particles module, ParticleType<?> type) {
        if (type == ParticleTypes.CRIT || type == ParticleTypes.DAMAGE_INDICATOR) {
            return !module.shouldShowCriticals();
        }
        if (type == ParticleTypes.ENCHANTED_HIT) {
            return !module.shouldShowEnchanted();
        }
        if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) {
            return !module.shouldShowExplosion();
        }
        if (type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT || type == ParticleTypes.ENTITY_EFFECT) {
            return !module.shouldShowPotion();
        }
        if (type == ParticleTypes.FIREWORK || type == ParticleTypes.FLASH) {
            return !module.shouldShowFirework();
        }
        if (type == ParticleTypes.TOTEM_OF_UNDYING) {
            return !module.shouldShowTotem();
        }
        if (type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {
            return !module.shouldShowSmoke();
        }
        if (type == ParticleTypes.FLAME || type == ParticleTypes.SOUL_FIRE_FLAME || type == ParticleTypes.SMALL_FLAME) {
            return !module.shouldShowFlame();
        }
        if (type == ParticleTypes.SPLASH || type == ParticleTypes.FISHING || type == ParticleTypes.BUBBLE || type == ParticleTypes.BUBBLE_POP || type == ParticleTypes.BUBBLE_COLUMN_UP || type == ParticleTypes.CURRENT_DOWN) {
            return !module.shouldShowWaterSplash();
        }
        if (type == ParticleTypes.RAIN || type == ParticleTypes.DRIPPING_WATER || type == ParticleTypes.FALLING_WATER || type == ParticleTypes.DRIPPING_DRIPSTONE_WATER || type == ParticleTypes.FALLING_DRIPSTONE_WATER) {
            return !module.shouldShowWeather();
        }
        return false;
    }
}

