package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Partícula de "carga": nace lejos del punto de origen del rayo y VIAJA
 * hacia él en línea recta, encogiéndose/desvaneciéndose al llegar --
 * el efecto de "energía siendo absorbida" antes de disparar.
 *
 * Equivalente directo al BIG/SMALL_ANNIHILATION_FLAME del Obliterator
 * (ticks 10-30 de su windup).
 *
 * IMPORTANTE sobre cómo se llama: hay que spawnearla con count=0 en
 * sendParticles para que Minecraft interprete (dx,dy,dz) como VELOCIDAD
 * real en vez de como offset aleatorio -- ver el ejemplo de integración
 * más abajo (spawnChargeParticles). Con count>0 el motor ignora la
 * dirección y tira las partículas al azar dentro de una caja.
 */
public class NecroticLightningChargeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected NecroticLightningChargeParticle(ClientLevel level, double x, double y, double z,
                                              double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz); // el constructor de 7 args ya asigna xd/yd/zd = dx/dy/dz
        this.sprites = sprites;
        this.gravity = 0.0F;
        this.hasPhysics = false; // no choca contra bloques, atraviesa todo en línea recta
        this.lifetime = 12 + this.random.nextInt(6);
        this.quadSize = 0.35F;
        // Sin tinte -- se muestra la textura tal cual (ya la pintaste vos).
        this.setSpriteFromAge(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        // Mismo criterio que el anillo -- luz de bloque forzada al máximo,
        // luz de cielo respetada. Si no la querés acá, borrá este método.
        return 0xF0 | (super.getLightColor(partialTick) & 0xFF0000);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // Se va achicando a medida que "llega" al punto de origen.
        float ageFrac = (float) this.age / (float) this.lifetime;
        this.quadSize = 0.35F * (1.0F - ageFrac * 0.6F);
        this.alpha = ageFrac > 0.8F ? Math.max(0.0F, (1.0F - ageFrac) / 0.2F) : 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new NecroticLightningChargeParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}