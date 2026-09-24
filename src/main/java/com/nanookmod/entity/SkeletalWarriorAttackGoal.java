package com.nanookmod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Persigue al objetivo y alterna SLASH1/SLASH2, tal como el YAML original
 * (randomskill{skills=Skeletal_Warrior-SLASH1,Skeletal_Warrior-SLASH2}).
 * Cooldown entre golpes ~0.7s (14 ticks), un poco más que el "Cooldown: 0.5"
 * del skill individual para que no se sienta como metralleta al combinarlo
 * con el delay de swing de la propia animación.
 */
public class SkeletalWarriorAttackGoal extends Goal {

    private static final int ATTACK_COOLDOWN_TICKS = 14;
    private static final double SPEED_MODIFIER = 1.1D;

    private final SkeletalWarriorEntity warrior;

    private int attackCooldown = 0;
    private int pathRecalcCooldown = 0;

    public SkeletalWarriorAttackGoal(SkeletalWarriorEntity warrior) {
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = warrior.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pathRecalcCooldown = 0;
    }

    @Override
    public void stop() {
        warrior.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = warrior.getTarget();
        if (target == null) {
            return;
        }

        warrior.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        double distSqr = warrior.distanceToSqr(target);
        double reachSqr = SkeletalWarriorEntity.MELEE_RANGE * SkeletalWarriorEntity.MELEE_RANGE;

        if (distSqr <= reachSqr) {
            warrior.getNavigation().stop();

            if (warrior.isAttacking()) {
                return; // ya está en medio de un golpe, esperar a que termine
            }

            if (attackCooldown <= 0) {
                if (warrior.getRandom().nextBoolean()) {
                    warrior.triggerSlash1();
                } else {
                    warrior.triggerSlash2();
                }
                attackCooldown = ATTACK_COOLDOWN_TICKS;
            }
            return;
        }

        if (warrior.isAttacking()) {
            return; // no perseguir a mitad del golpe
        }

        if (--pathRecalcCooldown <= 0) {
            pathRecalcCooldown = 4 + warrior.getRandom().nextInt(3);
            warrior.getNavigation().moveTo(target, SPEED_MODIFIER);
        }
    }
}