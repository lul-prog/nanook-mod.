package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Mota cálida tipo espora/luciérnaga del Bosque Crepuscular. Se usa tanto
 * como partícula ambiental del bioma (ver effects.particle en
 * bosque_crepuscular.json / bosque_crepuscular_river.json) como para
 * reforzar la zona alrededor de crepuscular_sprout (ver
 * CrepuscularSproutBlock#animateTick).
 *
 * Sube muy despacio, se desvía suavemente de lado a lado y parpadea (efecto
 * "luciérnaga") variando su alfa con una onda senoidal a lo largo de su vida.
 */
public class CrepuscularMoteParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final double swaySeed;

    protected CrepuscularMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.swaySeed = this.random.nextDouble() * Math.PI * 2.0D;

        this.gravity = 0.0F;
        this.lifetime = 80 + this.random.nextInt(60);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.05F;

        // Tono cálido dorado/verdoso, como una luciérnaga.
        float warmth = this.random.nextFloat() * 0.15F;
        this.setColor(0.867F + warmth * 0.02F, 0.910F - warmth * 0.02F, 0.871F + warmth * 0.02F);

        // Sube muy despacio, casi flotando.
        this.xd *= 0.1D;
        this.yd = 0.006D + this.random.nextDouble() * 0.01D;
        this.zd *= 0.1D;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        // Deriva lateral suave, como si el aire la empujara sin rumbo fijo.
        this.xd += Math.sin((this.age + this.swaySeed) * 0.08D) * 0.0006D;
        this.zd += Math.cos((this.age + this.swaySeed) * 0.065D) * 0.0006D;

        // Parpadeo tipo luciérnaga combinado con fade-in/fade-out en los extremos de su vida.
        float lifeFade = 1.0F;
        int fadeWindow = 10;
        if (this.age < fadeWindow) {
            lifeFade = (float) this.age / fadeWindow;
        } else if (this.age > this.lifetime - fadeWindow) {
            lifeFade = (float) (this.lifetime - this.age) / fadeWindow;
        }
        float flicker = 0.55F + 0.45F * (float) Math.sin((this.age + this.swaySeed) * 0.2D);
        this.alpha = Math.max(0.0F, lifeFade * flicker);
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
            return new CrepuscularMoteParticle(level, x, y, z, this.sprites);
        }
    }
}
