package com.nanookmod.client;

import com.nanookmod.NanookMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Sacudida de cámara genérica del mod (la dispara NanookScreenShakePacket).
 *
 * Diseño: un solo "pulso" activo por vez, con intensidad y duración. Si
 * llega un pulso nuevo mientras hay otro sonando, gana el más fuerte --
 * así una cadena de golpes (la onda del double_ground manda un pulso por
 * anillo) no se acumula hasta marear, pero el golpe grande igual pisa a
 * los chicos.
 *
 * El temblor decae de forma cuadrática hacia el final, que es lo que hace
 * que se lea como un impacto y no como un motor vibrando.
 *
 * Nota: convive sin problema con PermafrostClientHandler, que también se
 * engancha a ComputeCameraAngles -- Forge llama a todos los listeners y
 * los offsets se suman.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ScreenShakeHandler {

    /** Cuánto llega a moverse la cámara, en grados, a intensidad 1.0. */
    private static final float MAX_DEGREES = 6.0F;

    private static final Random RANDOM = new Random();

    private static float intensity = 0.0F;
    private static int ticksLeft = 0;
    private static int totalTicks = 0;

    /** La llama el paquete cuando el servidor avisa de un impacto. */
    public static void push(float newIntensity, int durationTicks) {
        if (newIntensity <= 0.0F || durationTicks <= 0) {
            return;
        }
        // El pulso más fuerte gana; uno más débil no corta al que ya suena.
        if (newIntensity >= intensity || ticksLeft <= 0) {
            intensity = Math.min(1.5F, newIntensity);
            ticksLeft = durationTicks;
            totalTicks = durationTicks;
        }
    }

    public static void clear() {
        intensity = 0.0F;
        ticksLeft = 0;
        totalTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) {
                intensity = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0 || intensity <= 0.0F || totalTicks <= 0) {
            return;
        }

        // Decaimiento cuadrático: fuerte al principio, se apaga rápido.
        float progress = ticksLeft / (float) totalTicks;
        float amount = intensity * progress * progress * MAX_DEGREES;

        float yaw = (RANDOM.nextFloat() - 0.5F) * 2.0F * amount;
        float pitch = (RANDOM.nextFloat() - 0.5F) * 2.0F * amount * 0.6F;
        float roll = (RANDOM.nextFloat() - 0.5F) * 2.0F * amount * 0.8F;

        event.setYaw(event.getYaw() + yaw);
        event.setPitch(Mth.clamp(event.getPitch() + pitch, -90.0F, 90.0F));
        event.setRoll(event.getRoll() + roll);
    }
}
