package com.nanookmod.event;

import com.nanookmod.entity.SnowyBlizzEntity;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Spawner de superficie del Snowy Blizz. Toda la lógica genérica vive en
 * SurfaceSpawnHandler; acá solo queda la configuración propia del Blizz y su
 * regla de validación (reutilizada de SpawnPlacementHandler, la misma que
 * usaba/usa la vía de spawn natural).
 */
public class SnowyBlizzSurfaceSpawner extends SurfaceSpawnHandler<SnowyBlizzEntity> {

    public SnowyBlizzSurfaceSpawner() {
        super(
                ModEntities.SNOWY_BLIZZ.get(),
                ModBiomes.MAR_PRIMIGENIO_KEY,
                60,     // attemptIntervalTicks (2.5s)
                16,     // minRadius
                40,     // maxRadius
                15,      // maxNearby
                48.0D,  // populationCheckRadius
                false    // debugLog
        );
    }

    @Override
    protected boolean canSpawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        return SpawnPlacementHandler.canSnowyBlizzSpawn(entityType, level, MobSpawnType.NATURAL, pos, random);
    }
}