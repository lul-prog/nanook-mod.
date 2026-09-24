package com.nanookmod.item.custom;

import com.nanookmod.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class FrostBottleEmptyItem extends Item {
    public FrostBottleEmptyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Raycast para detectar agua
        BlockHitResult blockHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            BlockState blockState = level.getBlockState(blockHitResult.getBlockPos());

            // Verificar si es agua
            if (blockState.getBlock() instanceof LiquidBlock && blockState.is(Blocks.WATER)) {
                // Consumir botella vacía
                if (!player.getAbilities().instabuild) {
                    itemStack.shrink(1);
                }

                // Dar botella llena
                ItemStack frostBottle = new ItemStack(ModItems.FROST_BOTTLE.get());
                if (itemStack.isEmpty()) {
                    player.setItemInHand(hand, frostBottle);
                } else {
                    if (!player.getInventory().add(frostBottle)) {
                        player.drop(frostBottle, false);
                    }
                }

                // Sonido de llenado
                player.playSound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
                player.awardStat(Stats.ITEM_USED.get(this));

                return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
            }
        }

        return InteractionResultHolder.pass(itemStack);
    }
}