package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import com.nanookmod.entity.TwilightWispEntity;

import java.util.List;

/**
 * Estela geométrica ("ribbon") verde neón que sigue al TwilightWispEntity,
 * armada a partir del historial de posiciones que guarda la entidad
 * (ver TwilightWispEntity#trailHistory).
 *
 * v2 -- "más estilo neón": en vez de una sola cinta plana, dibuja CADA
 * segmento dos veces, como un tubo de neón real:
 *   1) GLOW: cinta ancha, verde saturado, alfa bajo -- es el halo/resplandor
 *      que se difumina hacia los costados.
 *   2) CORE: cinta angosta por ENCIMA del glow, casi blanca, alfa alto --
 *      es el "filamento" brillante del centro del tubo.
 * Esa combinación (núcleo claro + halo de color) es lo que hace que algo se
 * lea como "neón" y no como una cinta de color liso. Además le agregué un
 * pulso sutil de brillo (respira) para que no se vea estático.
 *
 * Sigue usando RenderType.lightning() (igual que los rayos de vainilla):
 * quads sin textura, sin culling, blending ADITIVO -- el glow real se
 * consigue sumando luz sobre lo que ya está dibujado, no pintando una
 * textura semitransparente encima.
 *
 * IMPORTANTE sobre el ancho de la cinta: cada segmento se orienta hacia la
 * CÁMARA (billboard), no contra un eje fijo. La primera versión usaba
 * "arriba del mundo" para calcular el perpendicular, lo que dejaba la cinta
 * siempre plana en horizontal (como una alfombra) -- se veía perfecto desde
 * arriba/abajo pero de canto (casi invisible) desde el costado, que es el
 * ángulo normal de juego. Por eso se "desaparecía" en ciertos ángulos.
 */
public class TwilightWispTrailRenderLayer extends GeoRenderLayer<TwilightWispEntity> {

    // Halo exterior: tono #9cf6de (cian/menta neón).
    private static final float GLOW_R = 0.6118f;
    private static final float GLOW_G = 0.9647f;
    private static final float GLOW_B = 0.8706f;
    private static final float GLOW_WIDTH = 0.60f; // más ancho que antes -- es el resplandor, tiene que difuminarse
    private static final float GLOW_ALPHA = 0.55f;

    // Núcleo interior: el mismo tono #9cf6de pero aclarado (mezclado con
    // blanco) -- sigue siendo el filamento brillante, ahora en la misma
    // familia de color que el halo en vez de tirar a blanco puro.
    private static final float CORE_R = 0.8059f;
    private static final float CORE_G = 0.9824f;
    private static final float CORE_B = 0.9353f;
    private static final float CORE_WIDTH = 0.11f;
    private static final float CORE_ALPHA = 0.95f;

    // Pulso de brillo tipo "letrero de neón respirando" -- sutil a propósito,
    // si lo subís mucho parpadea en vez de respirar.
    private static final float PULSE_SPEED = 0.12f;
    private static final float PULSE_AMOUNT = 0.15f;

    public TwilightWispTrailRenderLayer(GeoRenderer<TwilightWispEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(PoseStack poseStack, TwilightWispEntity animatable, BakedGeoModel bakedModel,
                        RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                        float partialTick, int packedLight, int packedOverlay) {

        List<Vec3> history = animatable.getTrailHistory();
        if (history.size() < 2) {
            return; // recién spawneado, todavía no hay suficientes puntos
        }

        // Posición actual interpolada (mismo cálculo que usa vainilla para
        // el movimiento suave entre ticks) -- el PoseStack que llega acá ya
        // está trasladado a este punto, así que todo lo que dibujemos tiene
        // que estar expresado COMO OFFSET relativo a él, no en coordenadas
        // de mundo.
        Vec3 currentWorldPos = animatable.getPosition(partialTick);

        // Posición de la cámara, expresada relativa a currentWorldPos (mismo
        // truco que con los puntos del historial) -- la necesitamos para que
        // cada segmento pueda orientarse encarando al jugador en vez de
        // quedar fijo en un plano horizontal.
        Vec3 cameraLocal = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(currentWorldPos);

        VertexConsumer trailBuffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        float age = animatable.tickCount + partialTick;
        float pulse = 1.0f + PULSE_AMOUNT * Mth.sin(age * PULSE_SPEED);

        // Dos pasadas completas (glow ancho primero, core angosto encima) en
        // vez de intercalar por segmento -- así el core de un segmento nunca
        // queda tapado por el glow del segmento siguiente.
        renderRibbon(trailBuffer, matrix, history, currentWorldPos, cameraLocal,
                GLOW_WIDTH, GLOW_R, GLOW_G, GLOW_B, GLOW_ALPHA * pulse);
        renderRibbon(trailBuffer, matrix, history, currentWorldPos, cameraLocal,
                CORE_WIDTH, CORE_R, CORE_G, CORE_B, CORE_ALPHA * pulse);
    }

    private void renderRibbon(VertexConsumer buffer, Matrix4f matrix, List<Vec3> history, Vec3 currentWorldPos,
                               Vec3 cameraLocal, float baseWidth, float r, float g, float b, float baseAlpha) {
        int segments = history.size() - 1;
        for (int i = 0; i < segments; i++) {
            Vec3 fromLocal = history.get(i).subtract(currentWorldPos);
            Vec3 toLocal = history.get(i + 1).subtract(currentWorldPos);

            Vec3 segmentDir = toLocal.subtract(fromLocal);
            if (segmentDir.lengthSqr() < 1.0e-6) {
                continue; // el wisp no se movió entre estos dos puntos, no hay segmento que dibujar
            }

            // Perpendicular hacia la cámara: en vez de cruzar contra "arriba
            // del mundo" (fijo), cruzamos contra la dirección hacia la
            // cámara desde el punto medio del segmento. Así la cinta rota
            // sola para quedar siempre de frente al jugador, sin importar
            // desde qué ángulo mire (arriba, costado, lo que sea) -- esto es
            // lo que soluciona que se viera invisible desde ciertos ángulos.
            Vec3 midLocal = fromLocal.add(toLocal).scale(0.5);
            Vec3 towardCamera = cameraLocal.subtract(midLocal);
            Vec3 perp = segmentDir.cross(towardCamera);
            if (perp.lengthSqr() < 1.0e-6) {
                // Caso raro: la cámara está justo alineada con el segmento
                // (mirando derecho a lo largo de la cinta) -- ahí cualquier
                // ancho se ve de canto igual, así que un respaldo fijo alcanza.
                perp = segmentDir.cross(new Vec3(0, 1, 0));
                if (perp.lengthSqr() < 1.0e-6) {
                    perp = segmentDir.cross(new Vec3(1, 0, 0));
                }
            }
            perp = perp.normalize();

            // t=1 en la cabeza (i=0), t=0 en la punta de la cola -- controla
            // tanto el ancho como el alfa, así la cinta se afina Y se
            // desvanece a la vez en vez de terminar en un corte brusco.
            float tFrom = 1.0f - (float) i / segments;
            float tTo = 1.0f - (float) (i + 1) / segments;

            Vec3 fromA = fromLocal.add(perp.scale(baseWidth * tFrom / 2.0));
            Vec3 fromB = fromLocal.subtract(perp.scale(baseWidth * tFrom / 2.0));
            Vec3 toA = toLocal.add(perp.scale(baseWidth * tTo / 2.0));
            Vec3 toB = toLocal.subtract(perp.scale(baseWidth * tTo / 2.0));

            vertex(buffer, matrix, fromA, r, g, b, baseAlpha * tFrom);
            vertex(buffer, matrix, fromB, r, g, b, baseAlpha * tFrom);
            vertex(buffer, matrix, toB, r, g, b, baseAlpha * tTo);
            vertex(buffer, matrix, toA, r, g, b, baseAlpha * tTo);
        }
    }

    private void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, float r, float g, float b, float alpha) {
        // RenderType.lightning() usa el formato POSITION_COLOR (sin UV, sin
        // lightmap) -- por eso no hay .uv()/.lightmap() acá, es el mismo
        // patrón que usa LightningBoltRenderer en vainilla.
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}
