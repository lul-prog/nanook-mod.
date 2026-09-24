package com.nanookmod.entity;

import com.nanookmod.event.CrowOmenHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Mientras CrowOmenHandler tenga el "llamado" activo para un jugador
 * cercano (vida sostenida en 25% o menos), TODOS los cuervos cercanos -
 * tanto los que ya estaban vigilando como los invocados por el llamado -
 * dan vueltas alrededor de él formando una nube. Cada cuervo tiene su
 * propio radio/altura/velocidad/dirección de giro (fijados al azar en el
 * constructor) además del offset de ángulo por ID, para que la nube se
 * vea desordenada/orgánica en vez de un anillo perfecto y sincronizado.
 */
public class CrowSwarmOmenGoal extends Goal {

    private static final double SEARCH_RADIUS = 40.0D;
    private static final double LEASH_RADIUS = 48.0D;

    private final CrowEntity crow;

    private final double swarmRadius;
    private final double heightOffset;
    private final double angularSpeed;
    private final double flySpeed;
    private final double angleOffset;

    private Player targetPlayer;

    public CrowSwarmOmenGoal(CrowEntity crow) {
        this.crow = crow;
        this.setFlags(EnumSet.of(Flag.MOVE));

        var random = crow.getRandom();
        this.swarmRadius = 7.0D + random.nextDouble() * 5.0D; // 7-12
        this.heightOffset = 4.0D + random.nextDouble() * 5.0D; // 4-9
        int dir = random.nextBoolean() ? 1 : -1;
        this.angularSpeed = (0.04D + random.nextDouble() * 0.03D) * dir; // 0.04-0.07, sentido random
        this.flySpeed = 1.8D + random.nextDouble() * 0.6D; // 1.8-2.4
        this.angleOffset = random.nextDouble() * Math.PI * 2.0;
    }

    @Override
    public boolean canUse() {
        Player player = this.crow.level().getNearestPlayer(this.crow, SEARCH_RADIUS);
        if (player == null || !CrowOmenHandler.isSwarmActive(player)) {
            return false;
        }
        this.targetPlayer = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetPlayer != null
                && this.targetPlayer.isAlive()
                && CrowOmenHandler.isSwarmActive(this.targetPlayer)
                && this.crow.distanceTo(this.targetPlayer) <= LEASH_RADIUS;
    }

    @Override
    public void start() {
        this.crow.setBehavior(CrowEntity.Behavior.OMEN);
    }

    @Override
    public void stop() {
        this.crow.setBehavior(CrowEntity.Behavior.AMBIENT);
        this.targetPlayer = null;
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null) {
            return;
        }

        double angle = this.crow.level().getGameTime() * this.angularSpeed + this.angleOffset;

        Vec3 playerPos = this.targetPlayer.position();
        double x = playerPos.x + Math.cos(angle) * this.swarmRadius;
        double z = playerPos.z + Math.sin(angle) * this.swarmRadius;
        double y = playerPos.y + this.heightOffset;

        this.crow.flyTowards(new Vec3(x, y, z), this.flySpeed);
        this.crow.getLookControl().setLookAt(playerPos.x, playerPos.y, playerPos.z);
    }
}
