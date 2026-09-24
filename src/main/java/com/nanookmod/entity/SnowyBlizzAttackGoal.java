package com.nanookmod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SnowyBlizzAttackGoal extends Goal {

    private static final double MELEE_RANGE = 3.0D;
    private static final double MIN_PREFERRED_RANGE = 8.0D;
    private static final double SHOOT_RANGE = 20.0D;
    private static final double RETREAT_DISTANCE = 6.0D;
    private static final int ATTACK_COOLDOWN_TICKS = 30;

    private final SnowyBlizzEntity blizz;
    private int cooldown = 0;

    public SnowyBlizzAttackGoal(SnowyBlizzEntity blizz) {
        this.blizz = blizz;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.blizz.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.blizz.getTarget();
        return this.blizz.isAttacking() || (target != null && target.isAlive());
    }

    @Override
    public void stop() {
        this.blizz.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.blizz.getTarget();
        if (target == null) {
            return;
        }

        this.blizz.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.blizz.isAttacking()) {
            this.blizz.getNavigation().stop();
            return;
        }

        double distance = this.blizz.distanceTo(target);

        // Movimiento: mantener la banda de distancia preferida, independiente
        // de si el cooldown está listo o no.
        if (distance > SHOOT_RANGE) {
            this.blizz.getNavigation().moveTo(target, 1.0D);
        } else if (distance < MIN_PREFERRED_RANGE) {
            retreatFrom(target);
        } else {
            this.blizz.getNavigation().stop();
        }

        // Decisión de ataque: independiente del movimiento, se dispara apenas
        // el cooldown está listo, usando la distancia actual en ese instante.
        if (this.cooldown <= 0) {
            chooseAndStartAttack(distance);
            this.cooldown = ATTACK_COOLDOWN_TICKS;
        } else {
            this.cooldown--;
        }
    }

    /**
     * Retrocede en línea recta alejándose del objetivo, para no terminar
     * acorralado en rango de melee cuando lo que quiere es disparar.
     */
    private void retreatFrom(LivingEntity target) {
        Vec3 away = this.blizz.position().subtract(target.position());
        if (away.lengthSqr() < 1.0E-4) {
            // El objetivo está literalmente encima; no hay dirección clara,
            // usamos hacia donde mira como escape.
            away = this.blizz.getForward().reverse();
        }
        away = away.normalize().scale(RETREAT_DISTANCE);

        double retreatX = this.blizz.getX() + away.x;
        double retreatZ = this.blizz.getZ() + away.z;

        this.blizz.getNavigation().moveTo(retreatX, this.blizz.getY(), retreatZ, 1.0D);
    }

    private void chooseAndStartAttack(double distance) {
        if (distance <= MELEE_RANGE) {
            this.blizz.triggerStabAttack();
        } else {
            this.blizz.triggerShootAttack();
        }
    }
}