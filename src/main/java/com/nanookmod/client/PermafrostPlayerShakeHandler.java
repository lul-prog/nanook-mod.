package com.nanookmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanookmod.NanookMod;
import com.nanookmod.event.PermafrostDamageHandler;
import com.nanookmod.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PermafrostPlayerShakeHandler {

    private static final int SHAKE_THRESHOLD = 1700;
    private static final int MAX_CHARGE = 2000;

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = event.getEntity();

        // Solo aplicar al jugador local (no a otros jugadores)
        if (player != mc.player) return;

        if (player.hasEffect(ModEffects.PERMAFROST.get())) {
            int charge = PermafrostDamageHandler.getPermafrostCharge(player);

            if (charge > SHAKE_THRESHOLD) {
                // Calcular intensidad del temblor
                float intensity = (float)(charge - SHAKE_THRESHOLD) / (MAX_CHARGE - SHAKE_THRESHOLD);
                float shakeAmount = intensity * 0.08F; // Ligeramente aumentado para los lados

                // Aplicar temblor al modelo del jugador
                PoseStack poseStack = event.getPoseStack();

                // Temblor principalmente horizontal (como tiritar de frío)
                double shakeX = (Math.random() - 0.5) * shakeAmount;        // Lado a lado (normal)
                double shakeY = (Math.random() - 0.5) * shakeAmount * 0.1F; // Vertical (casi nada)
                double shakeZ = (Math.random() - 0.5) * shakeAmount;        // Adelante-atrás (normal)

                poseStack.translate(shakeX, shakeY, shakeZ);
            }
        }
    }
}
