package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanookmod.capability.NecromanticChainCapability;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

public class NecromanticChainRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final float TRANSPARENCY_ALPHA = NecromanticChainCapability.TRANSPARENCY_ALPHA;

    // Guardamos el renderer real del jugador (no solo la interfaz genérica)
    // para poder pedirle a ÉL la textura de skin ya resuelta, igual que hace
    // el cuerpo base. Resolverla nosotros mismos con
    // player.getSkinTextureLocation() puede apuntar a una textura que
    // todavía no se ha registrado (por ejemplo en un entorno de dev sin
    // sesión real de Mojang), y eso se ve como la textura "missing"
    // (el cuadro morado/negro).
    private final PlayerRenderer playerRenderer;

    public NecromanticChainRenderLayer(PlayerRenderer playerRenderer) {
        super(playerRenderer);
        this.playerRenderer = playerRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap == null || !cap.isActive()) {
            return;
        }

        ResourceLocation skinLocation = this.playerRenderer.getTextureLocation(player);
        RenderType renderType = RenderType.entityTranslucent(skinLocation);
        VertexConsumer buffer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();
        float alpha = TRANSPARENCY_ALPHA;
        getParentModel().renderToBuffer(poseStack, buffer, packedLight, LivingEntityRenderer.getOverlayCoords(player, 0), 1.0F, 1.0F, 1.0F, alpha);
        poseStack.popPose();
    }
}