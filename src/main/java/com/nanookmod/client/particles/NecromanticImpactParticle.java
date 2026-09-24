package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Estallido del golpe cargado de la Cadena Necromántica -- UNA sola
 * partícula, grande, clavada en el centro del objetivo, tipo "POW" de
 * cómic (no una nube de partículas chicas dispersas). El código solo la
 * planta ahí quieta; el crecimiento/desvanecido del estallido ya viene
 * animado en el propio arte (7 cuadros, necromantic_impact_1..7.png,
 * amarillo -> naranja -> rojo -> chispas finales) -- mismo criterio que
 * CorruptionParticle: el arte anima, el código no reescala.
 *
 * Sin movimiento (xd=yd=zd=0): no debe "flotar" ni dejar rastro, se queda
 * fija en el punto de impacto durante toda su vida.
 *
 * QUAD_SIZE grande a propósito (bastante más que las partículas normales
 * del mod, que rondan 0.2-0.3) para que cubra bien el torso del objetivo
 * como una onda de choque. Ajustable acá mismo si se ve muy grande/chica
 * en el juego real.
 */
public class NecromanticImpactParticle extends TextureSheetParticle {

    /** 7 cuadros a 3 ticks c/u = 21 ticks (~1s) de vida total. */
    private static final int LIFETIME_TICKS = 14;
    private static final float QUAD_SIZE = 2.0F;
    private static final int FADE_OUT_TICKS = 5;

    private final SpriteSet sprites;

    protected NecromanticImpactParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.gravity = 0.0F;
        this.lifetime = LIFETIME_TICKS;
        this.quadSize = QUAD_SIZE;

        // Sin tinte por código -- el color (amarillo->naranja->rojo) ya
        // viene pintado en los 7 PNG tal cual.

        // Clavada en el punto de impacto, no se mueve.
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;

        // Encara siempre a la cámara pero no rota con el viewpoint más
        // allá de eso -- comportamiento por defecto de TextureSheetParticle,
        // suficiente para un "flash" 2D estilo cómic.
        this.hasPhysics = false;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        int fadeStart = this.lifetime - FADE_OUT_TICKS;
        this.alpha = this.age > fadeStart
                ? Math.max(0.0F, (float) (this.lifetime - this.age) / FADE_OUT_TICKS)
                : 1.0F;
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
            return new NecromanticImpactParticle(level, x, y, z, this.sprites);
        }
    }
}
