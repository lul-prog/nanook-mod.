package com.nanookmod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.entity.TwilightWispEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Dibuja la estela verde neón de todos los TwilightWispEntity presentes en
 * el nivel.
 *
 * POR QUÉ NO ES UN GeoRenderLayer (como era antes, TwilightWispTrailRenderLayer):
 * el PoseStack que le llega a un render layer de GeckoLib está en espacio
 * LOCAL del modelo -- ya trasladado Y ROTADO según hacia dónde mira la
 * entidad, porque así es como GeckoLib pega capas (auras, capas de armadura,
 * etc.) al cuerpo para que giren junto con él. Nuestra estela necesita
 * coordenadas de MUNDO reales -- las posiciones pasadas del historial no
 * "rotan" con la entidad -- así que calcularla ahí contaminaba toda la
 * geometría con la rotación de la entidad: a veces coincidía por casualidad
 * y se veía bien, la mayoría de las veces no (por eso "según el ángulo" y
 * después directamente invisible al intentar arreglar el billboard).
 *
 * Acá enganchamos directo a RenderLevelStageEvent, el mismo hook que ya usa
 * AuroraSkyRenderer en este mod -- el PoseStack de este evento está
 * centrado en la CÁMARA, sin ninguna rotación de entidad de por medio, así
 * que las coordenadas de mundo funcionan directo.
 *
 * FIX (por qué no se veía): cada segmento de la cinta se calcula como un
 * billboard orientado hacia la cámara (perpendicular = segmentDir x
 * haciaCamara), y ese signo puede quedar "al revés" según desde qué lado
 * mires al wisp -- exactamente igual que con el plano de la aurora, la
 * mitad de los segmentos (o todos, según el ángulo) terminan con la única
 * cara visible mirando hacia el jugador del lado que OpenGL descarta por
 * defecto (culling). La versión vieja (TwilightWispTrailRenderLayer, ya no
 * se usa) no tenía este problema porque dibujaba con RenderType.lightning(),
 * que trae "sin culling" incluido de fábrica; esta versión dibuja directo
 * con Tesselator sin pasar por ningún RenderType, así que perdimos ese
 * ajuste sin darnos cuenta. Se soluciona igual que en AuroraSkyRenderer:
 * RenderSystem.disableCull() mientras se dibuja.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TwilightWispTrailRenderer {

    // Halo exterior: tono #9cf6de (cian/menta neón).
    private static final float GLOW_R = 0.6118f;
    private static final float GLOW_G = 0.9647f;
    private static final float GLOW_B = 0.8706f;
    private static final float GLOW_WIDTH = 0.30f;
    private static final float GLOW_ALPHA = 0.55f;

    // Núcleo interior: el mismo tono aclarado (mezclado con blanco) -- el
    // "filamento" brillante del centro del tubo de neón.
    private static final float CORE_R = 0.8059f;
    private static final float CORE_G = 0.9824f;
    private static final float CORE_B = 0.9353f;
    private static final float CORE_WIDTH = 0.09f;
    private static final float CORE_ALPHA = 0.95f;

    // Pulso de brillo tipo "letrero de neón respirando".
    private static final float PULSE_SPEED = 0.12f;
    private static final float PULSE_AMOUNT = 0.15f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_PARTICLES: dibuja por encima de bloques y partículas, como
        // corresponde a un efecto de glow que se suma sobre la escena.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        boolean began = false;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TwilightWispEntity wisp)) {
                continue;
            }

            List<Vec3> history = wisp.getTrailHistory();
            if (history.size() < 2) {
                continue;
            }

            if (!began) {
                // Blending aditivo manual (Tesselator no pasa por el
                // sistema de RenderType/MultiBufferSource, así que hay que
                // armar el estado nosotros mismos) -- mismo resultado que
                // RenderType.lightning(): SRC_ALPHA + ONE, sin escribir al
                // depth buffer para no tapar nada detrás.
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.depthMask(false);
                // Ver el comentario de clase: sin esto, según el ángulo
                // desde el que mires al wisp, la cinta entera (o la mitad
                // de sus segmentos) se descarta por culling y no se dibuja
                // nada.
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                began = true;
            }

            float age = wisp.tickCount + partialTick;
            float pulse = 1.0f + PULSE_AMOUNT * Mth.sin(age * PULSE_SPEED);

            renderRibbon(builder, matrix, history, cameraPos,
                    GLOW_WIDTH, GLOW_R, GLOW_G, GLOW_B, GLOW_ALPHA * pulse);
            renderRibbon(builder, matrix, history, cameraPos,
                    CORE_WIDTH, CORE_R, CORE_G, CORE_B, CORE_ALPHA * pulse);
        }

        if (began) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static void renderRibbon(BufferBuilder builder, Matrix4f matrix, List<Vec3> history, Vec3 cameraPos,
                                      float baseWidth, float r, float g, float b, float baseAlpha) {
        int segments = history.size() - 1;
        for (int i = 0; i < segments; i++) {
            // Coordenadas relativas a la cámara -- el PoseStack de
            // RenderLevelStageEvent ya está centrado ahí, así que estas
            // restas nos dan directamente el espacio en el que hay que
            // dibujar, sin ninguna rotación de por medio.
            Vec3 fromLocal = history.get(i).subtract(cameraPos);
            Vec3 toLocal = history.get(i + 1).subtract(cameraPos);

            Vec3 segmentDir = toLocal.subtract(fromLocal);
            if (segmentDir.lengthSqr() < 1.0e-6) {
                continue; // el wisp no se movió entre estos dos puntos, no hay segmento que dibujar
            }

            // La cámara está en el ORIGEN de este espacio (ver arriba), así
            // que la dirección "hacia la cámara" desde cualquier punto es
            // simplemente ese punto invertido -- no hace falta restar nada
            // más para el billboard.
            Vec3 midLocal = fromLocal.add(toLocal).scale(0.5);
            Vec3 towardCamera = midLocal.scale(-1.0);
            Vec3 perp = segmentDir.cross(towardCamera);
            if (perp.lengthSqr() < 1.0e-6) {
                // Caso raro: la cámara está alineada con el segmento
                // (mirando derecho a lo largo de la cinta) -- ahí cualquier
                // ancho se ve de canto igual, un respaldo fijo alcanza.
                perp = segmentDir.cross(new Vec3(0, 1, 0));
                if (perp.lengthSqr() < 1.0e-6) {
                    perp = segmentDir.cross(new Vec3(1, 0, 0));
                }
            }
            perp = perp.normalize();

            // t=1 en la cabeza (i=0), t=0 en la punta de la cola.
            float tFrom = 1.0f - (float) i / segments;
            float tTo = 1.0f - (float) (i + 1) / segments;

            Vec3 fromA = fromLocal.add(perp.scale(baseWidth * tFrom / 2.0));
            Vec3 fromB = fromLocal.subtract(perp.scale(baseWidth * tFrom / 2.0));
            Vec3 toA = toLocal.add(perp.scale(baseWidth * tTo / 2.0));
            Vec3 toB = toLocal.subtract(perp.scale(baseWidth * tTo / 2.0));

            vertex(builder, matrix, fromA, r, g, b, baseAlpha * tFrom);
            vertex(builder, matrix, fromB, r, g, b, baseAlpha * tFrom);
            vertex(builder, matrix, toB, r, g, b, baseAlpha * tTo);
            vertex(builder, matrix, toA, r, g, b, baseAlpha * tTo);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float r, float g, float b, float alpha) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}
