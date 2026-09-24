package com.nanookmod.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Máxima prioridad: sin importar qué esté haciendo el cuervo (posado,
 * vigilando, dando vueltas), si el jugador se le planta demasiado cerca
 * reacciona con la animación "scared" y sale volando rápido a restaurar
 * distancia. Es independiente del resto de los comportamientos - un
 * "espacio personal" universal.
 */
public class CrowStartleGoal extends Goal {

    private static final double STARTLE_DISTANCE = 6.0D;
    private static final double SAFE_DISTANCE = 14.0D;
    private static final double FLEE_SPEED = 2.6D;
    private static final int MAX_DURATION_TICKS = 100;

    private final CrowEntity crow;
    private Player scarePlayer;
    private int ticksFleeing;

    public CrowStartleGoal(CrowEntity crow) {
        this.crow = crow;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Player player = this.crow.level().getNearestPlayer(this.crow, STARTLE_DISTANCE);
        if (player == null || player.isSpectator()) {
            return false;
        }
        this.scarePlayer = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.scarePlayer != null
                && this.scarePlayer.isAlive()
                && this.ticksFleeing < MAX_DURATION_TICKS
                && this.crow.distanceTo(this.scarePlayer) < SAFE_DISTANCE;
    }

    @Override
    public void start() {
        this.ticksFleeing = 0;
        this.crow.triggerScared();
    }

    @Override
    public void stop() {
        this.scarePlayer = null;
    }

    @Override
    public void tick() {
        this.ticksFleeing++;

        Vec3 away = this.crow.position().subtract(this.scarePlayer.position());
        double horizLen = Math.sqrt(away.x * away.x + away.z * away.z);
        if (horizLen < 0.001) {
            away = new Vec3(1.0, 0.0, 0.0);
            horizLen = 1.0;
        }

        double dirX = away.x / horizLen;
        double dirZ = away.z / horizLen;

        Vec3 target = this.crow.position().add(dirX * 8.0, 2.0, dirZ * 8.0);
        this.crow.flyTowards(target, FLEE_SPEED);
    }
}
