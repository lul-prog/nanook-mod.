package com.nanookmod.client.sound;

import com.nanookmod.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Tema de batalla de Nanook ("Throne of Permafrost"). Mismo diseño que
 * NecroticKnightMusicSound: no es posicional (se oye igual esté el jugador
 * donde esté dentro de la pelea), va por el canal MUSIC (así respeta el
 * control de volumen de música del jugador) y hace fade in al arrancar y fade
 * out al terminar en vez de cortarse seco. Quien la crea y la pide parar es
 * NanookMusicHandler; esta clase solo sabe subir y bajar su propio volumen.
 */
public class NanookMusicSound extends AbstractTickableSoundInstance {

    private static final int FADE_IN_TICKS = 60;   // 3s
    private static final int FADE_OUT_TICKS = 80;  // 4s

    /**
     * Volumen objetivo. El tema del Knight usa 0.7. Esta canción está ~2.8 dB más
     * baja en promedio (-14.3 dB medio contra -11.5 dB) y los dos tocan 0 dB de
     * pico, así que no se le puede subir la ganancia al archivo sin recortarla.
     * Se compensa acá: 0.7 * 10^(2.8/20) = 0.97. Se deja en 0.95.
     */
    private static final float TARGET_VOLUME = 0.95F;

    private int ticksExisted = 0;
    private boolean stopping = false;
    private int stopTicksElapsed = 0;
    private boolean finished = false;

    public NanookMusicSound() {
        super(ModSounds.NANOOK_THEME.get(), SoundSource.MUSIC, RandomSource.create());
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

    // CRÍTICO (igual que en el tema del Knight): sin esto el motor de sonido descarta
    // la instancia apenas se llama a play(), porque arranca con volumen 0 a
    // propósito (para el fade in) y por defecto lo "silencioso" se tira sin
    // siquiera empezar a tickear.
    @Override
    public boolean canStartSilent() {
        return true;
    }
}
