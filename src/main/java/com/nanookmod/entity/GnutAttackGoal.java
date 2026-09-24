package com.nanookmod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Persigue al objetivo y decide el combo de ataque. Melee y stomp tienen
 * cooldowns INDEPENDIENTES a propósito: mientras espera el cooldown largo del
 * stomp (2s), sigue pegando golpes de contacto si está cerca, en vez de
 * quedarse plantado sin hacer nada.
 */
public class GnutAttackGoal extends Goal {

    private static final int HITS_PER_STOMP = 2;
    private static final int MELEE_COOLDOWN_TICKS = 16;
    private static final int STOMP_COOLDOWN_TICKS = 40;
    // Pequeño respiro tras aterrizar el stomp antes de poder pegar de cerca de nuevo,
    // para que no se sienta instantáneo, pero mucho menor al cooldown completo del stomp.
    private static final int POST_STOMP_MELEE_GRACE_TICKS = 8;
    private static final double SPEED_MODIFIER = 1.1D;

    private final GnutEntity gnut;

    private int meleeCooldown = 0;
    private int stompCooldown = 0;
    private int pathRecalcCooldown = 0;
    private int meleeStreak = 0;

    public GnutAttackGoal(GnutEntity gnut) {
        this.gnut = gnut;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = gnut.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pathRecalcCooldown = 0;
        meleeStreak = 0;
    }

    @Override
    public void stop() {
        gnut.getNavigation().stop();
        // El target lo maneja el targetSelector (HurtByTargetGoal) - no tocarlo acá.
    }

    @Override
    public void tick() {
        LivingEntity target = gnut.getTarget();
        if (target == null) {
            return;
        }

        gnut.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSqr = gnut.distanceToSqr(target);
        double stompReachSqr = GnutEntity.STOMP_RADIUS * GnutEntity.STOMP_RADIUS;
        double meleeReachSqr = GnutEntity.MELEE_RANGE * GnutEntity.MELEE_RANGE;

        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (stompCooldown > 0) {
            stompCooldown--;
        }

        if (distSqr <= stompReachSqr) {
            if (gnut.getAttackState() != GnutEntity.ATTACK_NONE) {
                gnut.getNavigation().stop();
                return; // ya está en medio de un golpe, esperar a que termine
            }

            boolean closeEnoughForMelee = distSqr <= meleeReachSqr;
            boolean comboReadyForStomp = meleeStreak >= HITS_PER_STOMP;

            if (comboReadyForStomp && stompCooldown <= 0) {
                gnut.getNavigation().stop();
                gnut.triggerStompAttack();
                meleeStreak = 0;
                stompCooldown = STOMP_COOLDOWN_TICKS;
                meleeCooldown = POST_STOMP_MELEE_GRACE_TICKS;
            } else if (closeEnoughForMelee && meleeCooldown <= 0) {
                gnut.getNavigation().stop();
                gnut.triggerMeleeAttack();
                meleeStreak++;
                meleeCooldown = MELEE_COOLDOWN_TICKS;
            } else if (!closeEnoughForMelee && stompCooldown <= 0) {
                gnut.getNavigation().stop();
                gnut.triggerStompAttack();
                meleeStreak = 0;
                stompCooldown = STOMP_COOLDOWN_TICKS;
                meleeCooldown = POST_STOMP_MELEE_GRACE_TICKS;
            } else if (!closeEnoughForMelee) {
                // Anillo medio, sin combo listo y stomp en cooldown: en vez de plantarse,
                // se acerca para intentar entrar en rango de golpe cercano.
                gnut.getNavigation().moveTo(target, SPEED_MODIFIER);
            } else {
                // En rango de contacto pero con cooldown corto activo (máx. ~0.8s):
                // pausa breve esperada, no un cuelgue.
                gnut.getNavigation().stop();
            }
            return;
        }

        if (gnut.getAttackState() != GnutEntity.ATTACK_NONE) {
            return; // no perseguir a mitad del salto/telegraph del ataque
        }

        if (--pathRecalcCooldown <= 0) {
            pathRecalcCooldown = 4 + gnut.getRandom().nextInt(3);
            gnut.getNavigation().moveTo(target, SPEED_MODIFIER);
        }
    }
}