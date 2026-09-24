package com.nanookmod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Igual estructura que SnowyBlizzAttackGoal pero sin la parte de melee (el
 * mago no tiene ningún golpe cuerpo a cuerpo en el YAML): mantiene la banda
 * de distancia preferida y dispara apenas el cooldown está listo,
 * independiente de si en ese instante se está moviendo o no.
 */
public class SkeletalMageAttackGoal extends Goal {

    private static final int ATTACK_COOLDOWN_TICKS = 24; // ~1.2s, igual al "Cooldown: 1" del YAML + margen

    private final SkeletalMageEntity mage;
    private int cooldown = 0;

    public SkeletalMageAttackGoal(SkeletalMageEntity mage) {
        this.mage = mage;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mage.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mage.getTarget();
        return mage.isAttacking() || (target != null && target.isAlive());
    }

    @Override
    public void stop() {
        mage.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mage.getTarget();
        if (target == null) {
            return;
        }

        mage.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (mage.isAttacking()) {
            mage.getNavigation().stop();
            return;
        }

        double distance = mage.distanceTo(target);

        if (distance > SkeletalMageEntity.SHOOT_RANGE) {
            mage.getNavigation().moveTo(target, 1.0D);
        } else if (distance < SkeletalMageEntity.MIN_PREFERRED_RANGE) {
            retreatFrom(target);
        } else {
            mage.getNavigation().stop();
        }

        if (cooldown <= 0) {
            if (distance <= SkeletalMageEntity.SHOOT_RANGE) {
                mage.triggerShoot();
                cooldown = ATTACK_COOLDOWN_TICKS;
            }
        } else {
            cooldown--;
        }
    }

    private void retreatFrom(LivingEntity target) {
        Vec3 away = mage.position().subtract(target.position());
        if (away.lengthSqr() < 1.0E-4) {
            away = mage.getForward().reverse();
        }
        away = away.normalize().scale(SkeletalMageEntity.RETREAT_DISTANCE);

        double retreatX = mage.getX() + away.x;
        double retreatZ = mage.getZ() + away.z;

        mage.getNavigation().moveTo(retreatX, mage.getY(), retreatZ, 1.0D);
    }
}