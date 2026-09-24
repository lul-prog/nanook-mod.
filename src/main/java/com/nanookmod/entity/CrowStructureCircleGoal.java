package com.nanookmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Prioridad más alta de las tres de comportamiento social: si hay un
 * bloque con el tag nanookmod:crow_circle_structures dentro del radio de
 * búsqueda, el cuervo da vueltas alrededor en vez de hacer su
 * comportamiento ambiente o vigilar al jugador. El tag arranca vacío -
 * listo para cuando las estructuras pendientes entren al mod, sin tocar
 * este archivo. Cada cuervo tiene su propio radio/altura/velocidad/
 * dirección (fijados al azar en el constructor) para que varios cuervos
 * en la misma estructura no se vean sincronizados.
 */
public class CrowStructureCircleGoal extends Goal {

    public static final TagKey<Block> CIRCLE_STRUCTURES_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    new net.minecraft.resources.ResourceLocation("nanookmod", "crow_circle_structures"));

    private static final int SEARCH_RADIUS_H = 16;
    private static final int SEARCH_RADIUS_V = 6;
    private static final int RESCAN_COOLDOWN_TICKS = 60;
    private static final int RECHECK_VALID_TICKS = 100;

    private final CrowEntity crow;

    private final double circleRadius;
    private final double heightOffset;
    private final double angularSpeed;
    private final double flySpeed;

    private BlockPos structurePos;
    private double angle;
    private int rescanCooldown = 0;
    private int recheckCooldown = 0;

    public CrowStructureCircleGoal(CrowEntity crow) {
        this.crow = crow;
        this.setFlags(EnumSet.of(Flag.MOVE));

        var random = crow.getRandom();
        this.circleRadius = 5.0D + random.nextDouble() * 3.0D; // 5-8
        this.heightOffset = 3.0D + random.nextDouble() * 3.0D; // 3-6
        int dir = random.nextBoolean() ? 1 : -1;
        this.angularSpeed = (0.025D + random.nextDouble() * 0.02D) * dir;
        this.flySpeed = 1.5D + random.nextDouble() * 0.5D; // 1.5-2.0
    }

    @Override
    public boolean canUse() {
        if (this.rescanCooldown > 0) {
            this.rescanCooldown--;
            return false;
        }
        this.rescanCooldown = RESCAN_COOLDOWN_TICKS;

        this.structurePos = findNearbyStructure();
        return this.structurePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.structurePos == null) {
            return false;
        }

        this.recheckCooldown--;
        if (this.recheckCooldown <= 0) {
            this.recheckCooldown = RECHECK_VALID_TICKS;
            BlockState state = this.crow.level().getBlockState(this.structurePos);
            if (!state.is(CIRCLE_STRUCTURES_TAG)) {
                return false;
            }
        }

        return this.crow.blockPosition().distSqr(this.structurePos) <= (double) (SEARCH_RADIUS_H * 2) * (SEARCH_RADIUS_H * 2);
    }

    @Override
    public void start() {
        this.angle = this.crow.getRandom().nextDouble() * Math.PI * 2.0;
        this.recheckCooldown = RECHECK_VALID_TICKS;
        this.crow.setBehavior(CrowEntity.Behavior.STRUCTURE_CIRCLE);
    }

    @Override
    public void stop() {
        this.structurePos = null;
        this.crow.setBehavior(CrowEntity.Behavior.AMBIENT);
    }

    @Override
    public void tick() {
        if (this.structurePos == null) {
            return;
        }

        this.angle += this.angularSpeed;

        double centerX = this.structurePos.getX() + 0.5D;
        double centerZ = this.structurePos.getZ() + 0.5D;
        double x = centerX + Math.cos(this.angle) * this.circleRadius;
        double z = centerZ + Math.sin(this.angle) * this.circleRadius;
        double y = this.structurePos.getY() + this.heightOffset;

        this.crow.flyTowards(new Vec3(x, y, z), this.flySpeed);
    }

    private BlockPos findNearbyStructure() {
        Level level = this.crow.level();
        BlockPos origin = this.crow.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS_H, -SEARCH_RADIUS_V, -SEARCH_RADIUS_H),
                origin.offset(SEARCH_RADIUS_H, SEARCH_RADIUS_V, SEARCH_RADIUS_H))) {
            if (level.getBlockState(pos).is(CIRCLE_STRUCTURES_TAG)) {
                return pos.immutable();
            }
        }
        return null;
    }
}
