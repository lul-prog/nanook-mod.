package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class SnowyBlizzIceball extends AbstractArrow {

    private static final float DAMAGE = 10.0F;
    private static final int MAX_LIFETIME_TICKS = 80;

    public SnowyBlizzIceball(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SnowyBlizzIceball(Level level, LivingEntity shooter) {
        super(ModEntities.SNOWY_BLIZZ_ICEBALL.get(), shooter, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // OJO: a propósito NO llamamos a super.onHitEntity(result) acá.
        // El comportamiento por defecto de AbstractArrow aplica su propio
        // cálculo de daño (duplicando el nuestro) Y reproduce
        // SoundEvents.ARROW_HIT automáticamente - ese es el "sonido de
        // flecha" que no queríamos. Manejamos todo nosotros.
        if (result.getEntity() instanceof Player player) {
            player.hurt(this.damageSources().mobAttack(
                    this.getOwner() instanceof LivingEntity owner ? owner : null), DAMAGE);

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, this.getSoundSource(), 1.0F, 1.3F);
        }
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Mismo motivo que onHitEntity: sin este override caía al default
        // de AbstractArrow (clavarse como flecha + sonido de flecha
        // pegando contra el bloque). Reusamos el mismo GLASS_BREAK que ya
        // suena al pegarle a una entidad -- tiene sentido que la bola de
        // hielo se "rompa" igual contra una pared.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GLASS_BREAK, this.getSoundSource(), 1.0F, 1.3F);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > MAX_LIFETIME_TICKS && !this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void tickDespawn() {
        this.discard();
    }
}