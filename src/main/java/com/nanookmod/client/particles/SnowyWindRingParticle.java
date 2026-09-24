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

/**
 * Aro de VIENTO: el efecto de "romper el aire" del dash.
 *
 * ===================================================================
 * QUÉ ES LO QUE PEDISTE
 * ===================================================================
 * En el dash del Nameless Guardian (EEEAB's Mobs) no hay una sola
 * partícula de viento: hay una CADENA DE AROS VERTICALES. Cada pocos ticks
 * aparece un aro grande y casi transparente POR DELANTE del mob, de pie,
 * perpendicular a la dirección en la que corre. El aro crece de golpe, se
 * encoge y se apaga, y el mob lo va atravesando. Uno tras otro forman un
 * túnel de aire desplazado. Eso es lo que se lee como "viento".
 *
 * Es distinto de SnowyRingParticle, que es un aro ACOSTADO en el piso (la
 * onda de impacto). Este está DE PIE y apunta según el yaw del mob.
 *
 * (Esto es una reimplementación propia del EFECTO. El arte del aro, la
 * curva de tamaño y de opacidad, y el render son de este mod.)
 *
 * ===================================================================
 * CÓMO SE SPAWNEA
 * ===================================================================
 * Igual que SnowyRingParticle, se reutilizan los tres argumentos de
 * velocidad como PARÁMETROS, y hay que mandarla con count = 0:
 *
 *   dx -> yaw en GRADOS, convención de Minecraft (el getYRot() del mob)
 *   dy -> radio máximo del aro, en bloques (0 = usa DEFAULT_PEAK_SIZE)
 *   dz -> multiplicador de opacidad (0 = 1.0)
 *
 *   serverLevel.sendParticles(ModParticles.SNOWY_WIND_RING.get(),
 *           x, y, z, 0, yawDegrees, peakRadius, alphaMult, 1.0D);
 *
 * Ver NanookEntity#spawnWindRing.
 */
public class SnowyWindRingParticle extends TextureSheetParticle {

    private static final int LIFETIME_TICKS = 26;
    /** Radio máximo por defecto (bloques). Nanook mide 3.1 de alto. */
    private static final float DEFAULT_PEAK_SIZE = 3.4F;
    /** Opacidad base. El de EEEAB's usa ~0.14; acá es más alta porque la nieve
     *  de fondo es blanca y un aro blanco tenue contra nieve no se ve. */
    private static final float BASE_ALPHA = 0.32F;
    /** Fracción de vida en la que llega al tamaño máximo (crece de golpe). */
    private static final float GROW_FRACTION = 0.22F;
    /** Tamaño final relativo al máximo: se cierra pero no desaparece del todo. */
    private static final float END_SIZE_FACTOR = 0.30F;
    /** Fracción de vida de aparición gradual (evita el "pop" en el primer frame). */
    private static final float FADE_IN_FRACTION = 0.10F;

    private final float peakSize;
    private final float alphaMult;
    /** Vectores del plano del aro, ya calculados desde el yaw. */
    private final float rightX;
    private final float rightZ;

    private float size;
    private float prevSize;

    protected SnowyWindRingParticle(ClientLevel level, double x, double y, double z,
                                    double yawDegrees, double peak, double alphaMult,
                                    SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);

        this.peakSize = peak > 0.01D ? (float) peak : DEFAULT_PEAK_SIZE;
        this.alphaMult = alphaMult > 0.001D ? (float) alphaMult : 1.0F;

        // Convención de yaw de Minecraft: el frente del mob es
        // (-sin yaw, 0, cos yaw). El aro está en el plano PERPENDICULAR a
        // ese frente, o sea que su eje horizontal es (cos yaw, 0, sin yaw).
        float yawRad = (float) Math.toRadians(yawDegrees);
        this.rightX = Mth.cos(yawRad);
        this.rightZ = Mth.sin(yawRad);

        this.lifetime = LIFETIME_TICKS;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0.0D;

        // Blanco con una pizca de azul: es aire helado, no humo.
        this.setColor(0.86F, 0.93F, 1.0F);
        this.alpha = 0.0F;

        this.size = 0.0F;
        this.prevSize = 0.0F;
        this.quadSize = 0.0F;

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        // Sin super.tick(): no queremos física ni movimiento, solo edad.
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.prevSize = this.size;
        float progress = this.age / (float) this.lifetime;
        this.size = this.peakSize * sizeCurve(progress);
        this.quadSize = this.size;

        // Aparece gradual, después decae. La potencia > 1 hace que se apague
        // rápido al final en vez de arrastrarse translúcida.
        float fadeIn = Mth.clamp(progress / FADE_IN_FRACTION, 0.0F, 1.0F);
        float fadeOut = (float) Math.pow(1.0F - progress, 1.25D);
        this.alpha = BASE_ALPHA * this.alphaMult * fadeIn * fadeOut;
    }

    /**
     * Curva de tamaño: crece rápido hasta GROW_FRACTION con easeOutCubic y
     * después se encoge suave hasta END_SIZE_FACTOR. Es lo que hace que el
     * aro "explote" hacia afuera y se cierre, en vez de crecer sin más.
     */
    private static float sizeCurve(float progress) {
        if (progress < GROW_FRACTION) {
            float t = progress / GROW_FRACTION;
            float inv = 1.0F - t;
            return 1.0F - inv * inv * inv;
        }
        float t = (progress - GROW_FRACTION) / (1.0F - GROW_FRACTION);
        float eased = t * t * (3.0F - 2.0F * t); // smoothstep
        return Mth.lerp(eased, 1.0F, END_SIZE_FACTOR);
    }

    @Override
    public int getLightColor(float partialTick) {
        return SnowyParticleUtil.brightLight(this.level, this.x, this.y, this.z);
    }

    /**
     * Quad DE PIE, en el plano perpendicular al frente del mob. Se emite por
     * las dos caras para que se vea igual desde adelante y desde atrás
     * (el jugador lo ve venir de frente y también lo ve pasar a su lado).
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());

        float s = Mth.lerp(partialTick, this.prevSize, this.size);
        if (s <= 0.001F || this.alpha <= 0.002F) {
            return;
        }

        float rx = this.rightX * s;
        float rz = this.rightZ * s;

        // Esquinas: (-right,-up) (-right,+up) (+right,+up) (+right,-up)
        float[][] corners = {
                {cx - rx, cy - s, cz - rz},
                {cx - rx, cy + s, cz - rz},
                {cx + rx, cy + s, cz + rz},
                {cx + rx, cy - s, cz + rz}
        };

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        float[][] uvs = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        int light = this.getLightColor(partialTick);

        for (int i = 0; i < 4; i++) {
            buffer.vertex(corners[i][0], corners[i][1], corners[i][2])
                    .uv(uvs[i][0], uvs[i][1])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            buffer.vertex(corners[i][0], corners[i][1], corners[i][2])
                    .uv(uvs[i][0], uvs[i][1])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }
    }

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
                                       double yawDegrees, double peak, double alphaMult) {
            return new SnowyWindRingParticle(level, x, y, z, yawDegrees, peak, alphaMult, this.sprites);
        }
    }
}
