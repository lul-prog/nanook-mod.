package com.nanookmod.registry;

import com.nanookmod.event.CrowSpawner;
import com.nanookmod.event.GnutSurfaceSpawner;
import com.nanookmod.event.SnowyBlizzCaveSpawner;
import com.nanookmod.event.SnowyBlizzSurfaceSpawner;
import net.minecraftforge.common.MinecraftForge;

/**
 * Registro centralizado de spawners dedicados (los que NO dependen de
 * NaturalSpawner). A diferencia de ModBlocks/ModItems/etc. esto no usa
 * DeferredRegister -  son instancias normales que se suscriben al bus de
 * eventos de Forge, porque cada una necesita su propio estado (throttle de
 * ticks, configuración propia).
 *
 * Para agregar un mob nuevo con este patrón: crear su spawner (extendiendo
 * SurfaceSpawnHandler y/o CaveSpawnHandler) y sumar una línea acá.
 *
 * Llamar a register() una sola vez desde el constructor de NanookMod.
 */
public class ModSpawnHandlers {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SnowyBlizzSurfaceSpawner());
        MinecraftForge.EVENT_BUS.register(new SnowyBlizzCaveSpawner());
        MinecraftForge.EVENT_BUS.register(new GnutSurfaceSpawner());
        MinecraftForge.EVENT_BUS.register(new CrowSpawner(ModBiomes.BOSQUE_CREPUSCULAR_KEY));
        MinecraftForge.EVENT_BUS.register(new CrowSpawner(ModBiomes.BOSQUE_CREPUSCULAR_RIVER_KEY));


        // Próximos mobs van acá, por ejemplo:
        // MinecraftForge.EVENT_BUS.register(new CabraAtacanteSurfaceSpawner());
    }
}