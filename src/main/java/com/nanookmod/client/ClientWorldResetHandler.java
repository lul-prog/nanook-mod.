package com.nanookmod.client;

import com.nanookmod.NanookMod;
import com.nanookmod.event.TwilightNightHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Complemento cliente de com.nanookmod.event.WorldResetHandler: apenas el
 * jugador se desconecta del mundo/servidor actual (salir al menú, cargar
 * otro mundo, caerse de un server), olvida el estado "cacheado" de la Luna
 * Crepuscular en el cliente.
 *
 * Sin esto, si invocás al NecroticKnight y salís del mundo antes de que
 * muera, clientTwilightActive queda en 'true' -> el próximo mundo que
 * cargues arranca con el cielo ya teñido de ese color, aunque ahí no exista
 * ningún NecroticKnight (bug reportado).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientWorldResetHandler {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TwilightNightHandler.resetClient();
    }
}