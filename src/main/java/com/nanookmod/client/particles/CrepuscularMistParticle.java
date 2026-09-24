package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Jirón de niebla baja del Bosque Crepuscular. La lanza
 * CrepuscularMistHandler, que calcula la altura real del piso en cada punto
 * (heightmap, no la altura del jugador) para que quede pegada al terreno en
 * vez de "flotando" cuando el suelo no es parejo.
 * No sube ni cae (gravity 0, yd 0), solo se desliza despacio en horizontal y
 * tiene un alfa bajo para no saturar la vista: es un detalle atmosférico, no
 * debe leerse como niebla densa.
 */
public class CrepuscularMistParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected CrepuscularMistParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 140 + this.random.nextInt(80);
        this.quadSize = 1.6F + this.random.nextFloat() * 1.4F;

        // Blanco-lavanda pálido, coherente con el tinte nocturno/crepuscular.
        this.setColor(0.86F, 0.85F, 0.94F);

        this.xd = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd = 0.0D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.01D;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        // Fade-in/fade-out suave. El pico de alfa se mantiene bajo a propósito
        // (niebla sutil dispersa, no una capa de fog sólida).
        int fadeWindow = 30;
        float lifeFade;
        if (this.age < fadeWindow) {
            lifeFade = (float) this.age / fadeWindow;
        } else if (this.age > this.lifetime - fadeWindow) {
            lifeFade = (float) (this.lifetime - this.age) / fadeWindow;
        } else {
            lifeFade = 1.0F;
        }
        this.alpha = Math.max(0.0F, lifeFade * 0.22F);
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
            return new CrepuscularMistParticle(level, x, y, z, this.sprites);
        }
    }
}
