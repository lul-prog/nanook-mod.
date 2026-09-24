package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DATA_ATTACK_STATE (el "qué ataque está haciendo ahora") SÍ está
 * sincronizado al cliente (NecroticKnightEntity#getAttackState()), pero
 * attackTicksElapsed (cuánto lleva ESE ataque puntual, tick a tick) es un
 * contador interno del servidor que nunca se sincroniza -- y es
 * justamente lo que NecroticWaveRenderer necesita para saber qué radio
 * dibujar en cada frame.
 *
 * En vez de agregar un campo sincronizado más (más tráfico de red, un
 * tick de latencia de por medio igual), el cliente simplemente cuenta
 * SUS PROPIOS ticks desde el momento en que detecta el cambio a
 * ATTACK_WAVE. El desfasaje contra el servidor real es como mucho el
 * ping del jugador -- imperceptible para un efecto puramente visual como
 * este (el daño real de la Wave lo sigue decidiendo el servidor con su
 * propio attackTicksElapsed, esto NUNCA se usa para hitbox/daño).
 *
 * v3 -- cada onda tiene una fase de DESVANECIDO después de terminar de
 * expandirse (FADE_TICKS) en vez de desaparecer de golpe: el radio se
 * queda fijo y el alpha baja de 1 a 0 (NecroticWaveRenderer multiplica
 * ese alpha en el degradado del anillo, Y usa (1 - alpha) como progreso
 * para dibujar la "evaporación" -- unas motas de geometría/shader que
 * se abren desde el anillo, ver NecroticWaveRenderer#drawEvaporation).
 * Esto vive TODO del lado del renderer ahora -- acá solo se calcula
 * radio + alpha por frame, sin spawnear ninguna Particle de Minecraft
 * (v2 lo hacía así y tenía un bug: el pulso salía de la lista un frame
 * antes de que la condición de "recién terminó" pudiera ser verdadera,
 * por eso nunca se veía nada).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NecroticWaveTracker {

    /** Cuánto tarda en desvanecerse del todo una vez que terminó de expandirse. */
    private static final int FADE_TICKS = 8;

    /** Radio + alpha de una onda en un frame dado -- lo que necesita el renderer para dibujarla. */
    public record PulseVisual(double radius, float alpha) {
    }

    private static final Map<Integer, Integer> waveStartClientTick = new HashMap<>();
    private static int clientTickCounter = 0;

    private NecroticWaveTracker() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // ClientTickEvent se sigue disparando con el juego en pausa (el
        // pause solo frena el tick del level/entidades), asi que sin este
        // chequeo el radio/alpha de la onda seguia calculandose con el
        // menu de pausa abierto -- al despausar, la animacion ya habia
        // "avanzado" de mas y el jugador se quedaba sin referencia visual
        // de cuando saltar.
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        clientTickCounter++;
    }

    /**
     * @return radio + alpha de CADA onda activa ahora mismo para este jefe
     * (puede haber varias al mismo tiempo, ver NecroticKnightEntity#WAVE_PULSES),
     * o lista vacía si no está en ATTACK_WAVE ahora mismo.
     */
    public static List<PulseVisual> getActivePulseVisuals(NecroticKnightEntity knight, float partialTick) {
        int id = knight.getId();

        if (knight.getAttackState() != NecroticKnightEntity.ATTACK_WAVE) {
            waveStartClientTick.remove(id);
            return Collections.emptyList();
        }

        int startTick = waveStartClientTick.computeIfAbsent(id, k -> clientTickCounter);
        float elapsed = (clientTickCounter - startTick) + partialTick;

        List<PulseVisual> visuals = new ArrayList<>();
        for (NecroticKnightEntity.WavePulseDef pulse : NecroticKnightEntity.WAVE_PULSES) {
            float sincePulse = elapsed - pulse.tick();
            float totalTicks = pulse.expansionTicks() + FADE_TICKS;
            if (sincePulse < 0 || sincePulse > totalTicks) {
                continue; // todavía no nació, o ya terminó de desvanecerse del todo
            }

            if (sincePulse <= pulse.expansionTicks()) {
                // Fase de expansión -- crece, todavía sin desvanecer.
                double radius = (sincePulse / pulse.expansionTicks()) * pulse.maxRadius();
                visuals.add(new PulseVisual(radius, 1.0F));
            } else {
                // Fase de desvanecido -- el radio se queda fijo en el
                // máximo y el alpha baja a 0 (antes, apenas se pasaba de
                // expansionTicks, esta onda directamente desaparecía).
                float fadeProgress = (sincePulse - pulse.expansionTicks()) / FADE_TICKS;
                float alpha = 1.0F - Mth.clamp(fadeProgress, 0F, 1F);
                visuals.add(new PulseVisual(pulse.maxRadius(), alpha));
            }
        }
        return visuals;
    }
}