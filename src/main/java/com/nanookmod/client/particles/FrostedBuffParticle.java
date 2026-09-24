package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Partícula simple que sale de los mobs potenciados por la Noche Escarchada:
 * nace en la posición del mob, sube despacio y se desvanece. Usa la textura
 * que tú pongas en assets/nanookmod/textures/particle/frosted_buff.png
 * (referenciada desde assets/nanookmod/particles/frosted_buff.json). Si esa
 * textura tiene varios cuadros apilados verticalmente (como la de la
 * estrella fugaz), automáticamente los anima a lo largo de su vida gracias a
 * setSpriteFromAge; si solo tiene un cuadro, se queda quieta en esa imagen.
 */
public class FrostedBuffParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected FrostedBuffParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.gravity = 0.0F;
        this.lifetime = 20 + this.random.nextInt(15);
        this.quadSize = 0.35F + this.random.nextFloat() * 0.15F;

        // Movimiento suave hacia arriba, casi sin deriva horizontal.
        this.xd *= 0.2D;
        this.yd = 0.03D + this.random.nextDouble() * 0.03D;
        this.zd *= 0.2D;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // Se va desvaneciendo hacia el final de su vida.
        this.alpha = 1.0F - (float) this.age / (float) this.lifetime;
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
            return new FrostedBuffParticle(level, x, y, z, this.sprites);
        }
    }
}