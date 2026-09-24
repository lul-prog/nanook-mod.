package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Desborde Necrótico -- lleva la cuenta de los filamentos activos de cada
 * knight (uno por vez que "brota" del cuerpo, con su propia posición,
 * fase de vaivén y momento de aparición). 100% cosmético y 100% cliente:
 * no hace falta que el servidor sepa nada de esto ni sincronizar nada --
 * cada cliente genera los suyos con el mismo criterio (arrancan/paran
 * según NecroticKnightEntity#getCorruptionLevel(), que SÍ está
 * sincronizado), así que aunque no sean pixel-perfect iguales entre
 * distintos jugadores mirando al mismo jefe, nadie lo va a notar.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NecroticOverflowTracker {

    /** Cuántos ticks vive cada filamento desde que aparece hasta que se disipa del todo. */
    static final int FILAMENT_LIFETIME_TICKS = 30; // 1.5s

    // Cada cuántos ticks aparece un filamento nuevo, según el nivel de
    // Corrupción actual (índices 0-2 en 0 -- Corrupción 0-II, todavía no
    // desbloqueado). Se pone más seguido con cada nivel.
    private static final int[] SPAWN_INTERVAL_TICKS = {0, 0, 0, 10, 7, 4, 2};
    private static final int MAX_CONCURRENT_PER_KNIGHT = 14;

    static final class Filament {
        final int spawnTick;
        final double offsetX;
        final double offsetZ;
        final double baseHeightFraction; // 0..1 de la altura del jefe, de dónde "brota"
        final double swayPhase;
        final double swaySpeedMul;

        Filament(int spawnTick, double offsetX, double offsetZ, double baseHeightFraction,
                 double swayPhase, double swaySpeedMul) {
            this.spawnTick = spawnTick;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.baseHeightFraction = baseHeightFraction;
            this.swayPhase = swayPhase;
            this.swaySpeedMul = swaySpeedMul;
        }
    }

    private static final Map<Integer, List<Filament>> filamentsByKnight = new HashMap<>();
    private static final Map<Integer, Integer> spawnAccumulatorByKnight = new HashMap<>();
    private static int clientTickCounter = 0;

    private NecroticOverflowTracker() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Mismo motivo que en NecroticWaveTracker: ClientTickEvent no se
        // frena con el juego en pausa, asi que sin esto los filamentos
        // seguian naciendo/moviendose con el menu de pausa abierto.
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        clientTickCounter++;
    }

    static int getClientTick() {
        return clientTickCounter;
    }

    /** Lista de filamentos activos ahora mismo para este jefe (puede estar vacía) -- también hace nacer/morir filamentos si corresponde. */
    static List<Filament> getActiveFilaments(NecroticKnightEntity knight) {
        // Desconectado a pedido -- el "Desborde Necrótico" (filamentos que
        // brotan del cuerpo del jefe a partir de Corrupción III) ya no se
        // dibuja. Para volver a activarlo, comentar el "return
        // Collections.emptyList()" de abajo y descomentar el bloque
        // original.
        return java.util.Collections.emptyList();

        /*
        int id = knight.getId();
        int level = Math.min(knight.getCorruptionLevel(), SPAWN_INTERVAL_TICKS.length - 1);

        List<Filament> list = filamentsByKnight.computeIfAbsent(id, k -> new ArrayList<>());

        if (level < 3) {
            list.clear();
            spawnAccumulatorByKnight.remove(id);
            return list;
        }

        list.removeIf(f -> clientTickCounter - f.spawnTick > FILAMENT_LIFETIME_TICKS);

        int spawnEveryTicks = SPAWN_INTERVAL_TICKS[level];
        int acc = spawnAccumulatorByKnight.merge(id, 1, Integer::sum);
        if (acc >= spawnEveryTicks) {
            spawnAccumulatorByKnight.put(id, 0);
            if (list.size() < MAX_CONCURRENT_PER_KNIGHT) {
                list.add(randomFilament(knight));
            }
        }

        return list;
        */
    }

    private static Filament randomFilament(NecroticKnightEntity knight) {
        RandomSource random = knight.getRandom();
        double halfWidth = knight.getBbWidth() * 0.5D;

        // Antes elegía un punto al azar DENTRO de un cuadrado (uniforme),
        // así que la mayoría de los filamentos terminaban cerca del eje
        // central -- dentro del cuerpo, no saliendo de él. Ahora elegimos
        // un ángulo y los ponemos justo en el borde de la silueta (o un
        // toque por fuera), como si brotaran de la superficie de verdad.
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = halfWidth * (0.95D + random.nextDouble() * 0.35D);
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;

        double baseHeightFraction = 0.1D + random.nextDouble() * 0.75D; // desde cerca del piso hasta casi la cabeza
        double swayPhase = random.nextDouble() * Math.PI * 2.0D;
        double swaySpeedMul = 0.8D + random.nextDouble() * 0.6D; // un poco de variedad entre filamentos

        return new Filament(clientTickCounter, offsetX, offsetZ, baseHeightFraction, swayPhase, swaySpeedMul);
    }
}