package com.nanookmod.event;

import com.nanookmod.entity.GnutEntity;
import com.nanookmod.entity.GnutSpawnRules;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public class GnutSurfaceSpawner extends SurfaceSpawnHandler<GnutEntity> {

    // "manadas de 3" - el primer miembro va exactamente en el punto ya validado por
    // findValidCandidate; los otros dos intentan un punto cercano (±2 bloques) y se
    // validan de nuevo ahí mismo. Si el terreno no da para los 3, spawnea los que entren
    // (no se fuerza) - para un mob no-jefe no vale la pena complicar más esto.
    private static final int PACK_SIZE = 3;
    private static final int PACK_SPREAD = 2;

    public GnutSurfaceSpawner() {
        super(
                ModEntities.GNUT.get(),
                ModBiomes.MAR_PRIMIGENIO_KEY,
                30,     // attemptIntervalTicks (1.5s) - más frecuente que el Blizz (2.5s), es el mob pasivo del bioma
                16,     // minRadius
                40,     // maxRadius
                30,      // maxNearby (cuenta individuos, no manadas) - más alto que el Blizz (20)
                48.0D,  // populationCheckRadius
                false    // debugLog
        );
    }

    @Override
    protected boolean canSpawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        return GnutSpawnRules.canSpawnAt(entityType, level, pos, random);
    }

    @Override
    protected void spawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        int spawned = 0;

        if (createAndSpawnOne(level, pos, random) != null) {
            spawned++;
        }

        for (int i = 1; i < PACK_SIZE; i++) {
            int offsetX = random.nextInt(PACK_SPREAD * 2 + 1) - PACK_SPREAD;
            int offsetZ = random.nextInt(PACK_SPREAD * 2 + 1) - PACK_SPREAD;
            BlockPos memberPos = pos.offset(offsetX, 0, offsetZ);

            if (canSpawnAt(level, memberPos, random) && createAndSpawnOne(level, memberPos, random) != null) {
                spawned++;
            }
        }

        debugLog(pos, "SPAWNEADO manada de " + spawned + "/" + PACK_SIZE);
    }
}