package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.entity.NanookIceSpikeProjectile;
import com.nanookmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer del pincho de hielo. Mismo patrón validado del mod para
 * proyectiles con modelo custom: ArrowRenderer + item dummy dibujado con
 * ItemRenderer (NO GeoEntityRenderer -- ver sección 4 del resumen técnico).
 *
 * El modelo real lo pones vos en:
 *   assets/nanookmod/models/item/nanook_ice_spike.json
 * y la textura en:
 *   assets/nanookmod/textures/entity/nanook_ice_spike.png
 */
public class NanookIceSpikeRenderer extends ArrowRenderer<NanookIceSpikeProjectile> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("nanookmod", "textures/entity/nanook_ice_spike.png");

    // El pincho se ve mejor bastante más grande que el modelo base -- ajustá
    // este número cuando tengas el modelo final de Blockbench.
    private static final float SCALE = 1.5F;

    public NanookIceSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NanookIceSpikeProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());

        // Solo yaw: el pincho sale del suelo, no queremos que se incline con
        // el pitch como una flecha.
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(ModItems.NANOOK_ICE_SPIKE_ITEM.get()),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NanookIceSpikeProjectile entity) {
        return TEXTURE;
    }
}
