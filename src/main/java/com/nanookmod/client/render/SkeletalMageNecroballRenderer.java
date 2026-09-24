package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.entity.SkeletalMageNecroball;
import com.nanookmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Mismo patrón que NanookClawProjectileRenderer (docx sección 4): item
 * dummy renderizado a mano en vez de un modelo GeckoLib, porque GeckoLib
 * castea a LivingEntity antes de aplicar rotaciones y la orientación de
 * proyectiles queda mal.
 */
public class SkeletalMageNecroballRenderer extends ArrowRenderer<SkeletalMageNecroball> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("nanookmod", "textures/item/necro_ball.png");

    // A diferencia del NecroticKnight (que escala su brillo con el nivel de
    // Corrupción vía una capa emisiva aparte), el necroball es un item
    // renderizado a mano sin glowmask propia, así que "que siempre brille"
    // se logra forzando el lightmap al máximo (15 bloque / 15 cielo) en vez
    // de pasarle el packedLight real del mundo -- queda con brillo pleno
    // constante sin importar si está de día, de noche o en una cueva.
    private static final int FULL_BRIGHT_LIGHT = LightTexture.pack(15, 15);

    public SkeletalMageNecroballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SkeletalMageNecroball entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(ModItems.NECRO_BALL_ITEM.get()),
                ItemDisplayContext.FIXED,
                FULL_BRIGHT_LIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                (int) entity.getId()
        );

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SkeletalMageNecroball entity) {
        return TEXTURE;
    }
}