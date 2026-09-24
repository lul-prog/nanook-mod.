package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Chispa -- se usa para la "evaporación" del wave del NecroticKnight:
 * cuando un anillo (NecroticWaveRenderer) termina de desvanecerse, deja
 * un par de estas flotando en su lugar (ver NecroticWaveTracker). Mismo
 * tono cian-verde que el anillo -- #9cf6de, ver
 * NecroticWaveRenderer#COLOR_R/G/B.
 *
 * NOTA histórica: esta clase originalmente era la chispa morada que
 * acompañaba al efecto de Corrupción -- se sacó de ahí (CorruptionEffect
 * usa partículas DUST vanilla ahora), pero se reaprovecha el esqueleto y
 * la textura (corruption_spark.png) que ya estaba de placeholder.
 *
 * No se mueve -- nace y muere en el mismo punto, sin dejar trazo. Arranca
 * como un puntito (quadSize 0) y CRECE a los saltos -- no de forma suave
 * -- hasta su tamaño final: un salto cada GROW_STEP_TICKS ticks, ~4 fps
 * (20 ticks/seg de Minecraft / 5 ticks por salto), para que se sienta
 * como una animación cuadro a cuadro. Como TextureSheetParticle dibuja un
 * quad cuadrado, este crecimiento ya es parejo en X e Y.
 */
public class CorruptionSparkParticle extends TextureSheetParticle {

    /** Tamaño final del quad (mismo en X e Y). */
    private static final float TARGET_QUAD_SIZE = 0.22F;
    /** Ticks entre cada salto de tamaño -- 5 ticks ~= 4 fps. */
    private static final int GROW_STEP_TICKS = 5;
    /** Cantidad de saltos hasta llegar al tamaño final. */
    private static final int GROW_STEPS = 4;
    /** Ticks de fundido de salida al final de la vida. */
    private static final int FADE_OUT_TICKS = 4;

    private final SpriteSet sprites;

    protected CorruptionSparkParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.gravity = 0.0F;
        this.lifetime = GROW_STEP_TICKS * GROW_STEPS + 8 + this.random.nextInt(6);
        this.quadSize = 0.0F;

        // Mismo tono que el anillo del wave (#9cf6de), con una variación
        // chica para que un puñado de chispas juntas no se vean clonadas.
        float variance = this.random.nextFloat() * 0.10F;
        this.setColor(0.6118F - variance, 0.9647F - variance * 0.4F, 0.8706F - variance * 0.4F);

        // Sin velocidad -- nace y crece en el lugar, no deja trazo detrás.
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        // Crecimiento a los saltos (no interpolado) de puntito a tamaño final.
        int step = Math.min(GROW_STEPS, this.age / GROW_STEP_TICKS);
        this.quadSize = TARGET_QUAD_SIZE * step / (float) GROW_STEPS;

        // Fundido de salida sobre el último tramo de vida.
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
            return new CorruptionSparkParticle(level, x, y, z, this.sprites);
        }
    }
}