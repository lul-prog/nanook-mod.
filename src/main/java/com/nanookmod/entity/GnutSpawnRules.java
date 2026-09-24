package com.nanookmod.entity;

import com.nanookmod.NanookMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Regla de validación de spawn del Gnut. A diferencia de SpawnPlacementHandler
 * (Snowy Blizz), esta clase NO se registra en SpawnPlacementRegisterEvent - el
 * Gnut nunca pasa por NaturalSpawner, va directo por spawners dedicados desde
 * el principio (ver GnutSurfaceSpawner / GnutCaveSpawner).
 */
public final class GnutSpawnRules {

    // DIAGNÓSTICO: activar para ver en consola por qué se rechaza cada intento.
    private static final boolean DEBUG_LOG = false;

    private static final double MIN_SPACING = 12.0D;

    private GnutSpawnRules() {
    }

    public static boolean canSpawnAt(EntityType<GnutEntity> type, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        if (belowState.is(BlockTags.ICE)) {
            log(pos, "RECHAZADO: piso es hielo (" + belowState.getBlock() + ")");
            return false;
        }

        boolean standableSurface = belowState.is(Blocks.SNOW)
                || belowState.isFaceSturdy(level, belowPos, Direction.UP);
        if (!standableSurface) {
            log(pos, "RECHAZADO: superficie no válida (" + belowState.getBlock() + ")");
            return false;
        }

        if (!isPassable(level, pos) || !isPassable(level, pos.above())) {
            log(pos, "RECHAZADO: sin espacio libre (pos=" + level.getBlockState(pos).getBlock()
                    + ", pos+1=" + level.getBlockState(pos.above()).getBlock() + ")");
            return false;
        }

        AABB entityBox = type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(entityBox)) {
            log(pos, "RECHAZADO: hitbox real choca con el entorno");
            return false;
        }

        AABB nearbyArea = new AABB(pos).inflate(MIN_SPACING);
        int nearbyCount = level.getEntitiesOfClass(GnutEntity.class, nearbyArea).size();
        if (nearbyCount > 0) {
            log(pos, "RECHAZADO: ya hay " + nearbyCount + " cerca (spacing)");
            return false;
        }

        log(pos, "ACEPTADO");
        return true;
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static void log(BlockPos pos, String result) {
        if (DEBUG_LOG) {
            NanookMod.LOGGER.info("[gnut spawn_rule] pos={} -> {}", pos, result);
        }
    }
}