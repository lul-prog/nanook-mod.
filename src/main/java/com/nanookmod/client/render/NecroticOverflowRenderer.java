package com.nanookmod.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.client.shader.ModShaders;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Desborde Necrótico -- filamentos de éter brotando del cuerpo del jefe,
 * subiendo con vaivén hasta disiparse (Corrupción III+, más densos con
 * cada nivel -- ver NecroticOverflowTracker). MISMA técnica y MISMO
 * shader que NecroticWaveRenderer/NecroticCorruptionTetherRenderer: nada
 * de matemática de distancia adentro del shader, toda la forma real
 * (posición, vaivén, billboard hacia cámara) es geometría armada acá en
 * Java con vectores comunes.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NecroticOverflowRenderer {

    // Un solo tono (antes tenía núcleo + halo como el cordón -- acá pediste
    // uno solo). Mismo verde/turquesa real del aura del jefe.
    private static final float FILAMENT_R = 0.55F;
    private static final float FILAMENT_G = 0.97F;
    private static final float FILAMENT_B = 0.85F;
    private static final float FILAMENT_WIDTH = 0.05F;
    private static final float FILAMENT_ALPHA = 0.85F;

    // Cuántos sub-tramos tiene cada filamento -- con pocos se nota cada
    // quiebre como un pedazo separado (se ve "cortado" en vez de un flujo
    // continuo de energía). Con bastantes más, la curva se ve una sola
    // línea fluida de punta a punta -- mismo criterio que el cordón (usa
    // 24). Se van generando SOLO hasta la longitud actual (ver growPhase
    // en drawFilament), así el filamento "crece" en vez de aparecer
    // entero de golpe.
    private static final int SEGMENTS_PER_FILAMENT = 20;

    // Cuánto sube el filamento, como FRACCIÓN de la altura real del jefe
    // (no un número fijo de bloques -- así se nota proporcionalmente sin
    // importar qué tan grande sea el modelo; antes era un valor fijo
    // chico y con un jefe grande casi no se veía). Y cuánto se balancea
    // de lado a lado mientras sube -- le da el "movimiento notorio"
    // pedido, sin que sea una línea recta rígida.
    private static final double RISE_HEIGHT_FRACTION_OF_BODY = 0.75D;
    private static final double SWAY_AMPLITUDE = 0.16D;
    private static final double SWAY_FREQUENCY = 1.6D;

    // Fracción de la vida total en la que el filamento TERMINA de crecer
    // hasta su largo completo (después de eso, se queda del todo hasta
    // que arranca el fade-out).
    private static final float GROW_FRACTION = 0.6F;

    // Fracción de la vida total en fade-in / fade-out (el resto, a alfa completo).
    private static final float FADE_IN_FRACTION = 0.15F;
    private static final float FADE_OUT_FRACTION = 0.35F;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        ShaderInstance shader = ModShaders.getNecroticWaveShader();
        if (shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();
        float gameTimeTicks = (level.getGameTime() % 1_000_000L) + partialTick;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        boolean began = false;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof NecroticKnightEntity knight) || knight.getCorruptionLevel() < 3) {
                continue;
            }

            List<NecroticOverflowTracker.Filament> filaments = NecroticOverflowTracker.getActiveFilaments(knight);
            if (filaments.isEmpty()) {
                continue;
            }

            if (!began) {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.setShader(() -> shader);

                Uniform gameTimeUniform = shader.getUniform("GameTime");
                if (gameTimeUniform != null) {
                    gameTimeUniform.set(gameTimeTicks / 20.0f);
                }

                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                began = true;
            }

            Vec3 knightPos = knight.getPosition(partialTick);
            double bbHeight = knight.getBbHeight();
            float clientTickFloat = NecroticOverflowTracker.getClientTick() + partialTick;

            for (NecroticOverflowTracker.Filament f : filaments) {
                drawFilament(builder, matrix, cameraPos, knightPos, bbHeight, f, clientTickFloat, gameTimeTicks);
            }
        }

        if (began) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
    }

    private static void drawFilament(BufferBuilder builder, Matrix4f matrix, Vec3 cameraPos,
                                     Vec3 knightPos, double bbHeight, NecroticOverflowTracker.Filament f,
                                     float clientTickFloat, float gameTimeTicks) {
        float age = clientTickFloat - f.spawnTick;
        float t = age / NecroticOverflowTracker.FILAMENT_LIFETIME_TICKS;
        if (t < 0.0F || t > 1.0F) {
            return; // todavía no nació o ya se disipó del todo -- no debería pasar (el tracker ya los saca), por las dudas
        }

        float alpha;
        if (t < FADE_IN_FRACTION) {
            alpha = t / FADE_IN_FRACTION;
        } else if (t > 1.0F - FADE_OUT_FRACTION) {
            alpha = (1.0F - t) / FADE_OUT_FRACTION;
        } else {
            alpha = 1.0F;
        }
        if (alpha <= 0.01F) {
            return;
        }

        double timeSeconds = gameTimeTicks / 20.0;

        // Punto de anclaje: FIJO en el cuerpo (de acá "brota" y no se
        // mueve) -- el filamento entero crece hacia arriba desde acá, no
        // es que el punto base también sube. Eso es lo que antes lo hacía
        // ver como un segmento corto flotando suelto en vez de algo
        // saliendo de la superficie.
        Vec3 anchorWorld = knightPos.add(f.offsetX, bbHeight * f.baseHeightFraction, f.offsetZ);
        Vec3 anchor = anchorWorld.subtract(cameraPos);

        // Longitud actual: crece de 0 a RISE_HEIGHT_FRACTION_OF_BODY*altura
        // durante el primer GROW_FRACTION de la vida, y se queda ahí hasta
        // que arranca el fade-out -- así se ve "brotando" en vez de
        // aparecer entero de golpe.
        float growPhase = Math.min(1.0F, t / GROW_FRACTION);
        double riseHeight = bbHeight * RISE_HEIGHT_FRACTION_OF_BODY;
        double currentLength = growPhase * riseHeight;

        Vec3[] points = new Vec3[SEGMENTS_PER_FILAMENT + 1];
        for (int i = 0; i <= SEGMENTS_PER_FILAMENT; i++) {
            double u = (double) i / SEGMENTS_PER_FILAMENT; // 0 = anclado en el cuerpo, 1 = la punta
            double y = u * currentLength;

            // El vaivén se apaga cerca de la base (anclada, no se mueve) y
            // es más libre cerca de la punta -- como una llama sujeta por
            // abajo pero suelta arriba.
            double swayAngle = u * SWAY_FREQUENCY * Math.PI * 2.0 * f.swaySpeedMul + f.swayPhase
                    + timeSeconds * 1.5;
            double swayScale = u; // 0 en la base, 1 en la punta
            double swayX = Math.sin(swayAngle) * SWAY_AMPLITUDE * swayScale;
            double swayZ = Math.cos(swayAngle * 0.7) * SWAY_AMPLITUDE * 0.6 * swayScale;

            points[i] = anchor.add(swayX, y, swayZ);
        }

        for (int i = 0; i < SEGMENTS_PER_FILAMENT; i++) {
            addRibbonSegment(builder, matrix, points[i], points[i + 1],
                    FILAMENT_WIDTH, FILAMENT_R, FILAMENT_G, FILAMENT_B, FILAMENT_ALPHA * alpha);
        }
    }

    private static void addRibbonSegment(BufferBuilder builder, Matrix4f matrix,
                                         Vec3 fromLocal, Vec3 toLocal,
                                         float width, float r, float g, float b, float alpha) {
        Vec3 segmentDir = toLocal.subtract(fromLocal);
        if (segmentDir.lengthSqr() < 1.0e-8) {
            return;
        }

        Vec3 midLocal = fromLocal.add(toLocal).scale(0.5);
        Vec3 towardCamera = midLocal.scale(-1.0); // la cámara está en el origen de este espacio
        Vec3 perp = segmentDir.cross(towardCamera);
        if (perp.lengthSqr() < 1.0e-6) {
            perp = segmentDir.cross(new Vec3(0, 1, 0));
            if (perp.lengthSqr() < 1.0e-6) {
                perp = segmentDir.cross(new Vec3(1, 0, 0));
            }
        }
        perp = perp.normalize();

        Vec3 fromA = fromLocal.add(perp.scale(width / 2.0));
        Vec3 fromB = fromLocal.subtract(perp.scale(width / 2.0));
        Vec3 toA = toLocal.add(perp.scale(width / 2.0));
        Vec3 toB = toLocal.subtract(perp.scale(width / 2.0));

        vertex(builder, matrix, fromA, r, g, b, alpha);
        vertex(builder, matrix, fromB, r, g, b, alpha);
        vertex(builder, matrix, toB, r, g, b, alpha);
        vertex(builder, matrix, toA, r, g, b, alpha);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float r, float g, float b, float alpha) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}