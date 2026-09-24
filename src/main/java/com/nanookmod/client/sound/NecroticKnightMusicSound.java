package com.nanookmod.client.sound;

import com.nanookmod.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Tema de batalla del NecroticKnight. No es posicional -- se escucha igual
 * de fuerte esté el jugador donde esté dentro del área de la pelea, como la
 * música del Wither/Ender Dragon, no como un sonido normal que se apaga con
 * la distancia -- y hace fade in al arrancar / fade out al terminar en vez
 * de cortarse seco. Quien la crea y la pide parar es
 * NecroticKnightMusicHandler; esta clase solo sabe subir/bajar su propio
 * volumen tick a tick.
 */
public class NecroticKnightMusicSound extends AbstractTickableSoundInstance {

    private static final int FADE_IN_TICKS = 40;   // 2s
    private static final int FADE_OUT_TICKS = 60;  // 3s
    private static final float TARGET_VOLUME = 0.7F;

    private int ticksExisted = 0;
    private boolean stopping = false;
    private int stopTicksElapsed = 0;
    private boolean finished = false;

    public NecroticKnightMusicSound() {
        super(ModSounds.NECROTIC_KNIGHT_THEME.get(), SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.attenuation = Attenuation.NONE; // no baja con la distancia, es "de pantalla completa"
        this.relative = true;                // no está atada a una posición del mundo
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    /** Empieza el fade out; isStopped() se pone en true solo cuando termina de apagarse. */
    public void requestStop() {
        this.stopping = true;
    }

    @Override
    public void tick() {
        ticksExisted++;

        if (stopping) {
            stopTicksElapsed++;
            float fadeOutProgress = Math.min(1.0F, (float) stopTicksElapsed / FADE_OUT_TICKS);
            this.volume = TARGET_VOLUME * (1.0F - fadeOutProgress);
            if (fadeOutProgress >= 1.0F) {
                finished = true;
            }
            return;
        }

        float fadeInProgress = Math.min(1.0F, (float) ticksExisted / FADE_IN_TICKS);
        this.volume = TARGET_VOLUME * fadeInProgress;
    }

    @Override
    public boolean isStopped() {
        return finished;
    }

    // CRÍTICO: sin esto, el motor de sonido descarta la instancia apenas
    // se llama a play() -- por defecto, cualquier sonido que arranque con
    // volumen 0 (como este, a propósito, para el fade in) se considera
    // "silencioso" y se tira sin siquiera empezar a tickear. Con esto en
    // true le decimos "sí, arranca en silencio a propósito, dejalo vivir".
    @Override
    public boolean canStartSilent() {
        return true;
    }
}