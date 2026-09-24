package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.entity.NanookClawProjectile;
import com.nanookmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class NanookClawProjectileRenderer extends ArrowRenderer<NanookClawProjectile> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("nanookmod", "textures/entity/nanook_claw_projectile.png");

    public NanookClawProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NanookClawProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        float scale = 3.0f;
        poseStack.scale(scale, scale, scale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(ModItems.CLAW_PROJECTILE_ITEM.get()),
                ItemDisplayContext.FIXED,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                (int) entity.getId()
        );

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NanookClawProjectile entity) {
        return TEXTURE;
    }
}