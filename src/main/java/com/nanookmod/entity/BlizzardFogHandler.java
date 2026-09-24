package com.nanookmod.entity;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Niebla densa tipo "tormenta de nieve" mientras el jugador está dentro de
 * mar_primigenio. Se desvanece gradualmente en dos capas:
 *   1) Espacial: más densa mientras más lejos del borde del bioma está el jugador.
 *   2) Temporal: el valor mostrado persigue al valor objetivo poco a poco cada
 *      frame, en vez de saltar de golpe cuando el jugador cruza el borde o
 *      cuando cambia el bloque muestreado. Esto es lo que da el efecto de
 *      "desvanecido" en vez de un cambio brusco.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlizzardFogHandler {

    // Qué tan cerca empieza/termina la niebla en el corazón del bioma. Más bajo = tormenta más cerrada.
    private static final float NEAR_PLANE = 0.0f;
    private static final float FAR_PLANE = 90.0f;

    // Distancia (en bloques) sobre la que se difumina el efecto cerca del borde del bioma.
    private static final int TRANSITION_RADIUS = 17;

    // Cuántos bloques salta cada muestreo al buscar el borde. 1 = el más suave posible.
    private static final int SAMPLE_STEP = 1;

    // Qué tan rápido el valor mostrado "persigue" al valor objetivo, por frame (0-1).
    // Más bajo = desvanecido más lento/perceptible. Más alto = reacciona casi al instante.
    private static final float FADE_SPEED = 0.05f;

    // Valor que se está mostrando ahora mismo (se actualiza cada frame acercándose al objetivo).
    private static float displayedFactor = 0f;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float t = updateAndGetDisplayedFactor();
        if (t <= 0f) {
            return; // fuera del bioma y ya totalmente desvanecido: no tocar nada
        }

        float newNear = lerp(event.getNearPlaneDistance(), NEAR_PLANE, t);
        float newFar = lerp(event.getFarPlaneDistance(), FAR_PLANE, t);

        event.setNearPlaneDistance(newNear);
        event.setFarPlaneDistance(newFar);
        event.setCanceled(true); // necesario para que los valores de arriba tengan efecto
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float t = displayedFactor; // ya se actualizó en onRenderFog, que se dispara antes en el mismo frame
        if (t <= 0f) {
            return;
        }

        event.setRed(lerp(event.getRed(), 0.92f, t));
        event.setGreen(lerp(event.getGreen(), 0.94f, t));
        event.setBlue(lerp(event.getBlue(), 1.0f, t));
    }

    /**
     * Actualiza displayedFactor acercándolo un paso hacia el valor "objetivo" espacial
     * (blizzardFactor) y devuelve el nuevo valor mostrado. Esto evita saltos bruscos
     * tanto al cruzar el borde del bioma como al recalcular la distancia.
     */
    private static float updateAndGetDisplayedFactor() {
        float target = blizzardFactor();
        displayedFactor = lerp(displayedFactor, target, FADE_SPEED);

        // Evita que se quede infinitamente acercándose sin llegar nunca (ej. 0.0004 residual).
        if (Math.abs(displayedFactor - target) < 0.001f) {
            displayedFactor = target;
        }
        return displayedFactor;
    }

    /**
     * 0 = fuera del bioma o justo en el borde (niebla normal).
     * 1 = bien adentro del bioma, a TRANSITION_RADIUS bloques o más del borde (tormenta completa).
     */
    private static float blizzardFactor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0f;
        }

        BlockPos pos = mc.player.blockPosition();
        if (!mc.level.getBiome(pos).is(ModBiomes.MAR_PRIMIGENIO_KEY)) {
            return 0f;
        }

        int distance = distanceToBorder(mc.level, pos.getX(), pos.getZ());
        return clamp(distance / (float) TRANSITION_RADIUS, 0f, 1f);
    }

    /**
     * Distancia mínima (hasta TRANSITION_RADIUS) hacia un punto donde el bioma
     * ya no es el nuestro, buscando en las 4 direcciones cardinales, bloque a
     * bloque (SAMPLE_STEP = 1) para que el valor cambie suavemente al caminar.
     */
    private static int distanceToBorder(ClientLevel level, int x, int z) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int minDist = TRANSITION_RADIUS;
        int y = level.getSeaLevel();

        for (int[] dir : dirs) {
            for (int d = SAMPLE_STEP; d <= TRANSITION_RADIUS; d += SAMPLE_STEP) {
                BlockPos checkPos = new BlockPos(x + dir[0] * d, y, z + dir[1] * d);
                if (!level.getBiome(checkPos).is(ModBiomes.MAR_PRIMIGENIO_KEY)) {
                    minDist = Math.min(minDist, d - SAMPLE_STEP);
                    break;
                }
            }
        }

        return minDist;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
