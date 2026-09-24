package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Partícula del efecto de Corrupción (potion effect) -- reemplaza al
 * remolino ambiente automático de Minecraft (no se pudo pintar del tono
 * pedido de forma confiable, así que ahora Corrupción dibuja su propia
 * partícula en vez de depender de eso). Mismo tono cian-verde que el
 * wave del NecroticKnight -- #9cf6de, ver
 * NecroticWaveRenderer#COLOR_R/G/B -- para que la pasiva y el ataque se
 * sientan del mismo paquete visual.
 *
 * ANIMACIÓN DE 3 CUADROS -- corruption_particle_0/1/2.png (ver
 * assets/.../particles/corruption_particle.json, que lista los 3 en
 * orden). Cada textura ES un estado de la animación (puntito -> crece ->
 * se abre), así que acá el quadSize se queda fijo -- es el ARTE el que
 * crece, no el código. setSpriteFromAge(sprites) ya interpola sola entre
 * los 3 cuadros en función de age/lifetime; con LIFETIME_TICKS corto,
 * eso da ~3-4 fps como se pidió.
 *
 * SIN TINTE POR CÓDIGO -- a diferencia de otras partículas del mod, acá
 * NO se llama a setColor(): el color que se ve es el que ya tiene el PNG,
 * tal cual. Los 3 placeholders vienen pintados del tono #9cf6de
 * directamente en la imagen (no en blanco esperando tinte); el arte
 * final puede traer el color que quiera, se va a ver exactamente ese.
 *
 * PLACEHOLDER: las 3 texturas actuales las generé yo (puntos blancos
 * radiales simples, sin arte) solo para tener la animación funcionando
 * ya. Para poner el arte final: pisar los 3 PNG con el mismo nombre y
 * tamaño (16x16 -- se puede agrandar el canvas sin problema, pero
 * mantené los 3 frames en 3 archivos separados como están, no hace
 * falta spritesheet ni .mcmeta).
 *
 * Sin trazo -- casi no se mueve (una deriva mínima hacia arriba) y muere
 * en el mismo lugar donde nació.
 */
public class CorruptionParticle extends TextureSheetParticle {

    /** 3 cuadros a ~5 ticks c/u (~4 fps) -- animación completa en una sola vida de partícula. */
    private static final int LIFETIME_TICKS = 15;
    private static final float QUAD_SIZE = 0.30F;
    private static final int FADE_OUT_TICKS = 4;

    private final SpriteSet sprites;

    protected CorruptionParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.gravity = 0.0F;
        this.lifetime = LIFETIME_TICKS;
        this.quadSize = QUAD_SIZE;

        // Sin tinte por código -- el color lo trae la textura tal cual
        // (los placeholders ya vienen pintados del tono #9cf6de; cuando
        // se reemplacen por el arte final, el color que tenga ESE PNG es
        // el que se va a ver).

        // Casi sin movimiento -- una deriva mínima hacia arriba (como
        // energía escapando), nada que deje un trazo visible.
        this.xd = 0.0D;
        this.yd = 0.01D + this.random.nextDouble() * 0.01D;
        this.zd = 0.0D;

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
            return new CorruptionParticle(level, x, y, z, this.sprites);
        }
    }
}