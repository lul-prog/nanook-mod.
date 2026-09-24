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
 * Base reutilizable para spawn en CUEVAS. Prueba varias columnas (x,z)
 * distintas cerca del jugador y, en cada una, hace un barrido sistemático de
 * Y (no un sorteo al azar) buscando una cámara real sin cielo visible - las
 * cuevas son una fracción chica del volumen de una montaña, así que un solo
 * intento al azar casi siempre cae en roca sólida.
 *
 * Para un mob nuevo: extender esta clase e implementar solo canSpawnAt().
 */
public abstract class CaveSpawnHandler<T extends Mob> extends AbstractDedicatedSpawner<T> {

    protected final int columnAttempts;
    protected final int yScanStep;
    protected final int minDepthBelowSurface;

    protected CaveSpawnHandler(EntityType<T> entityType, ResourceKey<Biome> biome,
                               int attemptIntervalTicks, int minRadius, int maxRadius,
                               int maxNearby, double populationCheckRadius, boolean debugLog,
                               int columnAttempts, int yScanStep, int minDepthBelowSurface) {
        super(entityType, biome, attemptIntervalTicks, minRadius, maxRadius, maxNearby, populationCheckRadius, debugLog);
        this.columnAttempts = columnAttempts;
        this.yScanStep = yScanStep;
        this.minDepthBelowSurface = minDepthBelowSurface;
    }

    @Override
    @Nullable
    protected BlockPos findValidCandidate(ServerLevel level, Player player, RandomSource random) {
        for (int col = 0; col < columnAttempts; col++) {
            int offsetX = minRadius + random.nextInt(maxRadius - minRadius + 1);
            int offsetZ = minRadius + random.nextInt(maxRadius - minRadius + 1);
            if (random.nextBoolean()) offsetX = -offsetX;
            if (random.nextBoolean()) offsetZ = -offsetZ;

            int x = player.getBlockX() + offsetX;
            int z = player.getBlockZ() + offsetZ;

            if (!level.getBiome(new BlockPos(x, player.getBlockY(), z)).is(biome)) {
                continue;
            }

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int maxY = surfaceY - minDepthBelowSurface;
            int minY = level.getMinBuildHeight() + 2;

            if (maxY <= minY) {
                continue;
            }

            int startOffset = random.nextInt(yScanStep);
            for (int y = maxY - startOffset; y >= minY; y -= yScanStep) {
                BlockPos candidate = new BlockPos(x, y, z);

                if (level.canSeeSky(candidate)) {
                    continue; // expuesto al cielo, no es "cueva"
                }

                if (canSpawnAt(level, candidate, random)) {
                    return candidate; // primera posición válida de verdad
                }
                // no era válida (sin piso, sin espacio, etc.) - seguimos bajando
            }
        }
        return null; // ninguna de las columnas probadas tenía una posición válida
    }

    @Override
    protected boolean isPopulationFull(ServerLevel level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(populationCheckRadius);
        long nearbyInCaves = level.getEntities(entityType, checkArea, e -> !level.canSeeSky(e.blockPosition())).size();
        return nearbyInCaves >= maxNearby;
    }

    @Override
    protected String logTag() {
        return "cave_spawner";
    }
}
