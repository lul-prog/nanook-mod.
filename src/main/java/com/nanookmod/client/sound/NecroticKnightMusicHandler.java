package com.nanookmod.client.sound;

import com.nanookmod.NanookMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Lado cliente: arranca/para el tema del NecroticKnight (con fade) cuando
 * llega SyncNecroticKnightMusicPacket, y mientras suena, calla la música
 * ambiente normal de Minecraft para que no se pisen -- el mismo problema
 * que vanilla resuelve a mano para la música del Wither/Ender Dragon.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, value = Dist.CLIENT)
public final class NecroticKnightMusicHandler {

    // IDs de entidad de los knights cuya música está activa ahora mismo.
    // Normalmente va a ser 0 o 1, pero soporta varios jefes vivos a la vez
    // por las dudas (con solo uno sonando, el más reciente).
    private static final Set<Integer> activeKnightIds = new HashSet<>();
    private static NecroticKnightMusicSound currentSound;

    private NecroticKnightMusicHandler() {
    }

    /** Llamado desde SyncNecroticKnightMusicPacket#handle. Solo el cliente debería llamar esto. */
    public static void setActive(int entityId, boolean active) {
        NanookMod.LOGGER.info("Nanook Mod (cliente): NecroticKnightMusicHandler.setActive(entityId={}, active={})", entityId, active);
        if (active) {
            activeKnightIds.add(entityId);
        } else {
            activeKnightIds.remove(entityId);
        }
        refresh();
    }

    private static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        boolean shouldPlay = !activeKnightIds.isEmpty();

        if (shouldPlay && currentSound == null) {
            NanookMod.LOGGER.info("Nanook Mod (cliente): arrancando NecroticKnightMusicSound (SoundEvent registrado = {})",
                    com.nanookmod.registry.ModSounds.NECROTIC_KNIGHT_THEME.isPresent());
            currentSound = new NecroticKnightMusicSound();
            mc.getSoundManager().play(currentSound);
        } else if (!shouldPlay && currentSound != null) {
            // Solo le pedimos el fade out -- ella sola se marca "stopped"
            // cuando termina de apagarse, el SoundManager la limpia solo.
            currentSound.requestStop();
            currentSound = null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            // Se desconectó / cambió de mundo con la música activa -- no la
            // dejamos sonando para siempre esperando un paquete que nunca
            // va a llegar.
            if (!activeKnightIds.isEmpty() || currentSound != null) {
                activeKnightIds.clear();
                currentSound = null;
            }
            return;
        }

        // Mientras nuestra música está activa, no dejamos que la música
        // ambiente de Minecraft se cuele encima -- la callamos todos los
        // ticks (stopPlaying() es un no-op barato si ya no hay nada
        // sonando). No tocamos nada del MusicManager fuera de la pelea.
        if (!activeKnightIds.isEmpty()) {
            mc.getMusicManager().stopPlaying();
        }
    }
}