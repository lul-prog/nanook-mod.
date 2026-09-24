package com.nanookmod.client.sound;

import com.nanookmod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Sonido en loop del rayo del NecroticKnight, mientras la entidad del rayo
 * (NecroticLightningBeamEntity) está viva -- posicional de verdad: cada
 * tick sigue las coordenadas reales del rayo (que a su vez sigue la punta
 * de la espada del jefe, ver NecroticLightningBeamEntity#followOwner), así
 * que se mueve con él en vez de quedarse pegado a donde nació.
 *
 * Arranca a volumen completo apenas se crea (el windup ya avisó que esto
 * viene), pero SIEMPRE hace fade out al pedirle requestStop() en vez de
 * cortarse seco -- pedido explícito, para que no quede la sensación de que
 * el sonido "seguía sonando solo" después de que el rayo ya desapareció.
 * Quien decide CUÁNDO pedir el stop es NecroticLightningBeamSoundHandler,
 * a partir de SyncLightningBeamSoundPacket(active=false), que el servidor
 * manda en el mismo tick en que la entidad del rayo se descarta de verdad
 * (no cuando termina la animación del jefe, que sigue un rato más).
 */
public class NecroticLightningBeamSound extends AbstractTickableSoundInstance {

    private static final int FADE_OUT_TICKS = 10; // 0.5s

    private final int beamEntityId;
    private boolean stopping = false;
    private int stopTicksElapsed = 0;
    private boolean finished = false;

    public NecroticLightningBeamSound(int beamEntityId, double x, double y, double z) {
        super(ModSounds.NECROKNIGHT_LIGHTNING_BEAM.get(), SoundSource.HOSTILE, RandomSource.create());
        this.beamEntityId = beamEntityId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Empieza el fade out; isStopped() se pone en true solo cuando termina de apagarse. */
    public void requestStop() {
        this.stopping = true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Entity beam = mc.level != null ? mc.level.getEntity(beamEntityId) : null;
        if (beam != null) {
            // Seguimos la posición real de la entidad del rayo tick a tick
            // -- si se movió con el jefe, el sonido se mueve con ella.
            this.x = beam.getX();
            this.y = beam.getY();
            this.z = beam.getZ();
        }

        if (stopping) {
            stopTicksElapsed++;
            float fadeOutProgress = Math.min(1.0F, (float) stopTicksElapsed / FADE_OUT_TICKS);
            this.volume = 1.0F - fadeOutProgress;
            if (fadeOutProgress >= 1.0F) {
                finished = true;
            }
        } else if (beam == null) {
            // Red de seguridad: la entidad ya no existe en el cliente y
            // nadie pidió el stop explícito (paquete perdido, desconexión a
            // medio camino, etc.) -- no la dejamos sonando en loop para
            // siempre.
            finished = true;
        }
    }

    @Override
    public boolean isStopped() {
        return finished;
    }
}