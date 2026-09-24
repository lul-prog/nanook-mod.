package com.nanookmod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja los filamentos de Corrupción (CorruptionFilamentManager).
 *
 * v4 -- cómo se desvanecen (cambiado a pedido): mientras crece (age <=
 * growthTicks) la punta sube y nada se desvanece. Terminado el
 * crecimiento, el desvanecido arranca en la BASE (s=0, pegada al
 * jugador) y avanza HACIA ARRIBA -- entonces la base es la primera en
 * borrarse y la punta es la ÚLTIMA parte visible, dando la sensación de
 * que la energía sigue viajando/escapando hacia arriba hasta el final,
 * en vez de "quedarse sin energía" desde arriba. (Antes era al revés, se
 * borraba desde la punta hacia abajo -- v3.)
 *
 * También: los dos extremos visibles (la punta y el borde del
 * desvanecido) se AFINAN a un ancho 0 en vez de cortar de golpe -- así no
 * se ven "cortadas" cuando aparecen o desaparecen.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CorruptionFilamentRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/effect/corruption_filament.png");

    // Mismo tono que NecroticWaveRenderer / la estela del wisp: #9cf6de.
    // (Alternativa si en algún momento lo querés más verde puro: #62f694
    // -- 0.3843F, 0.9647F, 0.5804F -- es el que usa el jefe en los tethers
    // de los portales/TwilightPortalEntity.)
    private static final float COLOR_R = 0.6118f;
    private static final float COLOR_G = 0.9647f;
    private static final float COLOR_B = 0.8706f;
    private static final float WIDTH = 0.09f;
    private static final float MAX_ALPHA = 1.0f;

    private static final int SEGMENTS = 10;

    // Ancho (fracción 0..1 de la línea) de la zona de transición del
    // borde de desvanecido -- más grande = fundido más gradual.
    private static final float DISSOLVE_BAND = 0.22F;

    // Cuánto (fracción 0..1 de la línea) tarda cada punta en afinarse a 0
    // de ancho. Se aplica tanto en la punta de arriba como en el borde
    // del desvanecido de abajo -- por eso ninguna de las dos se ve
    // cortada de golpe.
    private static final float TAPER_LEN = 0.14F;

    private static final class Point {
        final Vec3 pos;
        final float s; // 0 = base, 1 = punta

        Point(Vec3 pos, float s) {
            this.pos = pos;
            this.s = s;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        float currentTick = mc.player != null ? mc.player.tickCount + partialTick : 0F;

        CorruptionFilamentManager.pruneExpired(currentTick);
        List<CorruptionFilamentManager.Filament> filaments = CorruptionFilamentManager.active();
        if (filaments.isEmpty()) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (CorruptionFilamentManager.Filament f : filaments) {
            float age = currentTick - f.spawnTick;

            // Cuánto de la línea (0..1) alcanzó a crecer -- también es el
            // tope superior (la punta) mientras esté creciendo, y se queda
            // fijo en 1 una vez que terminó.
            float grownS = Mth.clamp(age / f.growthTicks, 0F, 1F);

            // Borde de desvanecido, medido desde la BASE hacia arriba.
            float eraseUpToS;
            if (age <= f.growthTicks) {
                eraseUpToS = 0F;
            } else {
                float dissolveDuration = Math.max(1F, f.duration - f.growthTicks);
                eraseUpToS = Mth.clamp((age - f.growthTicks) / dissolveDuration, 0F, 1F);
            }
            if (eraseUpToS >= grownS) {
                continue; // ya se borró del todo (el borde alcanzó a la punta)
            }

            List<Point> points = samplePoints(f, grownS, partialTick);
            renderRibbon(builder, matrix, points, cameraPos, eraseUpToS, grownS);
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Puntos desde la base (s=0) hasta donde llegó a crecer (s=grownS). */
    private static List<Point> samplePoints(CorruptionFilamentManager.Filament f, float grownS, float partialTick) {
        Entity owner = f.owner;
        Vec3 ownerPos = owner.getPosition(partialTick);
        double bbHeight = owner.getBbHeight();
        double axisRad = Math.toRadians(f.wobbleAxisAngle);
        double axisX = Math.cos(axisRad);
        double axisZ = Math.sin(axisRad);

        int segs = Math.max(2, Math.round(SEGMENTS * grownS));
        List<Point> points = new ArrayList<>(segs + 1);
        for (int i = 0; i <= segs; i++) {
            float s = grownS * i / segs;
            double wobble = Math.sin(s * f.wobbleFrequency * Math.PI * 2.0 + f.wobblePhase) * f.wobbleAmplitude;
            double x = ownerPos.x + f.originOffsetX + axisX * wobble;
            double z = ownerPos.z + f.originOffsetZ + axisZ * wobble;
            double y = ownerPos.y + bbHeight * (f.baseHeightFraction + f.riseHeightFraction * s);
            points.add(new Point(new Vec3(x, y, z), s));
        }
        return points;
    }

    /** Alpha en un punto s -- 0 antes del borde de desvanecido, rampa suave, después lleno. */
    private static float alphaAt(float s, float eraseUpToS) {
        if (s <= eraseUpToS) {
            return 0F;
        }
        if (s >= eraseUpToS + DISSOLVE_BAND) {
            return MAX_ALPHA;
        }
        return MAX_ALPHA * (s - eraseUpToS) / DISSOLVE_BAND;
    }

    /** Ancho en un punto s -- se afina a 0 cerca de las dos puntas visibles (borde de desvanecido y punta de arriba). */
    private static float widthMulAt(float s, float eraseUpToS, float grownS) {
        float fromErase = Mth.clamp((s - eraseUpToS) / TAPER_LEN, 0F, 1F);
        float fromTip = Mth.clamp((grownS - s) / TAPER_LEN, 0F, 1F);
        return Math.min(fromErase, fromTip);
    }

    private static void renderRibbon(BufferBuilder builder, Matrix4f matrix, List<Point> points, Vec3 cameraPos,
                                     float eraseUpToS, float grownS) {
        int segments = points.size() - 1;
        for (int i = 0; i < segments; i++) {
            Point from = points.get(i);
            Point to = points.get(i + 1);

            Vec3 fromLocal = from.pos.subtract(cameraPos);
            Vec3 toLocal = to.pos.subtract(cameraPos);

            Vec3 segmentDir = toLocal.subtract(fromLocal);
            if (segmentDir.lengthSqr() < 1.0e-6) {
                continue;
            }

            Vec3 midLocal = fromLocal.add(toLocal).scale(0.5);
            Vec3 towardCamera = midLocal.scale(-1.0);
            Vec3 perp = segmentDir.cross(towardCamera);
            if (perp.lengthSqr() < 1.0e-6) {
                perp = segmentDir.cross(new Vec3(0, 1, 0));
                if (perp.lengthSqr() < 1.0e-6) {
                    perp = segmentDir.cross(new Vec3(1, 0, 0));
                }
            }
            perp = perp.normalize();

            float alphaFrom = alphaAt(from.s, eraseUpToS);
            float alphaTo = alphaAt(to.s, eraseUpToS);
            if (alphaFrom <= 0.002F && alphaTo <= 0.002F) {
                continue;
            }

            float widthFrom = WIDTH * widthMulAt(from.s, eraseUpToS, grownS) / 2.0F;
            float widthTo = WIDTH * widthMulAt(to.s, eraseUpToS, grownS) / 2.0F;

            Vec3 perpFrom = perp.scale(widthFrom);
            Vec3 perpTo = perp.scale(widthTo);

            Vec3 fromA = fromLocal.add(perpFrom);
            Vec3 fromB = fromLocal.subtract(perpFrom);
            Vec3 toA = toLocal.add(perpTo);
            Vec3 toB = toLocal.subtract(perpTo);

            vertex(builder, matrix, fromA, from.s, 0F, alphaFrom);
            vertex(builder, matrix, fromB, from.s, 1F, alphaFrom);
            vertex(builder, matrix, toB, to.s, 1F, alphaTo);
            vertex(builder, matrix, toA, to.s, 0F, alphaTo);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float u, float v, float alpha) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .uv(u, v)
                .color(COLOR_R, COLOR_G, COLOR_B, alpha)
                .endVertex();
    }
}