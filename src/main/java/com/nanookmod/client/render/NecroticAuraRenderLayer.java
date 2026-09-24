package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Aura necrótica (fuego cian) como CAPA extra del render del mob, en vez de
 * una entidad satélite aparte (SkeletalWarriorAuraVfx, ahora en desuso).
 *
 * Ventajas sobre la entidad satélite:
 *  - Cero entidades extra (nada que trackear/sincronizar por red, nada que
 *    tickear, nada que spawnear/despawnear).
 *  - Se dibuja DENTRO del mismo render() del mob principal, usando el mismo
 *    PoseStack que YA está posicionado y rotado (yBodyRot interpolado) para
 *    el modelo principal -- por diseño, es IMPOSIBLE que quede desfasada o
 *    mirando para el lado que no es, porque no hay una segunda transformada
 *    que calcular a mano.
 *
 * Reutilizable entre mobs con proporciones distintas: el modelo/textura y
 * el offset de posición son parámetros del constructor, no están
 * hardcodeados. Ej.:
 *   - Warrior/Mage: new NecroticAuraRenderLayer<>(this)  (usa los defaults
 *     de acá abajo, offset 0,0,0 -- el .geo.json ya está calibrado para
 *     las proporciones del warrior).
 *   - Knight (misma textura, mob más grande): new NecroticAuraRenderLayer<>(this,
 *     0.0, 0.4, 0.0)  -- probá estos números hasta que quede pegado bien,
 *     ver comentario en offsetY/offsetX/offsetZ más abajo.
 *   - Un mob con aura totalmente distinta (ej. el caballo): usar el
 *     constructor completo pasando su propio geo/textura.
 */
public class NecroticAuraRenderLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {

    private static final ResourceLocation DEFAULT_MODEL_RESOURCE =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/skeletal_warrior_aura_vfx.geo.json");
    private static final String DEFAULT_TEXTURE_PREFIX = "textures/entity/skeletal_warrior/aura_frame_";
    private static final int DEFAULT_FRAME_COUNT = 8;

    // Timing por defecto (mismo que tenía la entidad satélite vieja):
    // original del pack es 1 frame/tick (parpadea demasiado en Java), lo
    // bajamos a 4 ticks/frame. Instancias que necesiten otra velocidad
    // (ej. varias capas del mismo portal girando a ritmos distintos) usan
    // el constructor completo que acepta ticksPerFrame.
    private static final int DEFAULT_TICKS_PER_FRAME = 4;

    private final ResourceLocation modelResource;
    private final ResourceLocation[] frameTextures;
    private final int frameCount;
    private final int ticksPerFrame;

    // Desplazamiento aplicado ANTES de dibujar el aura, en el espacio local
    // ya rotado/orientado del mob (no en coordenadas del mundo): X = a los
    // costados relativo hacia dónde mira, Y = arriba/abajo, Z = adelante
    // (+) / atrás (-) relativo hacia dónde mira. Ajustalo a mano hasta que
    // el aura quede pegada al cuerpo del mob que corresponda.
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    // Multiplicador de escala aplicado ANTES de dibujar (alrededor del
    // origen local, después del offset). Por qué existe: las 3 capas del
    // portal (small/middle/big) salieron del .bbmodel con EXACTAMENTE la
    // misma geometría (168x168) -- sin esto, las 3 quedan superpuestas
    // pixel a pixel en vez de crear el efecto de profundidad escalonada.
    // 1.0 = tamaño tal cual viene del geo.json.
    private final double scale;

    // GeoModel "genérico": el aura es siempre la misma geometría sin
    // animación de huesos, así que no hace falta un GeoModel<T> específico
    // por mob -- este mismo sirve para cualquier GeoAnimatable al que le
    // enchufemos esta capa.
    private final GeoModel<T> auraModel = new GeoModel<T>() {
        @Override
        public ResourceLocation getModelResource(T animatable) {
            return modelResource;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return frameTextures[0];
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return null;
        }
    };

    /** Warrior/Mage: mismo geo/textura de siempre, sin offset. */
    public NecroticAuraRenderLayer(GeoRenderer<T> entityRenderer) {
        this(entityRenderer, DEFAULT_MODEL_RESOURCE, DEFAULT_TEXTURE_PREFIX, DEFAULT_FRAME_COUNT, 0, 0, 0);
    }

    /** Mismo geo/textura del warrior, pero con offset propio (ej. Knight). */
    public NecroticAuraRenderLayer(GeoRenderer<T> entityRenderer, double offsetX, double offsetY, double offsetZ) {
        this(entityRenderer, DEFAULT_MODEL_RESOURCE, DEFAULT_TEXTURE_PREFIX, DEFAULT_FRAME_COUNT,
                offsetX, offsetY, offsetZ);
    }

    /**
     * Constructor completo: geo/textura propios (ej. un aura totalmente
     * distinta para el caballo).
     *
     * @param modelResource     ruta al .geo.json del aura.
     * @param texturePathPrefix ruta + prefijo de los frames, SIN el número
     *                          ni ".png" -- ej. "textures/entity/skeletal_warrior/aura_frame_"
     *                          para que arme aura_frame_0.png, aura_frame_1.png, etc.
     * @param frameCount        cantidad de frames (aura_frame_0.png .. aura_frame_{frameCount-1}.png).
     */
    public NecroticAuraRenderLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   String texturePathPrefix, int frameCount,
                                   double offsetX, double offsetY, double offsetZ) {
        this(entityRenderer, modelResource, texturePathPrefix, frameCount, offsetX, offsetY, offsetZ,
                DEFAULT_TICKS_PER_FRAME);
    }

    /**
     * Igual que el constructor completo de arriba, pero con velocidad de
     * ciclo propia -- útil cuando varias capas comparten entidad y cada
     * una debería girar a un ritmo distinto (ej. las 3 capas del
     * TwilightPortalEntity).
     *
     * @param ticksPerFrame cuántos ticks dura cada sprite antes de pasar
     *                      al siguiente. Más alto = más lento.
     */
    public NecroticAuraRenderLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   String texturePathPrefix, int frameCount,
                                   double offsetX, double offsetY, double offsetZ, int ticksPerFrame) {
        this(entityRenderer, modelResource, texturePathPrefix, frameCount, offsetX, offsetY, offsetZ,
                ticksPerFrame, 1.0);
    }

    /**
     * Constructor completo con escala propia -- para capas que salieron
     * del .bbmodel todas del mismo tamaño (como las 3 del portal) y
     * necesitan diferenciarse visualmente sin volver a exportar nada.
     *
     * @param scale multiplicador de tamaño, 1.0 = tal cual el geo.json.
     */
    public NecroticAuraRenderLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   String texturePathPrefix, int frameCount,
                                   double offsetX, double offsetY, double offsetZ, int ticksPerFrame,
                                   double scale) {
        super(entityRenderer);
        this.modelResource = modelResource;
        this.frameCount = frameCount;
        this.frameTextures = new ResourceLocation[frameCount];
        for (int i = 0; i < frameCount; i++) {
            this.frameTextures[i] = new ResourceLocation(NanookMod.MOD_ID, texturePathPrefix + i + ".png");
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.ticksPerFrame = ticksPerFrame;
        this.scale = scale;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        int frame = (animatable.tickCount / ticksPerFrame) % frameCount;
        ResourceLocation texture = frameTextures[frame];

        BakedGeoModel auraBakedModel = this.auraModel.getBakedModel(modelResource);
        // Ver GlowingDepthWriteRenderType para el detalle completo de por
        // qué se llegó a esta versión (v2 aditivo, v3 sin depth write, v4
        // con sombreado por cara -- todas probadas y descartadas antes de
        // esta). Extraída a su propia clase para que NecroticKnightGlowLayer
        // (ojos/arma brillando) la reuse tal cual.
        RenderType auraRenderType = GlowingDepthWriteRenderType.get(texture);
        VertexConsumer auraBuffer = bufferSource.getBuffer(auraRenderType);

        poseStack.pushPose();
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            poseStack.translate(offsetX, offsetY, offsetZ);
        }
        if (scale != 1.0) {
            poseStack.scale((float) scale, (float) scale, (float) scale);
        }

        // Solo aplica a NecroticKnightEntity (Warrior/Mage, que también
        // usan esta capa genérica, no tienen render alpha propio -- se
        // quedan en 1f, sin cambios de comportamiento para ellos). Mismo
        // motivo que en NecroticKnightGlowLayer: sin esto, el aura se
        // quedaría encendida aunque el cuerpo esté invisible durante el
        // Vanish, o tapando el flash de daño.
        float bodyAlpha = animatable instanceof NecroticKnightEntity knight ? knight.getRenderAlpha() / 255f : 1f;

        // Con blending normal, la textura ya trae su propio degradé de
        // alfa -- no hace falta forzar el brillo a mano como con eyes().
        // Si igual lo querés más intenso/tenue, este último número
        // (alpha) sigue siendo la perilla, pero ahora sí se comporta como
        // transparencia normal (0 = invisible, 1 = tan opaco como permita
        // el alfa de la textura -- no hay "más allá de 1" útil acá).
        getRenderer().reRender(auraBakedModel, poseStack, bufferSource, animatable, auraRenderType,
                auraBuffer, partialTick, 15, packedOverlay, 1f, 1f, 1f, bodyAlpha);

        poseStack.popPose();
    }
}