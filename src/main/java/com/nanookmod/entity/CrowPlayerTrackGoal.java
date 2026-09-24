package com.nanookmod.entity;

import com.nanookmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Si un jugador entra en TRACK_RANGE, el cuervo lo "vigila": busca un
 * posadero real (superficie sólida, con línea de visión al jugador)
 * dentro de una banda de distancia horizontal [watchMin, watchMax] y se
 * queda ahí quieto (posado, animación idle) mientras el jugador no se
 * mueva lo suficiente como para salirse de la banda - ya no da vueltas sin
 * parar todo el tiempo. Si no hay perch disponible, circula como
 * respaldo mientras sigue reintentando encontrar uno.
 *
 * Cada cuervo tiene su propia banda/altura/velocidad/dirección de giro
 * (fijadas al azar en el constructor), para que varios cuervos vigilando
 * al mismo jugador NO se vean todos siguiendo el mismo camino.
 */
public class CrowPlayerTrackGoal extends Goal {

    private static final double TRACK_RANGE = 28.0D;
    private static final double LEASH_RANGE = 40.0D;

    private static final double PERCH_SEARCH_RETRY_TICKS = 40;
    private static final double UNPERCH_LEEWAY = 3.0D;

    private final CrowEntity crow;

    // Variación propia de este cuervo (fijada una vez, en el constructor).
    private final double watchMin;
    private final double watchMax;
    private final double heightOffset;
    private final int orbitDir;
    private final double speedMultiplier;
    private final double angularSpeed;

    private Player targetPlayer;
    private Vec3 perchTarget;
    private boolean perched = false;
    private double hoverAngle;
    private int perchSearchCooldown = 0;

    public CrowPlayerTrackGoal(CrowEntity crow) {
        this.crow = crow;
        this.setFlags(EnumSet.of(Flag.MOVE));

        var random = crow.getRandom();
        double band = 15.0D + random.nextDouble() * 3.0D; // 15-18
        this.watchMin = band;
        this.watchMax = band + 3.0D + random.nextDouble() * 2.0D; // +3 a +5 sobre el min
        this.heightOffset = 3.0D + random.nextDouble() * 4.0D; // 3-7
        this.orbitDir = random.nextBoolean() ? 1 : -1;
        this.speedMultiplier = 1.4D + random.nextDouble() * 0.6D; // 1.4-2.0
        this.angularSpeed = (0.01D + random.nextDouble() * 0.02D) * this.orbitDir;
    }

    @Override
    public boolean canUse() {
        Player player = this.crow.level().getNearestPlayer(this.crow, TRACK_RANGE);
        if (player == null || player.isSpectator() || player.isCreative()) {
            return false;
        }
        this.targetPlayer = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetPlayer != null
                && this.targetPlayer.isAlive()
                && !this.targetPlayer.isSpectator()
                && this.crow.distanceTo(this.targetPlayer) <= LEASH_RANGE;
    }

    @Override
    public void start() {
        this.crow.setBehavior(CrowEntity.Behavior.WATCHING);
        this.perched = false;
        this.perchTarget = null;
        this.perchSearchCooldown = 0;
        this.hoverAngle = this.crow.getRandom().nextDouble() * Math.PI * 2.0;

        if (!this.crow.hasNoticedPlayer()) {
            this.crow.setNoticedPlayer(true);
            this.crow.playSound(ModSounds.ENTITY_CROW_NOTICE.get(), 1.0F, 1.0F);
        }
    }

    @Override
    public void stop() {
        this.crow.setBehavior(CrowEntity.Behavior.AMBIENT);
        this.targetPlayer = null;
        this.perched = false;
        this.perchTarget = null;
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null) {
            return;
        }

        Vec3 playerPos = this.targetPlayer.position();

        if (this.perched) {
            this.crow.getLookControl().setLookAt(playerPos.x, playerPos.y + 1.0, playerPos.z);

            double dist = this.crow.distanceTo(this.targetPlayer);
            if (dist < this.watchMin - UNPERCH_LEEWAY || dist > this.watchMax + UNPERCH_LEEWAY) {
                this.perched = false;
                this.perchTarget = null;
            }
            return; // quieto: no se toca el moveControl, se posa de verdad
        }

        if (this.perchTarget == null && this.perchSearchCooldown <= 0) {
            this.perchTarget = findPerchWithinBand(playerPos);
            this.perchSearchCooldown = (int) PERCH_SEARCH_RETRY_TICKS;
        } else if (this.perchSearchCooldown > 0) {
            this.perchSearchCooldown--;
        }

        if (this.perchTarget != null) {
            this.crow.flyTowards(this.perchTarget, this.speedMultiplier);
            this.crow.getLookControl().setLookAt(playerPos.x, playerPos.y + 1.0, playerPos.z);

            if (this.crow.position().distanceToSqr(this.perchTarget) <= 1.5D) {
                this.perched = true;
                this.perchTarget = null;
            }
            return;
        }

        // Respaldo mientras no hay perch disponible: hover/círculo (variado por cuervo).
        this.hoverAngle += this.angularSpeed;

        double dx = this.crow.getX() - playerPos.x;
        double dz = this.crow.getZ() - playerPos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        double desiredRadius;
        double speed;
        if (horizDist < this.watchMin || horizDist < 0.001) {
            desiredRadius = this.watchMin + 3.0D;
            speed = this.speedMultiplier + 0.3D;
        } else if (horizDist > this.watchMax) {
            desiredRadius = this.watchMax - 2.0D;
            speed = this.speedMultiplier;
        } else {
            desiredRadius = horizDist;
            speed = this.speedMultiplier * 0.7D;
        }

        double x = playerPos.x + Math.cos(this.hoverAngle) * desiredRadius;
        double z = playerPos.z + Math.sin(this.hoverAngle) * desiredRadius;
        double y = playerPos.y + this.heightOffset;

        this.crow.flyTowards(new Vec3(x, y, z), speed);
        this.crow.getLookControl().setLookAt(playerPos.x, playerPos.y, playerPos.z);
    }

    /** Busca un posadero sólido dentro de la banda [watchMin, watchMax] del jugador, con línea de visión. */
    private Vec3 findPerchWithinBand(Vec3 playerPos) {
        Level level = this.crow.level();

        for (int i = 0; i < 10; i++) {
            double angle = this.crow.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = this.watchMin + this.crow.getRandom().nextDouble() * (this.watchMax - this.watchMin);

            int x = (int) Math.round(playerPos.x + Math.cos(angle) * radius);
            int z = (int) Math.round(playerPos.z + Math.sin(angle) * radius);
            int baseY = (int) Math.round(playerPos.y) + (int) this.heightOffset;

            for (int dy = -4; dy <= 6; dy++) {
                BlockPos candidate = new BlockPos(x, baseY + dy, z);
                BlockPos below = candidate.below();

                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        || !level.getBlockState(candidate).isAir()
                        || !level.getBlockState(candidate.above()).isAir()) {
                    continue;
                }

                Vec3 perchPos = Vec3.atBottomCenterOf(candidate);
                Vec3 eyeTarget = playerPos.add(0, 1.5, 0);

                HitResult hit = level.clip(new ClipContext(
                        perchPos.add(0, 0.3, 0), eyeTarget,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.crow));

                if (hit.getType() == HitResult.Type.MISS) {
                    return perchPos;
                }
            }
        }
        return null;
    }
}
