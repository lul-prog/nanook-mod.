package com.nanookmod.event;

import com.nanookmod.NanookMod;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Resetea los flags "static" de efectos de mundo (por ahora, la Luna
 * Crepuscular del NecroticKnight) cada vez que el servidor -integrado, en
 * singleplayer, o dedicado- se apaga. Esto pasa cada vez que el jugador
 * sale del mundo actual, sea al menú principal o para cargar otro mundo
 * distinto.
 *
 * Por qué hace falta: TwilightNightHandler enciende/apaga su flag SOLO
 * cuando el NecroticKnight aparece o muere. Si el jugador sale del mundo
 * con el jefe todavía vivo, ese aviso de "apagado" nunca llega, y como el
 * flag es static, sobrevive al cambio de mundo dentro del mismo proceso
 * del juego -> el mundo siguiente arranca con el cielo ya teñido sin
 * ningún jefe de por medio. ServerStoppingEvent es el momento correcto
 * para "olvidar" ese estado, sin importar por qué se salió del mundo.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldResetHandler {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TwilightNightHandler.resetServer();
    }
}