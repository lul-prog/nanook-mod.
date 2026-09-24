package com.nanookmod.item.custom;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class FrozenAppleItem extends Item {

    private final int nutrition;
    private final float saturationModifier;

    public FrozenAppleItem(Properties pProperties, int nutrition, float saturationModifier) {
        super(pProperties);
        this.nutrition = nutrition;
        this.saturationModifier = saturationModifier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pUsedHand);

        // Si ya está en cooldown, no dejamos que empiece a comer
        if (pPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemstack);
        }

        pPlayer.startUsingItem(pUsedHand);
        return InteractionResultHolder.consume(itemstack);
    }

    @Override
    public int getUseDuration(ItemStack pStack) {
        return 32; // 1.6 segundos para comerla
    }

    @Override
    public UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pEntityLiving) {
        // NO llamamos a super.finishUsingItem() para evitar que consuma el item

        if (pEntityLiving instanceof Player player) {
            // 1. Aplicar manualmente la restauración de hambre y saturación
            player.getFoodData().eat(this.nutrition, this.saturationModifier);

            // 2. Aplicar los efectos mágicos
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 3600, 0));

            // 3. Aplicar cooldown de 20 segundos (400 ticks)
            player.getCooldowns().addCooldown(this, 400);
        }

        // Devolvemos el mismo stack SIN consumir
        return pStack;
    }
}