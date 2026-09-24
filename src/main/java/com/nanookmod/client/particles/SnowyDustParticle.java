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
 * Polvo/nieve de Nanook (snowy_dust).
 *
 * -------------------------------------------------------------------
 * CAMBIO DE COMPORTAMIENTO (pedido: "como la partícula que hace Chesed
 * después del rayo, pero moviéndose hacia adelante")
 * -------------------------------------------------------------------
 * Antes era una partícula CHICA con gravedad positiva: nacía, caía como
 * una piedrita y se apagaba. Se leía como ceniza, no como una bocanada.
 *
 * Ahora se comporta como una BOCANADA DE HUMO/NIEVE proyectada:
 *
 *   1. Nace GRANDE y sigue creciendo durante el primer tercio de su vida
 *      (EXPAND_UNTIL). Es lo que hace que se lea como "una nube que se
 *      abre", no como un puntito.
 *   2. NO tiene gravedad (gravity = 0). Lo único que la mueve es la
 *      velocidad con la que la spawneaste, así que si la lanzás hacia
 *      adelante, sale volando hacia adelante -- que es exactamente la
 *      diferencia que pediste respecto a la de Chesed, que se queda
 *      flotando en el sitio.
 *   3. Frena progresivamente (friction = 0.90). Sale disparada, pierde
 *      fuerza y se queda deshaciéndose en el aire.
 *   4. Los 9 cuadros de arte se reparten a lo largo de TODA la vida
 *      (setSpriteFromAge), así que el ciclo se lee como "la bocanada se
 *      deshace", no como un loop.
 *   5. Se desvanece por alfa en la segunda mitad, sin encogerse de golpe.
 *
 * Si querés la versión vieja (polvo que cae al piso) para algún efecto
 * puntual, no toques esta clase: spawneala con velocidad -Y y subí
 * FALL_BIAS. Está todo en constantes acá arriba a propósito.
 *
 * -------------------------------------------------------------------
 * SEGUNDA RONDA: GRIS AL FINAL Y TRANSPARENCIA AL SOLAPARSE
 * -------------------------------------------------------------------
 * Las texturas son blanco puro; el gris venía de la LUZ (la nube se hunde
 * en el terreno y el motor de luz le da 0) y lo "transparente" de la
 * ESCRITURA DE PROFUNDIDAD del render type vanilla. Las dos correcciones
 * están en SnowyParticleUtil, con la explicación completa.
 *
 * -------------------------------------------------------------------
 * SOBRE EL "FONDO NEGRO" QUE VISTE
 * -------------------------------------------------------------------
 * Sí, el fondo transparente está 100% permitido y es lo correcto. Lo
 * negro NO venía del código: venía de las PNG. Estaban exportadas con el
 * anti-aliasing "matteado" contra negro, es decir, los píxeles del borde
 * tenían RGB casi (0,0,0) con alfa bajo. Como el render de partículas
 * mezcla por alfa (RGB * alfa), esos bordes pintaban gris/negro encima de
 * lo que hubiera detrás: el clásico halo oscuro.
 *
 * Las texturas de esta entrega ya vienen corregidas (des-multiplicadas:
 * mismo alfa, RGB llevado a blanco). Si en el futuro exportás cuadros
 * nuevos, acordate de exportar con fondo transparente REAL, no con fondo
 * negro y después borrar el negro.
 */
public class SnowyDustParticle extends TextureSheetParticle {

    /** Fracción de vida durante la cual la bocanada sigue creciendo. */
    private static final float EXPAND_UNTIL = 0.35F;
    /** Cuánto crece respecto al tamaño inicial antes de empezar a apagarse. */
    private static final float EXPAND_FACTOR = 1.65F;
    /** Empujón vertical fijo. 0 = totalmente neutra, >0 sube, <0 cae. */
    private static final double FALL_BIAS = 0.0D;
    /** A partir de qué fracción de la vida empieza a desvanecerse. */
    private static final float FADE_START = 0.68F;

    private final SpriteSet sprites;
    private final float startQuadSize;
    private final float spin;

    protected SnowyDustParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites,
                                float sizeScale) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;

        // La velocidad la manda quien la spawnea -- tal cual, sin tocarla.
        // Eso es lo que permite el "sale volando hacia adelante".
        this.xd = dx;
        this.yd = dy + FALL_BIAS;
        this.zd = dz;

        // Sin gravedad: es humo, no grava.
        this.gravity = 0.0F;
        // Frena de a poco: arranca rápido y se queda flotando al final.
        this.friction = 0.90F;
        // Sin colisión: con hasPhysics=true las bocanadas se "pegaban" a las
        // paredes y se notaba muchísimo que eran quads planos.
        this.hasPhysics = false;

        this.lifetime = 16 + this.random.nextInt(10);
        this.startQuadSize = (0.85F + this.random.nextFloat() * 0.55F) * sizeScale;
        this.quadSize = this.startQuadSize;

        // Blanco con una pizca de azul: es nieve, no humo de fogata.
        this.setColor(0.95F, 0.97F, 1.0F);
        this.alpha = 1.0F;

        // Rotación inicial al azar + giro lento propio. Sin esto, 20 copias
        // de la misma bocanada se ven como un patrón repetido obvio.
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.06F;

        this.setSpriteFromAge(sprites);
    }

    /**
     * Curva de tamaño: crece rápido hasta EXPAND_UNTIL y después se mantiene
     * casi igual (el apagado lo hace el alfa, no el tamaño). Encoger Y
     * desvanecer a la vez hace que la partícula "se escape" de la vista;
     * mantener el tamaño y solo desvanecer se lee mucho más como humo.
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        float scale;
        if (progress < EXPAND_UNTIL) {
            float t = progress / EXPAND_UNTIL;
            // easeOut: crece de golpe al principio y frena.
            scale = Mth.lerp(1.0F - (1.0F - t) * (1.0F - t), 1.0F, EXPAND_FACTOR);
        } else {
            float t = (progress - EXPAND_UNTIL) / (1.0F - EXPAND_UNTIL);
            scale = Mth.lerp(t, EXPAND_FACTOR, EXPAND_FACTOR * 0.88F);
        }
        return this.startQuadSize * scale;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        this.roll += this.spin;
        super.tick();

        if (!this.removed) {
            // Avanza el cuadro de arte junto con la edad (9 sprites repartidos
            // a lo largo de toda la vida de la partícula).
            this.setSpriteFromAge(this.sprites);
            // Opaca hasta el 68 % de la vida y apagado RÁPIDO al final. Antes
            // empezaba a desvanecerse a mitad de vida, y una partícula blanca
            // medio transparente sobre un fondo oscuro se lee como gris.
            float progress = this.age / (float) this.lifetime;
            this.alpha = progress < FADE_START
                    ? 1.0F
                    : Mth.clamp(1.0F - (progress - FADE_START) / (1.0F - FADE_START), 0.0F, 1.0F);
        }
    }

    /**
     * Sin escritura de profundidad: arregla que al cruzarse una nube con otra
     * la de atrás quedara "recortada" y se viera transparente. Ver
     * SnowyParticleUtil.
     */
    @Override
    public ParticleRenderType getRenderType() {
        return SnowyParticleUtil.TRANSLUCENT_NO_DEPTH;
    }

    /**
     * Arregla el gris del final del rugido: la nube se hundía en el terreno,
     * el motor de luz le daba 0 y se pintaba oscura. Ver SnowyParticleUtil.
     */
    @Override
    public int getLightColor(float partialTick) {
        return SnowyParticleUtil.brightLight(this.level, this.x, this.y, this.z);
    }

    /**
     * Escala de la variante CHICA (snowy_dust_small), para los ataques básicos.
     * La nube normal mide hasta ~4.6 bloques de ancho (arranca en 0.85-1.4 de
     * radio y crece x1.65), y el oso mide 5.4 de alto: una ráfaga a la altura
     * del cuerpo tapa toda la animación. Con 0.42 la nube queda en ~1.9 de
     * ancho como máximo: se ve, pero deja ver el ataque.
     */
    public static final float SMALL_SCALE = 0.42F;

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float sizeScale;

        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F);
        }

        public Provider(SpriteSet sprites, float sizeScale) {
            this.sprites = sprites;
            this.sizeScale = sizeScale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SnowyDustParticle(level, x, y, z, dx, dy, dz, this.sprites, this.sizeScale);
        }
    }
}
