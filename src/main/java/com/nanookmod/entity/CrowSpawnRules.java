package com.nanookmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * Regla de validación de spawn del cuervo. Solo necesita aire libre en la
 * posición candidata (viene ya calculada a la altura de copa por
 * AirSpawnHandler) y que no haya demasiados cuervos ya cerca.
 */
public final class CrowSpawnRules {

    private static final double MIN_SPACING = 16.0D;

    private CrowSpawnRules() {
    }

    public static boolean canSpawnAt(EntityType<CrowEntity> type, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isPassable(level, pos) || !isPassable(level, pos.above())) {
            return false;
        }

        AABB entityBox = type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(entityBox)) {
            return false;
        }

        AABB nearbyArea = new AABB(pos).inflate(MIN_SPACING);
        return level.getEntitiesOfClass(CrowEntity.class, nearbyArea).isEmpty();
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
