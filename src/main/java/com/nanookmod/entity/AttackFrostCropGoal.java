package com.nanookmod.entity;

import com.nanookmod.block.custom.FrostCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Hace que el mob camine hasta el FrostCrop más cercano y lo destruya, igual
 * que los zombies con los huevos de tortuga. Solo se activa si el mob NO
 * tiene ya un objetivo vivo al que atacar (un jugador cerca siempre tiene
 * prioridad sobre un cultivo), así que esto obliga a proteger los cultivos
 * con paredes/techo tipo invernadero si no quieres estar siempre vigilando.
 *
 * Uso: this.goalSelector.addGoal(N, new AttackFrostCropGoal(this, 1.0D, 16));
 */
public class AttackFrostCropGoal extends MoveToBlockGoal {

    private static final int TICKS_TO_BREAK = 30; // medio segundo y monedas parado rompiendo

    private final PathfinderMob mob;
    private int breakTicks;

    public AttackFrostCropGoal(PathfinderMob mob, double speedModifier, int searchRange) {
        super(mob, speedModifier, searchRange);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null) return false; // un jugador siempre tiene prioridad
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null) return false;
        return this.isValidTarget(this.mob.level(), this.blockPos) && this.tryTicks <= 1200;
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FrostCrop;
    }

    /**
     * Reemplaza el chequeo de "llegué" por defecto de MoveToBlockGoal (que
     * resultó ser demasiado estricto y casi nunca daba true) por uno propio,
     * más permisivo: basta con estar cerca en horizontal y a una altura
     * parecida.
     */
    @Override
    public boolean isReachedTarget() {
        double dx = this.blockPos.getX() + 0.5D - this.mob.getX();
        double dy = this.blockPos.getY() - this.mob.getY();
        double dz = this.blockPos.getZ() + 0.5D - this.mob.getZ();
        // dy chico a propósito: el cultivo está al ras del suelo, así que el
        // mob tiene que estar prácticamente al mismo nivel (mismo bloque),
        // no un bloque arriba mirando hacia abajo.
        return (dx * dx + dz * dz) <= 2.5D && Math.abs(dy) <= 0.55D;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isReachedTarget()) {
            if (this.breakTicks == 0) {
                // Pequeño salto/impulso hacia el cultivo, como un "pisotón"
                // antes de romperlo (además de sonido, para que se note que
                // está atacando y no solo parado ahí).
                this.mob.getLookControl().setLookAt(
                        this.blockPos.getX() + 0.5D, this.blockPos.getY(), this.blockPos.getZ() + 0.5D
                );
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().x, 0.25D, this.mob.getDeltaMovement().z);
                this.mob.level().playSound(null, this.blockPos, SoundEvents.CROP_BREAK,
                        this.mob.getSoundSource(), 1.0F, 1.0F);
            }
            this.breakTicks++;
            if (this.breakTicks >= TICKS_TO_BREAK) {
                // false = sin soltar drops (es un castigo, no una cosecha gratis)
                this.mob.level().destroyBlock(this.blockPos, false);
                this.mob.level().gameEvent(this.mob, GameEvent.BLOCK_DESTROY, this.blockPos);
                this.breakTicks = 0;
            }
        } else {
            this.breakTicks = 0;
        }
    }
}