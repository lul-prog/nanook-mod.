package com.nanookmod.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Hoja que cae de crepuscular_leaves (ver CrepuscularLeavesBlock#animateTick),
 * al estilo de las hojas de cerezo vanilla: cae despacio, se mece de lado a
 * lado en el aire y se desvanece al final de su vida.
 *
 * La textura tiene 3 variantes (crepuscular_leaf_0/1/2). El color sale
 * directo de la textura (sin tinte aplicado en Java), así que cualquier
 * paleta que se pinte a mano en esas texturas se ve tal cual en el juego.
 * Cada partícula elige una variante al azar UNA sola vez al nacer (no cicla
 * frames por edad, para no "animar" la forma de la hoja).
 */
public class CrepuscularLeafParticle extends TextureSheetParticle {

    private final double swaySeed;

    protected CrepuscularLeafParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.swaySeed = this.random.nextDouble() * Math.PI * 2.0D;

        // Sin gravedad "de motor": controlamos la caída a mano para que sea
        // lenta y constante, no una caída acelerada realista.
        this.gravity = 0.0F;
        this.hasPhysics = true;
        this.lifetime = 100 + this.random.nextInt(60);
        this.quadSize = 0.26F + this.random.nextFloat() * 0.14F;

        this.xd = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd = -0.012D - this.random.nextDouble() * 0.01D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.01D;

        // Variante fija al azar (no animada): cada hoja se queda con su forma.
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();

        // Mecido lateral: dos senoidales desfasadas para que no caiga en línea recta.
        this.xd += Math.sin((this.age + this.swaySeed) * 0.1D) * 0.0009D;
        this.zd += Math.cos((this.age + this.swaySeed) * 0.085D) * 0.0009D;

        if (this.onGround) {
            // Al tocar el suelo se queda quieta y se apaga un poco antes.
            this.xd = 0.0D;
            this.zd = 0.0D;
        }

        int fadeWindow = 15;
        if (this.age > this.lifetime - fadeWindow) {
            this.alpha = Math.max(0.0F, (float) (this.lifetime - this.age) / fadeWindow);
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
            return new CrepuscularLeafParticle(level, x, y, z, this.sprites);
        }
    }
}
