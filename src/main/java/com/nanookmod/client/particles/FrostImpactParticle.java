package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Estallido azul de impacto (arte directional_impact_001_small_blue, 7
 * cuadros). Se usa cuando el METEORO de Nanook toca el suelo o a alguien:
 * un solo golpe visual grande, fullbright, que no se mueve y crece un
 * poquito mientras recorre sus cuadros.
 *
 * A diferencia de snowy_dust (que es polvo blanco y se achica), esta es la
 * partícula "azul" del jefe y se comporta al revés: se abre hacia afuera.
 */
public class FrostImpactParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float startQuadSize;

    protected FrostImpactParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;

        // 7 cuadros; 2 ticks por cuadro se lee bien sin parecer un flash.
        this.lifetime = 14;
        this.startQuadSize = 1.5F;
        this.quadSize = this.startQuadSize;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.setSpriteFromAge(sprites);
    }

    /** Crece ~40% a lo largo de su vida: da sensación de onda expansiva. */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        return this.startQuadSize * (1.0F + progress * 0.4F);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // fullbright: el impacto brilla aunque sea de noche
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
            return new FrostImpactParticle(level, x, y, z, this.sprites);
        }
    }
}
