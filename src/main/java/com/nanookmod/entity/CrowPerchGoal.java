package com.nanookmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Comportamiento ambiental por defecto (la prioridad más baja: solo corre
 * si ninguno de los otros goals está activo). El cuervo deambula
 * volando de un punto cercano a otro sin parar; de vez en cuando el punto
 * elegido resulta estar sobre una superficie sólida y ahí se posa a
 * descansar un rato (animación idle). Si no encuentra superficie sólida
 * cerca (por ejemplo, recién spawneado sobre el dosel del bosque), sigue
 * flotando/deambulando en el aire en vez de quedarse congelado - ese era
 * el bug: antes, si no había un posadero válido, el cuervo se quedaba sin
 * objetivo para siempre y se veía "sentado" en el aire.
 */
public class CrowPerchGoal extends Goal {

    private static final int SEARCH_RADIUS_H = 12;
    private static final int SEARCH_RADIUS_V = 6;
    private static final double ARRIVAL_DISTANCE_SQR = 1.0D;

    // Probabilidad de que, al elegir nuevo destino, sea un intento real de
    // posarse (busca superficie sólida) en vez de solo un punto de aire.
    private static final float PERCH_ATTEMPT_CHANCE = 0.35F;

    // Pausa breve al llegar a un punto de aire (no es descanso real, solo
    // una pausa antes de elegir el siguiente punto, para que no sea un
    // vuelo 100% frenético).
    private static final int AIR_PAUSE_MIN_TICKS = 20;
    private static final int AIR_PAUSE_MAX_TICKS = 60;

    private static final int REST_MIN_TICKS = 100;
    private static final int REST_MAX_TICKS = 300;

    private final CrowEntity crow;
    private final double flySpeed;

    private Vec3 target;
    private boolean targetIsPerch;
    private boolean waiting = false;
    private int waitTicksLeft = 0;

    public CrowPerchGoal(CrowEntity crow) {
        this.crow = crow;
        this.flySpeed = 1.5D + crow.getRandom().nextDouble() * 0.7D; // 1.5-2.2
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        this.crow.setBehavior(CrowEntity.Behavior.AMBIENT);
        this.waiting = false;
        this.target = null;
    }

    @Override
    public void stop() {
        this.waiting = false;
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.waiting) {
            this.waitTicksLeft--;
            if (this.waitTicksLeft <= 0) {
                this.waiting = false;
                this.target = null;
            }
            return;
        }

        if (this.target == null) {
            boolean tryPerch = this.crow.getRandom().nextFloat() < PERCH_ATTEMPT_CHANCE;
            this.target = tryPerch ? findPerch() : findAirWaypoint();
            this.targetIsPerch = tryPerch && this.target != null;

            if (this.target == null) {
                // Ni siquiera un punto de aire válido este intento (raro,
                // pero puede pasar si está rodeado de bloques): reintenta
                // el próximo tick en vez de quedarse quieto para siempre.
                this.target = findAirWaypointFallback();
            }
            if (this.target == null) {
                return;
            }
        }

        this.crow.flyTowards(this.target, this.flySpeed);

        if (this.crow.position().distanceToSqr(this.target) <= ARRIVAL_DISTANCE_SQR) {
            this.waiting = true;
            if (this.targetIsPerch) {
                this.waitTicksLeft = REST_MIN_TICKS + this.crow.getRandom().nextInt(REST_MAX_TICKS - REST_MIN_TICKS);
            } else {
                this.waitTicksLeft = AIR_PAUSE_MIN_TICKS + this.crow.getRandom().nextInt(AIR_PAUSE_MAX_TICKS - AIR_PAUSE_MIN_TICKS);
            }
            this.target = null;
        }
    }

    /** Punto de aire libre cerca del cuervo, sin requerir superficie sólida debajo - siempre debería encontrar algo. */
    private Vec3 findAirWaypoint() {
        Level level = this.crow.level();
        BlockPos origin = this.crow.blockPosition();

        for (int i = 0; i < 10; i++) {
            double angle = this.crow.getRandom().nextDouble() * Math.PI * 2.0;
            int dist = 5 + this.crow.getRandom().nextInt(SEARCH_RADIUS_H - 4);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int dy = this.crow.getRandom().nextInt(SEARCH_RADIUS_V * 2 + 1) - SEARCH_RADIUS_V;
            int y = Mth.clamp(origin.getY() + dy, level.getMinBuildHeight() + 3, level.getMaxBuildHeight() - 3);

            BlockPos candidate = new BlockPos(x, y, z);
            if (level.getBlockState(candidate).isAir()) {
                return Vec3.atCenterOf(candidate);
            }
        }
        return null;
    }

    /** Último recurso: un punto muy cerca, casi siempre válido. */
    private Vec3 findAirWaypointFallback() {
        Level level = this.crow.level();
        BlockPos origin = this.crow.blockPosition();
        for (Direction dir : Direction.values()) {
            BlockPos candidate = origin.relative(dir, 3);
            if (level.getBlockState(candidate).isAir()) {
                return Vec3.atCenterOf(candidate);
            }
        }
        return this.crow.position(); // no debería pasar nunca, pero evita null
    }

    /** Busca un punto encima de un bloque sólido cercano donde posarse de verdad. */
    private Vec3 findPerch() {
        Level level = this.crow.level();
        BlockPos origin = this.crow.blockPosition();

        for (int i = 0; i < 8; i++) {
            double angle = this.crow.getRandom().nextDouble() * Math.PI * 2.0;
            int dist = 4 + this.crow.getRandom().nextInt(SEARCH_RADIUS_H - 3);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int dy = this.crow.getRandom().nextInt(SEARCH_RADIUS_V * 2 + 1) - SEARCH_RADIUS_V;
            int y = Mth.clamp(origin.getY() + dy, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 2);

            BlockPos candidate = new BlockPos(x, y, z);
            BlockPos below = candidate.below();

            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()) {
                return Vec3.atBottomCenterOf(candidate);
            }
        }
        return null;
    }
}
