package com.nanookmod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Lado cliente: arranca/para (con fade) el sonido en loop del rayo cuando
 * llega SyncLightningBeamSoundPacket. Una entrada por id de entidad de
 * rayo -- soporta varios rayos sonando a la vez (varios jefes/mirror
 * clones) sin pisarse entre ellos.
 */
public final class NecroticLightningBeamSoundHandler {

    private static final Map<Integer, NecroticLightningBeamSound> activeSounds = new HashMap<>();

    private NecroticLightningBeamSoundHandler() {
    }

    /** Llamado desde SyncLightningBeamSoundPacket#handle. Solo el cliente debería llamar esto. */
    public static void setActive(int beamEntityId, boolean active) {
        Minecraft mc = Minecraft.getInstance();

        if (active) {
            if (activeSounds.containsKey(beamEntityId) || mc.level == null) {
                return;
            }
            Entity beam = mc.level.getEntity(beamEntityId);
            double x = beam != null ? beam.getX() : 0.0D;
            double y = beam != null ? beam.getY() : 0.0D;
            double z = beam != null ? beam.getZ() : 0.0D;
            NecroticLightningBeamSound sound = new NecroticLightningBeamSound(beamEntityId, x, y, z);
            activeSounds.put(beamEntityId, sound);
            mc.getSoundManager().play(sound);
        } else {
            NecroticLightningBeamSound sound = activeSounds.remove(beamEntityId);
            if (sound != null) {
                // Solo le pedimos el fade out -- ella sola se marca
                // "stopped" cuando termina de apagarse, el SoundManager la
                // limpia sola.
                sound.requestStop();
            }
        }
    }
}