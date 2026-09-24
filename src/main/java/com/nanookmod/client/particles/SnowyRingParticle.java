package com.nanookmod.client.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Anillo PLANO sobre el suelo que se abre hacia afuera (snowy_ring).
 *
 * Es el equivalente propio al "anillo grande" que deja el Nameless
 * Guardian cuando corre: un aro que aparece a los pies, crece rápido y se
 * apaga. La idea es la misma, la implementación y el arte son de este mod
 * (4 cuadros generados, snowy_ring_0..3.png).
 *
 * La diferencia importante con NecroticLightningRingParticle: aquella es
 * un BILLBOARD (siempre mira a la cámara, así que desde arriba se ve de
 * canto). Esta dibuja el quad ACOSTADO en el plano XZ, así que se lee
 * como una onda pegada al piso desde cualquier ángulo. Por eso hay un
 * render() propio en vez de usar el de TextureSheetParticle.
 *
 * -------------------------------------------------------------------
 * CÓMO SE SPAWNEA (importante)
 * -------------------------------------------------------------------
 * SimpleParticleType no lleva datos extra, así que se reutilizan los
 * argumentos de velocidad como PARÁMETROS:
 *
 *   dx -> multiplicador de tamaño  (1.0 = tamaño base)
 *   dy -> multiplicador de duración (1.0 = duración base)
 *   dz -> sin uso (reservado)
 *
 * Desde el servidor, con count = 0 para que los tres doubles lleguen tal
 * cual al cliente:
 *
 *   serverLevel.sendParticles(ModParticles.SNOWY_RING.get(),
 *           x, y, z, 0, sizeMult, timeMult, 0.0D, 1.0D);
 */
public class SnowyRingParticle extends TextureSheetParticle {

    private static final float BASE_START_SIZE = 0.6F;
    private static final float BASE_END_SIZE = 4.2F;
    private static final int BASE_LIFETIME = 14;

    private final SpriteSet sprites;
    private final float startSize;
    private final float endSize;
    /** Giro del anillo sobre sí mismo, puramente estético. */
    private final float spinPerTick;
    private float yaw;

    protected SnowyRingParticle(ClientLevel level, double x, double y, double z,
                                double sizeMult, double timeMult, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;

        float size = (float) (sizeMult <= 0.0D ? 1.0D : sizeMult);
        float time = (float) (timeMult <= 0.0D ? 1.0D : timeMult);

        this.startSize = BASE_START_SIZE * size;
        this.endSize = BASE_END_SIZE * size;
        this.quadSize = this.startSize;

        this.lifetime = Math.max(4, Mth.floor(BASE_LIFETIME * time));
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0.0D;

        this.setColor(0.96F, 0.98F, 1.0F);
        this.alpha = 0.9F;

        this.yaw = this.random.nextFloat() * Mth.TWO_PI;
        this.spinPerTick = (this.random.nextFloat() - 0.5F) * 0.05F;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        this.setSpriteFromAge(this.sprites);
        this.yaw += this.spinPerTick;

        float progress = this.lifetime <= 0 ? 1.0F : (float) this.age / (float) this.lifetime;
        // easeOut: se abre de golpe y frena, que es como se lee una onda.
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        this.quadSize = Mth.lerp(eased, this.startSize, this.endSize);
        // Se apaga sobre la segunda mitad.
        this.alpha = progress < 0.5F ? 0.9F : Mth.clamp((1.0F - progress) / 0.5F, 0.0F, 1.0F) * 0.9F;
    }

    /**
     * Luz de bloque al máximo (0xF0) manteniendo la de cielo real, para que
     * el anillo se vea de noche y en cuevas sin quedar plano de día. Mismo
     * criterio que ya usa NecroticLightningRingParticle.
     */
    @Override
    public int getLightColor(float partialTick) {
        return SnowyParticleUtil.brightLight(this.level, this.x, this.y, this.z);
    }

    /**
     * Quad ACOSTADO en el plano XZ. Se dibuja dos veces con el orden de
     * vértices invertido para que sea visible desde arriba y desde abajo
     * sin depender de si el culling está activo en ese momento.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());

        float size = this.getQuadSize(partialTick);
        float cos = Mth.cos(this.yaw) * size;
        float sin = Mth.sin(this.yaw) * size;

        // Esquinas del aro, ya rotadas en Y.
        Vector3f[] corners = new Vector3f[]{
                new Vector3f(-cos + sin, 0.0F, -sin - cos),
                new Vector3f(-cos - sin, 0.0F, -sin + cos),
                new Vector3f(cos - sin, 0.0F, sin + cos),
                new Vector3f(cos + sin, 0.0F, sin - cos)
        };

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        float[][] uvs = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        int light = this.getLightColor(partialTick);

        // Cara de arriba.
        for (int i = 0; i < 4; i++) {
            Vector3f c = corners[i];
            buffer.vertex(x + c.x(), y + c.y(), z + c.z())
                    .uv(uvs[i][0], uvs[i][1])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }
        // Cara de abajo (mismo quad, winding invertido).
        for (int i = 3; i >= 0; i--) {
            Vector3f c = corners[i];
            buffer.vertex(x + c.x(), y + c.y(), z + c.z())
                    .uv(uvs[i][0], uvs[i][1])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }
    }

    /** Sin escritura de profundidad (ver SnowyParticleUtil). */
    @Override
    public ParticleRenderType getRenderType() {
        return SnowyParticleUtil.TRANSLUCENT_NO_DEPTH;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double sizeMult, double timeMult, double unused) {
            return new SnowyRingParticle(level, x, y, z, sizeMult, timeMult, this.sprites);
        }
    }
}
