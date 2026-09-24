package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class NanookClawProjectile extends AbstractArrow {

    private static final float DAMAGE = 6.0F;
    private static final int MAX_LIFETIME_TICKS = 60;

    public NanookClawProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public NanookClawProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.NANOOK_CLAW_PROJECTILE.get(), shooter, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // OJO: sacamos el super.onHitEntity(result) que había acá -- el
        // default de AbstractArrow aplica SU PROPIO cálculo de daño (de
        // yapa, duplicando el player.hurt(...) de abajo -- este proyectil
        // pegaba el doble de lo que pensabas) Y reproduce
        // SoundEvents.ARROW_HIT automáticamente, el "sonido de flecha" que
        // no queríamos. Mismo patrón que SnowyBlizzIceball/
        // SkeletalMageNecroball, que ya lo hacían bien.
        if (result.getEntity() instanceof Player player) {
            player.hurt(this.damageSources().mobAttack(
                    this.getOwner() instanceof LivingEntity owner ? owner : null), DAMAGE);
        }
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Sin esto caía al default de AbstractArrow: clavarse como flecha
        // (inGround=true) y reproducir su sonido de impacto contra
        // madera/piedra. No le pusimos un sonido propio -- el diseño
        // original de este proyectil tampoco tenía uno custom al pegarle
        // a una entidad, así que mantenemos silencio acá también por
        // consistencia; agregalo fácil si en algún momento lo querés.
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