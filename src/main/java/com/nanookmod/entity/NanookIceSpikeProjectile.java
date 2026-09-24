package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pincho de hielo del ataque nanook_ice_fist_ground: sale de Nanook hacia
 * afuera arrastrándose por el suelo (sin gravedad, altura fija) y congela +
 * ralentiza a quien toque, replicando el totem del YAML original
 * (freeze{ticks=40} + potion{SLOW;l=2} + damage).
 *
 * NO rompe escudos a propósito: es un ataque a distancia y telegrafiado, el
 * escudo es justamente el counterplay correcto contra este.
 *
 * VFX: el BLOCK de BLUE_ICE se mantiene (es una de las partículas azuladas
 * del jefe); la SNOWFLAKE blanca vanilla pasó a ModParticles.SNOWY_DUST.
 */
public class NanookIceSpikeProjectile extends AbstractArrow {

    /** Velocidad de avance (v=6 del YAML, escalado a bloques/tick). */
    public static final double SPEED = 0.55D;

    private static final float DAMAGE = 5.0F;
    private static final int FREEZE_TICKS = 40;
    private static final int SLOW_TICKS = 40;
    private static final int MAX_LIFETIME_TICKS = 70; // ~alcance de 15 bloques a SPEED

    public NanookIceSpikeProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public NanookIceSpikeProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.NANOOK_ICE_SPIKE.get(), shooter, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // A propósito NO llamamos a super.onHitEntity(): el default de
        // AbstractArrow aplica su propio daño (duplicando el nuestro) y mete
        // el sonido de flecha vanilla.
        if (result.getEntity() instanceof LivingEntity victim && victim != this.getOwner()) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
            victim.hurt(this.damageSources().mobAttack(owner), DAMAGE);
            victim.setTicksFrozen(FREEZE_TICKS);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 1));

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, this.getSoundSource(), 1.0F, 1.3F);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                        victim.getX(), victim.getY() + 0.8D, victim.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.05D);
            }
        }
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Sin esto caería al default de AbstractArrow (clavarse en el bloque
        // como flecha y quedarse ahí visible). El pincho simplemente se
        // rompe contra la pared.
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, this.getSoundSource(), 0.8F, 0.9F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                        this.getX(), this.getY() + 0.3D, this.getZ(), 8, 0.4D, 0.3D, 0.4D, 0.04D);
            }
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        // Mantiene la velocidad constante: AbstractArrow aplica rozamiento en
        // cada tick y sin esto el pincho iría frenándose hasta pararse a
        // mitad de camino.
        Vec3 motion = this.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal > 1.0E-4) {
            this.setDeltaMovement(motion.x / horizontal * SPEED, 0.0D, motion.z / horizontal * SPEED);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            // Azulada: se queda.
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState()),
                    this.getX(), this.getY() + 0.2D, this.getZ(), 3, 0.25D, 0.25D, 0.25D, 0.02D);
            // Antes: ParticleTypes.SNOWFLAKE (blanca vanilla).
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    this.getX(), this.getY() + 0.3D, this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.01D);
        }

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    /** El pincho nunca se queda "clavado" esperando a despawnear. */
    @Override
    protected void tickDespawn() {
        this.discard();
    }
}
