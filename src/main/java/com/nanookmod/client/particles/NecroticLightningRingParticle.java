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
 * Anillo de energía plano (billboard horizontal, como una onda en el
 * suelo) que CRECE o SE ENCOGE a lo largo de su vida, con 7 frames que
 * ciclan para dar el efecto de "shimmer" eléctrico.
 *
 * Equivalente directo al Circle.RingData del Obliterator (circle_0..6.png).
 * Usalo así:
 *   - GROW:   arranca chico y termina grande (telegraph de que algo va a pasar)
 *   - SHRINK: arranca grande y se cierra (eco de que el ataque ya salió)
 *
 * Para elegir GROW o SHRINK, seteá growing en el Provider (ver más abajo)
 * o directamente clonate esta clase en dos variantes si preferís no tocar
 * el Provider cada vez.
 */
public class NecroticLightningRingParticle extends TextureSheetParticle {

    private static final float START_SIZE_GROW = 0.05F;
    private static final float END_SIZE_GROW = 2.4F;
    private static final float START_SIZE_SHRINK = 2.4F;
    private static final float END_SIZE_SHRINK = 0.05F;

    private final boolean growing;
    private final SpriteSet sprites;

    protected NecroticLightningRingParticle(ClientLevel level, double x, double y, double z,
                                            SpriteSet sprites, boolean growing, int lifetimeTicks) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.growing = growing;
        this.gravity = 0.0F;
        this.lifetime = lifetimeTicks;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        // Sin tinte -- se muestra la textura tal cual (ya la pintaste vos).
        this.setSpriteFromAge(sprites);
        this.updateSize(0.0F);
    }

    private void updateSize(float ageFrac) {
        float from = this.growing ? START_SIZE_GROW : START_SIZE_SHRINK;
        float to = this.growing ? END_SIZE_GROW : END_SIZE_SHRINK;
        this.quadSize = Mth.lerp(ageFrac, from, to);
    }

    @Override
    public int getLightColor(float partialTick) {
        // Fuerza el componente de luz de BLOQUE al máximo (0xF0), sin
        // importar si hay antorchas/luz cerca -- así el anillo siempre se
        // ve brillante por sí mismo. El componente de luz de CIELO se deja
        // como lo calcula el juego normalmente (& 0xFF0000), para que de
        // noche a cielo abierto no se vea artificialmente igual que de día
        // -- mismo criterio que usa Circle (la clase original de este
        // efecto en el mod de referencia).
        return 0xF0 | (super.getLightColor(partialTick) & 0xFF0000);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float ageFrac = this.lifetime <= 0 ? 1.0F : (float) this.age / (float) this.lifetime;
        this.updateSize(ageFrac);
        // Fade out sobre el último 25% de vida.
        this.alpha = ageFrac > 0.75F ? Mth.clamp((1.0F - ageFrac) / 0.25F, 0.0F, 1.0F) : 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Dos providers -- uno para GROW y otro para SHRINK -- así podés
     * registrar dos ParticleTypes separados (necrotic_lightning_ring_grow /
     * _shrink) y no tenés que pasar un flag por fuera del sistema de
     * partículas vanilla.
     */
    public static class GrowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GrowProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new NecroticLightningRingParticle(level, x, y, z, this.sprites, true, 20);
        }
    }

    public static class ShrinkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ShrinkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new NecroticLightningRingParticle(level, x, y, z, this.sprites, false, 14);
        }
    }
}