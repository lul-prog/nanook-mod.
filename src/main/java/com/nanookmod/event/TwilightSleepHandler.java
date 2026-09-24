package com.nanookmod.event;

import com.nanookmod.NanookMod;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mientras la Luna Crepuscular está activa, nadie puede dormir/saltar la
 * noche - el jugador va a ver el mensaje vanilla de "no es posible dormir
 * ahora" al intentarlo, igual que si fuera de día.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TwilightSleepHandler {

    @SubscribeEvent
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (TwilightNightHandler.isTwilightNight(event.getEntity().level())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }
}