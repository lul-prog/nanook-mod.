package com.nanookmod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Se activa cuando GnutEntity acumula suficiente daño reciente
 * (ver hurt()/consumeFleeRequest() en GnutEntity). Prioridad más alta que
 * GnutAttackGoal, así que interrumpe un ataque en curso si hace falta.
 *
 * No borra el target: cuando termina la huida (por tiempo), GnutAttackGoal
 * simplemente retoma porque el objetivo sigue siendo el mismo.
 */
public class GnutFleeGoal extends Goal {

    private final GnutEntity gnut;
    private final double fleeSpeed;
    private final int dodgeFleeDurationTicks;

    private int ticksLeft;
    private int recalcCooldown;

    public GnutFleeGoal(GnutEntity gnut, double fleeSpeed, int dodgeFleeDurationTicks) {
        this.gnut = gnut;
        this.fleeSpeed = fleeSpeed;
        this.dodgeFleeDurationTicks = dodgeFleeDurationTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return gnut.getTarget() != null && gnut.consumeFleeRequest();
    }

    @Override
    public boolean canContinueToUse() {
        if (gnut.isCurrentFleeHealFlee() && gnut.getHealth() >= gnut.getMaxHealth()) {
            return false; // ya se curó del todo con la regeneración - no hace falta seguir corriendo
        }
        return ticksLeft > 0;
    }

    @Override
    public void start() {
        if (gnut.isCurrentFleeHealFlee()) {
            ticksLeft = gnut.getHealFleeDurationTicks();
            gnut.onHealFleeStarted(); // acá se aplica la Regeneración, si quedan usos
        } else {
            ticksLeft = dodgeFleeDurationTicks;
        }
        recalcCooldown = 0; // fuerza a elegir dirección ya en el primer tick
    }

    @Override
    public void stop() {
        gnut.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticksLeft--;

        LivingEntity target = gnut.getTarget();
        if (target == null) {
            return;
        }

        recalcCooldown--;

        // Recalcula la dirección seguido (cada ~0.4-0.7s), no solo cuando termina el
        // camino anterior - así zigzaguea en vez de correr en línea recta hacia un solo
        // punto, que era fácil de perseguir/alcanzar en diagonal.
        if (recalcCooldown <= 0 || gnut.getNavigation().isDone()) {
            recalcCooldown = 8 + gnut.getRandom().nextInt(7); // 8 a 14 ticks
            Vec3 awayPos = DefaultRandomPos.getPosAway(gnut, 10, 6, target.position());
            if (awayPos != null) {
                gnut.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, fleeSpeed);
            }
        }

        // Mira hacia atrás mientras corre, más expresivo que mirar para adelante a ciegas.
        gnut.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}