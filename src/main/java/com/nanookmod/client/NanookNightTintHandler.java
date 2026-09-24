package com.nanookmod.client;

import com.nanookmod.event.FrostedNightHandler;
import com.nanookmod.event.TwilightNightHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Decide qué color (si alguno) hay que imponerle AHORA MISMO al lightmap
 * ambiental del juego (ver MixinLightTexture, que es quien llama a
 * {@link #getActiveTint()} en cada frame que Minecraft recalcula el
 * lightmap). Reemplaza al viejo FrostedNightScreenTint (overlay de
 * pantalla con blend "multiplicar"): en vez de dibujar un rectángulo
 * encima de todo, esto tiñe el MAPA DE LUZ real, igual que hace Confluence
 * con su Luna de Sangre - así el resultado se ve como "el mundo entero
 * cambió de iluminación" y no como "hay un cristal de color delante de la
 * cámara".
 *
 * Misma prioridad que ya usan MixinFrostedMoon y MixinFrostedSky:
 * Crepuscular > Escarchada, así no hay que recordar mantener el orden
 * sincronizado en cuatro archivos distintos.
 */
public final class NanookNightTintHandler {

    // Mismo azul hielo que ya tenías ajustado en FrostedNightScreenTint -
    // se reutiliza tal cual para no perder ese trabajo de ajuste fino.
    // Si preferís que la Escarchada use el tono del CIELO en vez de este
    // (0.62, 0.78, 0.92, ver MixinFrostedSky#FROSTED_SKY_COLOR), cambiá
    // este vector por ese y listo.
    private static final Vector3f FROSTED_TINT = new Vector3f(0.30F, 0.55F, 0.85F);

    // Mismo tono que usa el cielo en la Noche Crepuscular
    // (MixinFrostedSky#TWILIGHT_SKY_COLOR = 0.32, 0.55, 0.42) - así el
    // mundo entero y el cielo quedan en la misma familia de color, tal
    // como pediste.
    private static final Vector3f TWILIGHT_TINT = new Vector3f(0.32F, 0.55F, 0.42F);

    private NanookNightTintHandler() {
    }

    /**
     * @return el color a imponer sobre el lightmap ambiental, o null si
     * ninguna noche especial está activa (en cuyo caso MixinLightTexture no
     * toca nada y el juego se comporta 100% vanilla).
     */
    @Nullable
    public static Vector3f getActiveTint() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        if (TwilightNightHandler.isTwilightNight(level)) {
            return TWILIGHT_TINT;
        }
        if (FrostedNightHandler.isFrostedNight(level)) {
            return FROSTED_TINT;
        }
        return null;
    }

    /**
     * Qué tan fuerte aplicar el color de {@link #getActiveTint()}, de 0
     * (nada, el lightmap queda 100% vainilla) a 1 (el tinte de lleno).
     *
     * Para la Escarchada esto siempre es 1.0 -- nunca se pidió que esa
     * fuera progresiva, sigue siendo el corte binario de siempre.
     *
     * Para la Crepuscular usa {@link TwilightNightHandler#getTransitionProgress}:
     * es la pieza que hace que el mundo se vaya oscureciendo DE A POCO en
     * vez de teñirse de un salto en el mismo tick en que arranca el
     * ritual. Ver MixinLightTexture para cómo se usa este valor.
     */
    public static float getActiveTintBlend() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return 0.0F;
        }
        if (TwilightNightHandler.isTwilightNight(level)) {
            return TwilightNightHandler.getTransitionProgress(level);
        }
        return 1.0F; // Escarchada, o ninguna (getActiveTint() ya dio null en ese caso)
    }
}
