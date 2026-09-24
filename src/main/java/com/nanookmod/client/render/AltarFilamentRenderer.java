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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja los filamentos del Altar Necromante (AltarFilamentManager). Es un
 * calco casi literal de CorruptionFilamentRenderer -- misma técnica de
 * listón camera-facing con blending aditivo, tesselator a mano, sin
 * escribir GLSL -- adaptado para que el "crecimiento" vaya de un punto fijo
 * en el aire HACIA el altar (en vez de desde un jugador hacia arriba), y
 * el desvanecido avance desde la punta lejana hacia el altar, es decir que
 * el filamento se "consume hacia adentro del bloque" -- que es justo lo
 * que pediste.
 *
 * Si en algún momento controlás visualmente que esto se ve "muy parecido"
 * al efecto de Corrupción y querés diferenciarlos más, los puntos de ajuste
 * rápido son COLOR_* acá abajo y la textura altar_filament.png (tiene un
 * patrón de "nodos" pulsantes a lo largo, a diferencia de la lisa de
 * Corrupción).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AltarFilamentRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/effect/altar_filament.png");

    // Teal brillante, la misma familia que ya usa la paleta del altar
    // (ver altar_necromante.png: acento vivo ~#44EDBD). Se mezcla hacia
    // blanco puro con la intensidad capturada por cada filamento (más
    // blanco = más cerca estaba el ritual de terminar cuando nació).
    private static final float BASE_R = 0.30f;
    private static final float BASE_G = 0.95f;
    private static final float BASE_B = 0.78f;

    private static final float WIDTH = 0.075f;
    private static final float MAX_ALPHA = 0.95f;
    private static final int SEGMENTS = 9;
    private static final float DISSOLVE_BAND = 0.22F;
    private static final float TAPER_LEN = 0.16F;

    private static final class Point {
        final Vec3 pos;
        final float s;

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
        float currentTick = mc.player != null ? mc.player.tickCount + partialTick : (float) level.getGameTime();

        AltarFilamentManager.pruneExpired(currentTick);
        List<AltarFilamentManager.Filament> filaments = AltarFilamentManager.active();
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

        for (AltarFilamentManager.Filament f : filaments) {
            float age = currentTick - f.spawnTick;

            float grownS = Mth.clamp(age / f.growthTicks, 0F, 1F);

            float eraseUpToS;
            if (age <= f.growthTicks) {
                eraseUpToS = 0F;
            } else {
                float dissolveDuration = Math.max(1F, f.duration - f.growthTicks);
                eraseUpToS = Mth.clamp((age - f.growthTicks) / dissolveDuration, 0F, 1F);
            }
            if (eraseUpToS >= grownS) {
                continue;
            }

            List<Point> points = samplePoints(f, grownS);
            renderRibbon(builder, matrix, points, cameraPos, eraseUpToS, grownS, f.intensity);
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Puntos desde el origen lejano (s=0) hasta donde llegó a crecer hacia el altar (s=grownS). */
    private static List<Point> samplePoints(AltarFilamentManager.Filament f, float grownS) {
        int segs = Math.max(2, Math.round(SEGMENTS * grownS));
        List<Point> points = new ArrayList<>(segs + 1);

        double mixAngle = f.wobbleAxisAngle;
        double axisMixA = Math.cos(mixAngle);
        double axisMixB = Math.sin(mixAngle);

        for (int i = 0; i <= segs; i++) {
            float s = grownS * i / segs;
            // El wobble se atenúa cerca de las dos puntas (origen y altar)
            // para que el filamento "nazca" y "llegue" prolijo, sin un
            // codo brusco justo en los extremos.
            double edgeFade = Math.sin(Math.PI * s);
            double wobble = Math.sin(s * f.wobbleFrequency * Math.PI * 2.0 + f.wobblePhase)
                    * f.wobbleAmplitude * edgeFade;

            double offX = (f.wobbleAxisA.x * axisMixA + f.wobbleAxisB.x * axisMixB) * wobble;
            double offY = (f.wobbleAxisA.y * axisMixA + f.wobbleAxisB.y * axisMixB) * wobble;
            double offZ = (f.wobbleAxisA.z * axisMixA + f.wobbleAxisB.z * axisMixB) * wobble;

            double x = Mth.lerp(s, f.origin.x, f.target.x) + offX;
            double y = Mth.lerp(s, f.origin.y, f.target.y) + offY;
            double z = Mth.lerp(s, f.origin.z, f.target.z) + offZ;
            points.add(new Point(new Vec3(x, y, z), s));
        }
        return points;
    }

    private static float alphaAt(float s, float eraseUpToS) {
        if (s <= eraseUpToS) {
            return 0F;
        }
        if (s >= eraseUpToS + DISSOLVE_BAND) {
            return MAX_ALPHA;
        }
        return MAX_ALPHA * (s - eraseUpToS) / DISSOLVE_BAND;
    }

    private static float widthMulAt(float s, float eraseUpToS, float grownS) {
        float fromErase = Mth.clamp((s - eraseUpToS) / TAPER_LEN, 0F, 1F);
        float fromTip = Mth.clamp((grownS - s) / TAPER_LEN, 0F, 1F);
        return Math.min(fromErase, fromTip);
    }

    private static void renderRibbon(BufferBuilder builder, Matrix4f matrix, List<Point> points, Vec3 cameraPos,
                                     float eraseUpToS, float grownS, float intensity) {
        // Más blanco cuanto más intenso -- el clímax del ritual se ve más
        // "caliente" que los primeros filamentos, tímidos y bien teal.
        float r = Mth.lerp(intensity, BASE_R, 1.0F);
        float g = Mth.lerp(intensity, BASE_G, 1.0F);
        float b = Mth.lerp(intensity, BASE_B, 1.0F);

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

            vertex(builder, matrix, fromA, from.s, 0F, alphaFrom, r, g, b);
            vertex(builder, matrix, fromB, from.s, 1F, alphaFrom, r, g, b);
            vertex(builder, matrix, toB, to.s, 1F, alphaTo, r, g, b);
            vertex(builder, matrix, toA, to.s, 0F, alphaTo, r, g, b);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float u, float v, float alpha,
                               float r, float g, float b) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .uv(u, v)
                .color(r, g, b, alpha)
                .endVertex();
    }
}
