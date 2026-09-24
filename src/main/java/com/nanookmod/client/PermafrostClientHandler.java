package com.nanookmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanookmod.NanookMod;
import com.nanookmod.event.PermafrostDamageHandler;
import com.nanookmod.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PermafrostClientHandler {

    private static final int SHAKE_THRESHOLD = 1700;
    private static final int MAX_CHARGE = 2000;

    // Temblor de cámara
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.hasEffect(ModEffects.PERMAFROST.get())) {
            int charge = PermafrostDamageHandler.getPermafrostCharge(mc.player);

            if (charge > SHAKE_THRESHOLD) {
                float intensity = (float)(charge - SHAKE_THRESHOLD) / (MAX_CHARGE - SHAKE_THRESHOLD);
                float shakeAmount = intensity * 0.5F; // Intensidad base

                // Temblor de cámara simulando tiritar de frío (movimiento lateral de cabeza)
                float pitch = (float)((Math.random() - 0.5) * shakeAmount * 0.1F); // Arriba/abajo (casi imperceptible)
                float yaw   = (float)((Math.random() - 0.5) * shakeAmount);        // Izquierda/derecha (movimiento principal)
                float roll  = (float)((Math.random() - 0.5) * shakeAmount * 0.5F); // Inclinación de cabeza (medio)

                event.setPitch(event.getPitch() + pitch);
                event.setYaw(event.getYaw() + yaw);
                event.setRoll(event.getRoll() + roll);
            }
        }
    }
}