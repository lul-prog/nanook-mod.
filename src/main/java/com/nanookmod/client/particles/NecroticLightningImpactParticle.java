package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Destello/chispa de impacto: nace y muere en el mismo punto (no se
 * mueve), fullbright, cicla sus 8 frames en 12 ticks y desaparece.
 * Pensada para spawnearse VARIAS veces por tick mientras el rayo está
 * tocando algo -- la superposición de varios de estos da el efecto de
 * "parpadeo eléctrico" en el punto de contacto.
 *
 * Equivalente directo a AnnihilationExplosion del Obliterator
 * (annihilation_explosion_0..7.png, 64x64).
 */
public class NecroticLightningImpactParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected NecroticLightningImpactParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0); // sin movimiento -- nace y muere en el lugar
        this.sprites = sprites;
        this.lifetime = 12;
        this.quadSize = 1.4F; // el original usa 2.0F, bajalo/subilo a gusto
        this.setColor(1.0F, 1.0F, 1.0F);
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // fullbright -- se ve brillante sin importar la luz ambiental
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
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
            return new NecroticLightningImpactParticle(level, x, y, z, this.sprites);
        }
    }
}
