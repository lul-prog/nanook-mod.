package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanookmod.NanookMod;
import com.nanookmod.entity.NanookCrystal;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Dibuja NanookCrystal con estética de Minecraft: CUBOS APILADOS que se afinan
 * hacia arriba (como las agujas de hielo o los cúmulos de amatista), con una
 * textura pixel-art de paleta corta y bordes duros.
 *
 * ===================================================================
 * QUÉ CAMBIÓ RESPECTO A LA VERSIÓN ANTERIOR
 * ===================================================================
 * Antes: prisma hexagonal con punta, textura con degradados y translúcida.
 * Se leía "realista". Ahora:
 *   - la forma son 4 cubos apilados (8 -> 6 -> 4 -> 2 píxeles de ancho), con un
 *     desfase de 0-1 px entre cubos para que quede "torcido" y no una torre
 *     perfecta;
 *   - los satélites usan los mismos cubos (sin escalarlos), así todos los
 *     píxeles de la textura miden lo mismo: es lo que da el look de Minecraft;
 *   - los ángulos de inclinación son múltiplos de 22.5°;
 *   - se dibuja opaco (entityCutoutNoCull), sin mezclar por alfa.
 *
 * Solo hubo que tocar ESTE archivo y nanook_crystal.png. NanookCrystal (la
 * lógica, el daño, la ola) no cambia: sigue exponiendo getGrowth(),
 * getCrystalSize() y getSeed().
 *
 * ===================================================================
 * MAPA DE LA TEXTURA (64x64) -- si editás la PNG, respetá estas zonas
 * ===================================================================
 * Cada cubo usa el desplegado clásico de vanilla (ModelPart / addBox):
 *
 *        +-----+-----+
 *        | top |bott.|        ancho de la zona = 2*d + 2*w
 *   +----+-----+-----+----+   alto  de la zona = d + h
 *   |west |north|east |south|
 *   +----+-----+-----+----+
 *
 *   A  (8x14x8) en (0,0)     B  (6x12x6) en (32,0)    C  (4x10x4) en (0,22)
 *   D  (2x6x2)  en (16,22)   SA (6x10x6) en (32,22)   SB (4x8x4)  en (0,38)
 *
 * Principal = A + B + C + D (42 px = 2.6 bloques). Satélites = SA + SB (+ D).
 */
public class NanookCrystalRenderer extends EntityRenderer<NanookCrystal> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/entity/nanook_crystal.png");

    private static final float TEX_W = 64.0F;
    private static final float TEX_H = 64.0F;
    private static final float PX = 1.0F / 16.0F;

    /** Un cubo del cúmulo: tamaño en píxeles y dónde está su zona en la textura. */
    private record Seg(int w, int h, int d, int u, int v) {
    }

    private static final Seg A = new Seg(8, 14, 8, 0, 0);
    private static final Seg B = new Seg(6, 12, 6, 32, 0);
    private static final Seg C = new Seg(4, 10, 4, 0, 22);
    private static final Seg D = new Seg(2, 6, 2, 16, 22);
    private static final Seg SA = new Seg(6, 10, 6, 32, 22);
    private static final Seg SB = new Seg(4, 8, 4, 0, 38);

    private static final Seg[] MAIN = {A, B, C, D};
    private static final Seg[] SAT_LONG = {SA, SB, D};
    private static final Seg[] SAT_SHORT = {SA, SB};

    private static final int SATELLITES = 4;
    /** Cuánto se entierra la base para que no se vea un borde flotando. */
    private static final float SINK_INTO_GROUND = 0.12F;

    public NanookCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(NanookCrystal entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float growth = entity.getGrowth(partialTick);
        if (growth <= 0.001F) {
            return;
        }

        float size = entity.getCrystalSize();
        RandomSource rnd = RandomSource.create(entity.getSeed());
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        // Casi emisivo: hielo que no queda negro en una cueva ni de noche.
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 12), LightTexture.sky(packedLight));

        // Al surgir empieza más flaco y se ensancha: se lee como que "empuja".
        float widthGrowth = 0.7F + 0.3F * Math.min(growth, 1.0F);

        poseStack.pushPose();
        // Giro del cúmulo en múltiplos de 22.5°.
        poseStack.mulPose(Axis.YP.rotationDegrees(rnd.nextInt(16) * 22.5F));
        poseStack.scale(size * widthGrowth, size * growth, size * widthGrowth);
        poseStack.translate(0.0D, -SINK_INTO_GROUND, 0.0D);

        // --- Cubo principal: vertical, sin inclinar ---
        stack(poseStack, consumer, light, MAIN, MAIN.length, rnd);

        // --- Satélites: inclinados hacia afuera, de a 22.5° (o 45° a veces) ---
        for (int i = 0; i < SATELLITES; i++) {
            float angle = i * 90.0F + (rnd.nextBoolean() ? 0.0F : 45.0F);
            float distance = 0.28F + rnd.nextFloat() * 0.14F;
            // 22.5° casi siempre; 45° solo uno de cada tres: con más, los
            // satélites quedan casi horizontales y el cúmulo se ve despatarrado.
            float lean = rnd.nextInt(3) == 0 ? 45.0F : 22.5F;
            Seg[] segs = rnd.nextBoolean() ? SAT_LONG : SAT_SHORT;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            poseStack.translate(distance, 0.0D, 0.0D);
            // Girar alrededor de Z por un ángulo NEGATIVO inclina +Y hacia +X, o
            // sea hacia afuera del cúmulo.
            poseStack.mulPose(Axis.ZP.rotationDegrees(-lean));
            stack(poseStack, consumer, light, segs, segs.length, rnd);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * Apila 'count' cubos hacia arriba. Cada uno se corre 0 o +-1 píxel respecto
     * del anterior: una pila perfectamente centrada se ve como un pino de
     * Navidad; con el desfase parece un cristal de verdad.
     */
    private static void stack(PoseStack poseStack, VertexConsumer vc, int light,
                              Seg[] segs, int count, RandomSource rnd) {
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();

        int y = 0;
        int ox = 0;
        int oz = 0;
        for (int i = 0; i < count; i++) {
            Seg s = segs[i];
            if (i > 0) {
                ox = Mth.clamp(ox + rnd.nextInt(3) - 1, -1, 1);
                oz = Mth.clamp(oz + rnd.nextInt(3) - 1, -1, 1);
            }
            box(vc, pose, normal, light, s, ox, y, oz);
            y += s.h();
        }
    }

    /**
     * Un cubo centrado en (ox, oz) (en píxeles) con la base en y. Se dibujan las
     * 4 caras laterales y la de arriba; la de abajo nunca se ve (está enterrada
     * o apoyada sobre el cubo de abajo).
     */
    private static void box(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light,
                            Seg s, int ox, int y, int oz) {
        float x0 = (ox - s.w() * 0.5F) * PX;
        float x1 = (ox + s.w() * 0.5F) * PX;
        float y0 = y * PX;
        float y1 = (y + s.h()) * PX;
        float z0 = (oz - s.d() * 0.5F) * PX;
        float z1 = (oz + s.d() * 0.5F) * PX;

        int u = s.u();
        int v = s.v();
        int w = s.w();
        int h = s.h();
        int d = s.d();

        // Arriba (+Y): zona (u+d, v) de w x d
        quad(vc, pose, normal, light, 0, 1, 0,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                u + d, v, w, d);
        // Norte (-Z): zona (u+d, v+d) de w x h
        quad(vc, pose, normal, light, 0, 0, -1,
                x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0,
                u + d, v + d, w, h);
        // Sur (+Z): zona (u+d+w+d, v+d) de w x h
        quad(vc, pose, normal, light, 0, 0, 1,
                x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
                u + d + w + d, v + d, w, h);
        // Oeste (-X): zona (u, v+d) de d x h
        quad(vc, pose, normal, light, -1, 0, 0,
                x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
                u, v + d, d, h);
        // Este (+X): zona (u+d+w, v+d) de d x h
        quad(vc, pose, normal, light, 1, 0, 0,
                x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0,
                u + d + w, v + d, d, h);
    }

    /**
     * Un cuádruple con la zona rectangular (zu, zv, zw, zh) de la textura, en
     * píxeles. Los vértices van en el orden: arriba-izq, arriba-der, abajo-der,
     * abajo-izq de la zona.
     */
    private static void quad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int zu, int zv, int zw, int zh) {
        float uL = zu / TEX_W;
        float uR = (zu + zw) / TEX_W;
        float vT = zv / TEX_H;
        float vB = (zv + zh) / TEX_H;

        vertex(vc, pose, normal, light, x0, y0, z0, uL, vT, nx, ny, nz);
        vertex(vc, pose, normal, light, x1, y1, z1, uR, vT, nx, ny, nz);
        vertex(vc, pose, normal, light, x2, y2, z2, uR, vB, nx, ny, nz);
        vertex(vc, pose, normal, light, x3, y3, z3, uL, vB, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        vc.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(NanookCrystal entity) {
        return TEXTURE;
    }
}
