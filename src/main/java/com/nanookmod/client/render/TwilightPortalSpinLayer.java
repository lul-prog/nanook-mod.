package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanookmod.NanookMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Capa extra para piezas del portal que tienen su propia textura/geo,
 * separadas del modelo base para poder controlar en qué orden se dibujan
 * (ver charla: "star" se sacó de la base a una capa propia para poder
 * ponerla DESPUÉS de "small" y que quede dibujada encima).
 *
 * Combina 2 efectos independientes, tal como estaban en Blockbench:
 *   1. Rotación real en Z por código (la velocidad que sacamos del
 *      animation.json), en vez del hack viejo de 12 frames pre-rotados.
 *   2. Flipbook de textura: la textura en sí también cicla frames (esto
 *      se nos había pasado -- en el bbmodel cada capa tenía
 *      frame_time=1 tick, loop, o sea es una animación de textura aparte
 *      de la rotación, no un reemplazo de esta).
 */
public class TwilightPortalSpinLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {

    private final ResourceLocation modelResource;
    private final ResourceLocation[] frameTextures; // 1 elemento si es textura fija
    private final int ticksPerFrame;

    // Offset constante (mismas unidades que el geo.json, 16 = 1 bloque
    // -- se dividen por 16 al aplicarlas).
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    // Escala por eje. Las capas small/middle/big usan la misma en los 3
    // ejes; "star" necesita X e Y distintos (viene estirada de fábrica).
    private final double scaleX;
    private final double scaleY;
    private final double scaleZ;

    // Pivot del hueso alrededor del cual rota/escala (sacado del geo.json).
    private final double pivotX;
    private final double pivotY;
    private final double pivotZ;

    // Grados por segundo de rotación en Z (animada, cambia con el tiempo).
    private final double degreesPerSecond;

    // Rotación fija adicional, aplicada SIEMPRE antes de escalar (no
    // cambia con el tiempo). Sirve para reusar la misma geometría girada
    // -- ej. dibujar "star_h" una vez tal cual (horizontal) y otra vez
    // con 90° extra para que el mismo estiramiento quede vertical, en
    // vez de que ambas ramas compartan una escala mal orientada.
    private final double baseRotationDegrees;

    // Parpadeo de escala opcional (ej. "star_h": alterna entre 2 poses
    // cada 0.05s en Blockbench -- ver charla). Si pulseHz == 0, se ignora
    // y se usa scaleX/Y/Z tal cual.
    private final Vec3 pulseScaleA;
    private final Vec3 pulseScaleB;
    private final double pulseHz;

    // Textura actual del frame -- se recalcula cada render() según el
    // tickCount, así que el GeoModel de abajo siempre pregunta por la
    // última calculada.
    private ResourceLocation currentFrameTexture;

    // Si es true, esta capa se dibuja en modo cutout (opaco) en vez de
    // translúcido emisivo. Pensado para una copia extra de una capa ya
    // existente (misma textura, mismo geo) usada como "fondo" que bloquee
    // la vista detrás del portal -- ver charla sobre por qué NO alcanza
    // con volver opacas TODAS las capas (rompía el efecto visual, opción
    // B descartada). Todas las capas existentes siguen con esto en false
    // (translúcidas, sin cambios de comportamiento).
    private final boolean opaque;

    private final GeoModel<T> spinModel = new GeoModel<T>() {
        @Override
        public ResourceLocation getModelResource(T animatable) {
            return modelResource;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return currentFrameTexture;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return null;
        }
    };

    /** Textura fija (sin flipbook) -- ej. "star". Escala uniforme. */
    public TwilightPortalSpinLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   ResourceLocation texture, Vec3 offset, Vec3 pivot,
                                   double scale, double degreesPerSecond) {
        this(entityRenderer, modelResource, new ResourceLocation[]{texture}, 1, offset, pivot,
                new Vec3(scale, scale, scale), new Vec3(scale, scale, scale), 0, 0, degreesPerSecond, false);
    }

    /** Textura fija (sin flipbook), escala por eje + rotación fija extra -- ej. "star" girada 90°. */
    public TwilightPortalSpinLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   ResourceLocation texture, Vec3 offset, Vec3 pivot,
                                   Vec3 scaleA, Vec3 scaleB, double pulseHz,
                                   double baseRotationDegrees, double degreesPerSecond) {
        this(entityRenderer, modelResource, new ResourceLocation[]{texture}, 1, offset, pivot,
                scaleA, scaleB, pulseHz, baseRotationDegrees, degreesPerSecond, false);
    }

    /**
     * Versión flipbook: cicla por frameTextures (1 frame cada
     * ticksPerFrame ticks, loop) ADEMÁS de rotar -- ej. small/middle/big,
     * que en Blockbench tenían frame_time=1 (1 tick por frame).
     */
    public TwilightPortalSpinLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   ResourceLocation[] frameTextures, int ticksPerFrame,
                                   Vec3 offset, Vec3 pivot, double scale, double degreesPerSecond) {
        this(entityRenderer, modelResource, frameTextures, ticksPerFrame, offset, pivot,
                new Vec3(scale, scale, scale), new Vec3(scale, scale, scale), 0, 0, degreesPerSecond, false);
    }

    /**
     * Misma versión flipbook de arriba, pero permitiendo pedir la
     * variante opaca (para usar como capa de fondo que bloquee la vista,
     * reusando la misma textura/geo de una capa translúcida existente).
     */
    public TwilightPortalSpinLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   ResourceLocation[] frameTextures, int ticksPerFrame,
                                   Vec3 offset, Vec3 pivot, double scale, double degreesPerSecond,
                                   boolean opaque) {
        this(entityRenderer, modelResource, frameTextures, ticksPerFrame, offset, pivot,
                new Vec3(scale, scale, scale), new Vec3(scale, scale, scale), 0, 0, degreesPerSecond, opaque);
    }

    /** Versión completa: flipbook + parpadeo de escala + rotación fija + rotación animada. */
    public TwilightPortalSpinLayer(GeoRenderer<T> entityRenderer, ResourceLocation modelResource,
                                   ResourceLocation[] frameTextures, int ticksPerFrame,
                                   Vec3 offset, Vec3 pivot,
                                   Vec3 scaleA, Vec3 scaleB, double pulseHz,
                                   double baseRotationDegrees, double degreesPerSecond,
                                   boolean opaque) {
        super(entityRenderer);
        this.modelResource = modelResource;
        this.frameTextures = frameTextures;
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
        this.currentFrameTexture = frameTextures[0];
        this.offsetX = offset.x;
        this.offsetY = offset.y;
        this.offsetZ = offset.z;
        this.pivotX = pivot.x;
        this.pivotY = pivot.y;
        this.pivotZ = pivot.z;
        this.scaleX = scaleA.x;
        this.scaleY = scaleA.y;
        this.scaleZ = scaleA.z;
        this.pulseScaleA = scaleA;
        this.pulseScaleB = scaleB;
        this.pulseHz = pulseHz;
        this.baseRotationDegrees = baseRotationDegrees;
        this.degreesPerSecond = degreesPerSecond;
        this.opaque = opaque;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        int frameIndex = (animatable.tickCount / ticksPerFrame) % frameTextures.length;
        this.currentFrameTexture = frameTextures[frameIndex];

        BakedGeoModel spinBakedModel = this.spinModel.getBakedModel(modelResource);
        RenderType spinRenderType = opaque
                ? RenderType.entityCutoutNoCull(currentFrameTexture)
                : RenderType.entityTranslucentEmissive(currentFrameTexture);
        VertexConsumer spinBuffer = bufferSource.getBuffer(spinRenderType);

        float ageInSeconds = (animatable.tickCount + partialTick) / 20.0F;
        float angleDegrees = (float) (ageInSeconds * degreesPerSecond);

        double curScaleX = scaleX;
        double curScaleY = scaleY;
        double curScaleZ = scaleZ;
        if (pulseHz > 0) {
            // Onda cuadrada: la mitad del tiempo scaleA, la otra mitad
            // scaleB -- así es el parpadeo real (no un fundido suave).
            boolean phaseA = ((long) (ageInSeconds * pulseHz * 2)) % 2 == 0;
            Vec3 s = phaseA ? pulseScaleA : pulseScaleB;
            curScaleX = s.x;
            curScaleY = s.y;
            curScaleZ = s.z;
        }

        poseStack.pushPose();
        poseStack.translate(offsetX / 16.0, offsetY / 16.0, offsetZ / 16.0);
        // Movemos al pivot, rotamos/escalamos, y volvemos -- así queda
        // centrado en el mismo punto que tenía en Blockbench en vez de
        // girar/escalar alrededor del origin del modelo.
        poseStack.translate(pivotX / 16.0, pivotY / 16.0, pivotZ / 16.0);
        if (degreesPerSecond != 0 || baseRotationDegrees != 0) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees + (float) baseRotationDegrees));
        }
        if (curScaleX != 1.0 || curScaleY != 1.0 || curScaleZ != 1.0) {
            poseStack.scale((float) curScaleX, (float) curScaleY, (float) curScaleZ);
        }
        poseStack.translate(-pivotX / 16.0, -pivotY / 16.0, -pivotZ / 16.0);

        getRenderer().reRender(spinBakedModel, poseStack, bufferSource, animatable, spinRenderType,
                spinBuffer, partialTick, 15, packedOverlay, 1f, 1f, 1f, 1f);

        poseStack.popPose();
    }
}