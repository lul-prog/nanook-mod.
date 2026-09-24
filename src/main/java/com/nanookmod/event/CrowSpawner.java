package com.nanookmod.event;

import com.nanookmod.entity.CrowEntity;
import com.nanookmod.entity.CrowSpawnRules;
import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

/**
 * Spawner aéreo del cuervo. Se instancia una vez por bioma (bosque
 * crepuscular normal y su variante de río) en ModSpawnHandlers, pasando la
 * ResourceKey correspondiente - no hace falta una clase por bioma.
 */
public class CrowSpawner extends AirSpawnHandler<CrowEntity> {

    public CrowSpawner(ResourceKey<Biome> biome) {
        super(
                ModEntities.CROW.get(),
                biome,
                100,    // attemptIntervalTicks (5s)
                20,     // minRadius
                48,     // maxRadius
                4,      // minAltitudeAboveGround
                14,     // maxAltitudeAboveGround
                6,      // maxNearby
                40.0D,  // populationCheckRadius
                false   // debugLog
        );
    }

    @Override
    protected boolean canSpawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        return CrowSpawnRules.canSpawnAt(entityType, level, pos, random);
    }
}
