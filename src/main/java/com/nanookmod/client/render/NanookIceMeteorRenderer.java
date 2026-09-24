package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.entity.NanookIceMeteorProjectile;
import com.nanookmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer del meteoro de hielo del canalizado. Gira sobre su propio eje
 * mientras cae para que se lea claro que es una roca cayendo y no un bloque
 * flotando.
 *
 * Modelo:  assets/nanookmod/models/item/nanook_ice_meteor.json
 * Textura: assets/nanookmod/textures/entity/nanook_ice_meteor.png
 */
public class NanookIceMeteorRenderer extends ArrowRenderer<NanookIceMeteorProjectile> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("nanookmod", "textures/entity/nanook_ice_meteor.png");

    private static final float SCALE = 1.8F;
    private static final float SPIN_DEGREES_PER_TICK = 14.0F;

    public NanookIceMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NanookIceMeteorProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        float spin = (entity.tickCount + partialTick) * SPIN_DEGREES_PER_TICK;
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(ModItems.NANOOK_ICE_METEOR_ITEM.get()),
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
    public ResourceLocation getTextureLocation(NanookIceMeteorProjectile entity) {
        return TEXTURE;
    }
}
