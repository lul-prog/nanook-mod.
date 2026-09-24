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
 * Base reutilizable para spawn AÉREO (mobs voladores tipo el cuervo). A
 * diferencia de SurfaceSpawnHandler (que calcula la altura vía heightmap y
 * spawnea justo en el piso), esta busca un punto de aire libre entre la
 * altura del piso y minAltitudeAboveGround/maxAltitudeAboveGround por
 * encima, para que el mob aparezca ya volando a la altura de las copas en
 * vez de aparecer parado en el suelo.
 *
 * Para un mob nuevo: extender esta clase e implementar solo canSpawnAt().
 */
public abstract class AirSpawnHandler<T extends Mob> extends AbstractDedicatedSpawner<T> {

    protected final int minAltitudeAboveGround;
    protected final int maxAltitudeAboveGround;

    protected AirSpawnHandler(EntityType<T> entityType, ResourceKey<Biome> biome,
                               int attemptIntervalTicks, int minRadius, int maxRadius,
                               int minAltitudeAboveGround, int maxAltitudeAboveGround,
                               int maxNearby, double populationCheckRadius, boolean debugLog) {
        super(entityType, biome, attemptIntervalTicks, minRadius, maxRadius, maxNearby, populationCheckRadius, debugLog);
        this.minAltitudeAboveGround = minAltitudeAboveGround;
        this.maxAltitudeAboveGround = maxAltitudeAboveGround;
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

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int altitude = minAltitudeAboveGround + random.nextInt(maxAltitudeAboveGround - minAltitudeAboveGround + 1);
        int y = Math.min(groundY + altitude, level.getMaxBuildHeight() - 2);

        BlockPos candidate = new BlockPos(x, y, z);

        return canSpawnAt(level, candidate, random) ? candidate : null;
    }

    @Override
    protected boolean isPopulationFull(ServerLevel level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(populationCheckRadius);
        long nearby = level.getEntities(entityType, checkArea, e -> true).size();
        return nearby >= maxNearby;
    }

    @Override
    protected String logTag() {
        return "air_spawner";
    }
}
