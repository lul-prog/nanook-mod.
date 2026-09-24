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

/**
 * Proyectil del Skeletal Mage (VFX_NECROBALL en el YAML). Mismo patrón que
 * NanookClawProjectile/SnowyBlizzIceball: AbstractArrow + item dummy para
 * el modelo visual (ver docx sección 4 -- GeckoLib no orienta bien
 * proyectiles basados en ThrowableProjectile/LivingEntity).
 */
public class SkeletalMageNecroball extends AbstractArrow {

    private static final int MAX_LIFETIME_TICKS = 60;

    public SkeletalMageNecroball(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SkeletalMageNecroball(Level level, LivingEntity shooter) {
        super(ModEntities.SKELETAL_MAGE_NECROBALL.get(), shooter, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // A propósito NO llamamos a super.onHitEntity(...) -- ver el mismo
        // comentario en SnowyBlizzIceball, el default de AbstractArrow
        // duplicaría el daño y metería el sonido de flecha vainilla.
        if (result.getEntity() instanceof Player player) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
            SkeletalMageEntity.applyHitEffects(player, owner);

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SOUL_ESCAPE, this.getSoundSource(), 1.0F, 0.8F);
        }
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Sin esto caía al default de AbstractArrow: clavarse como flecha
        // (inGround=true) y quedar visible pegada en el bloque un buen
        // rato -- es una bola de magia, no una flecha, tiene que
        // desaparecer al toque. Mismo motivo que onHitEntity de arriba
        // para NO llamar a super.onHitBlock(...): evita el sonido de
        // flecha vainilla pegando contra madera/piedra.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SOUL_ESCAPE, this.getSoundSource(), 1.0F, 0.8F);
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
}