package com.nanookmod.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Base reutilizable para spawn de SUPERFICIE. Calcula la altura real vía
 * heightmap (en vez de dejar que un sorteo random de Y termine casi siempre
 * en una cueva, como hacía NaturalSpawner por defecto).
 *
 * Para un mob nuevo: extender esta clase e implementar solo canSpawnAt().
 */
public abstract class SurfaceSpawnHandler<T extends Mob> extends AbstractDedicatedSpawner<T> {

    protected SurfaceSpawnHandler(EntityType<T> entityType, ResourceKey<Biome> biome,
                                  int attemptIntervalTicks, int minRadius, int maxRadius,
                                  int maxNearby, double populationCheckRadius, boolean debugLog) {
        super(entityType, biome, attemptIntervalTicks, minRadius, maxRadius, maxNearby, populationCheckRadius, debugLog);
    }

    @Override
    @Nullable
    protected BlockPos findValidCandidate(ServerLevel level, Player player, RandomSource random) {
        int offsetX = minRadius + random.nextInt(maxRadius - minRadius + 1);
        int offsetZ = minRadius + random.nextInt(maxRadius - minRadius + 1);
        if (random.nextBoolean()) offsetX = -offsetX;
        if (random.nextBoolean()) offsetZ = -offsetZ;

        int x = player.getBlockX() + offsetX;
        int z = player.getBlockZ() + offsetZ;

        if (!level.getBiome(new BlockPos(x, player.getBlockY(), z)).is(biome)) {
            return null; // el jugador no está cerca del bioma
        }

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos candidate = new BlockPos(x, y, z);

        return canSpawnAt(level, candidate, random) ? candidate : null;
    }

    @Override
    protected boolean isPopulationFull(ServerLevel level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(populationCheckRadius);
        long nearbyOnSurface = level.getEntities(entityType, checkArea, e -> level.canSeeSky(e.blockPosition())).size();
        return nearbyOnSurface >= maxNearby;
    }

    @Override
    protected String logTag() {
        return "surface_spawner";
    }
}