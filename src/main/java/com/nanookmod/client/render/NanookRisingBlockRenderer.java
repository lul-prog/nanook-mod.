package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.NanookMod;
import com.nanookmod.entity.NanookRisingBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

/**
 * Dibuja NanookRisingBlock en sus dos modos:
 *
 *   FLY   -> el escombro que gira en el aire (como siempre).
 *   BULGE -> la copia visual de un bloque de suelo que sube inclinada, se
 *            queda un rato y se hunde (la "abolladura"). Ver la explicación
 *            completa en NanookRisingBlock.
 *
 * Se usa renderSingleBlock (el mismo camino que el render de ítems tipo
 * bloque): es corto y no necesita el seed de modelo. Limitación conocida:
 * no aplica el tinte por bioma, así que un bloque de césped se ve con el
 * verde por defecto. En el bioma de Nanook casi todo es nieve/tierra, que
 * no se tiñe, así que no se nota; si algún día molesta, hay que pasar a
 * tesselateBlock con el ClientLevel (como hace FallingBlockRenderer).
 */
public class NanookRisingBlockRenderer extends EntityRenderer<NanookRisingBlock> {

    /** Escala del escombro volador: un pelín más chico que un bloque real. */
    private static final float FLY_SCALE = 0.85F;

    /**
     * Elevación mínima de la copia respecto al bloque real. Sin esto, el
     * primer fotograma de la copia queda EXACTAMENTE sobre el bloque de
     * abajo y las caras superiores se pelean por el mismo píxel (z-fighting).
     */
    private static final float BULGE_EPSILON = 0.004F;

    public NanookRisingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(NanookRisingBlock entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getRenderedBlockState();
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        if (entity.isBulge()) {
            renderBulge(entity, state, partialTick, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();

        // Giro cosmético sobre su propio centro.
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getSpinYaw(partialTick)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getSpinPitch(partialTick)));
        poseStack.scale(FLY_SCALE, FLY_SCALE, FLY_SCALE);
        // renderSingleBlock dibuja desde la esquina (0,0,0) a (1,1,1), así que
        // hay que correrlo medio bloque para que gire centrado.
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * El origen de la entidad es la CARA SUPERIOR del bloque de piso. La
     * copia ocupa por lo tanto de y-1 a y (relativo al origen), y gira
     * sobre el centro de ese cubo.
     */
    private void renderBulge(NanookRisingBlock entity, BlockState state, float partialTick,
                             PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float offset = entity.getBulgeOffset(partialTick);
        float tiltFactor = entity.getBulgeTiltFactor(partialTick);

        poseStack.pushPose();

        // 1) Al centro del cubo, ya elevado.
        poseStack.translate(0.0D, offset + BULGE_EPSILON - 0.5D, 0.0D);

        // 2) Inclinación, escalada de 0 a 1. Se parte de la identidad y se
        //    interpola hacia la inclinación objetivo; NO se modifica el
        //    cuaternión sincronizado (es el dato de la entidad).
        if (tiltFactor > 0.001F) {
            Quaternionf scaled = new Quaternionf().slerp(entity.getBulgeTilt(), tiltFactor);
            poseStack.mulPose(scaled);
        }

        // 3) renderSingleBlock dibuja de (0,0,0) a (1,1,1): centrarlo.
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NanookRisingBlock entity) {
        // El bloque se dibuja con el atlas de bloques; este valor no se usa
        // en la práctica, pero EntityRenderer obliga a devolver algo.
        return new ResourceLocation(NanookMod.MOD_ID, "textures/particle/snowy_dust_0.png");
    }
}
