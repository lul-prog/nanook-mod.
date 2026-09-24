package com.nanookmod.event;

import com.nanookmod.entity.SnowyBlizzEntity;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Spawner de cuevas del Snowy Blizz. Toda la lógica genérica de barrido vive
 * en CaveSpawnHandler; acá solo queda la configuración propia del Blizz.
 */
public class SnowyBlizzCaveSpawner extends CaveSpawnHandler<SnowyBlizzEntity> {

    public SnowyBlizzCaveSpawner() {
        super(
                ModEntities.SNOWY_BLIZZ.get(),
                ModBiomes.MAR_PRIMIGENIO_KEY,
                50,     // attemptIntervalTicks (2.5s)
                16,     // minRadius
                40,     // maxRadius
                25,      // maxNearby
                48.0D,  // populationCheckRadius
                false,   // debugLog
                5,      // columnAttempts
                4,      // yScanStep
                6       // minDepthBelowSurface
        );
    }

    @Override
    protected boolean canSpawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        return SpawnPlacementHandler.canSnowyBlizzSpawn(entityType, level, MobSpawnType.NATURAL, pos, random);
    }
}
