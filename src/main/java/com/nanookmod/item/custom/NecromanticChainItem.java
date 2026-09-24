package com.nanookmod.item.custom;

import com.nanookmod.NanookMod;
import com.nanookmod.capability.NecromanticChainCapability;
import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.SyncNecromanticChainPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;
import java.util.List;

public class NecromanticChainItem extends Item {

    public NecromanticChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (cap.isActive()) {
            NecromanticChainCapability.LOGGER.info("[NecromanticChain] Uso rechazado: ya está activa.");
            return InteractionResultHolder.fail(stack);
        }

        long gameTime = ((ServerLevel) level).getGameTime();
        long remainingCooldown = cap.getCooldownRemaining(gameTime);
        if (remainingCooldown > 0) {
            NecromanticChainCapability.LOGGER.info("[NecromanticChain] Uso rechazado: quedan {} ticks de cooldown (gameTime={}).",
                    remainingCooldown, gameTime);
            return InteractionResultHolder.fail(stack);
        }

        cap.activate(player);
        player.getCooldowns().addCooldown(this, NecromanticChainCapability.DURATION_TICKS);
        // player.playSound(ModSounds.NECROMANTIC_CHAIN_ACTIVATE.get(), 1.0F, 1.0F); // COMENTADO

        cap.syncToClient((ServerPlayer) player);
        player.swing(hand, true);

        return InteractionResultHolder.sidedSuccess(stack, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (level != null && player != null) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
            if (cap != null) {
                if (cap.isActive()) {
                    tooltip.add(Component.translatable("tooltip.nanookmod.necromantic_chain.active")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    long remaining = cap.getCooldownRemaining(level.getGameTime());
                    if (remaining > 0) {
                        tooltip.add(Component.translatable("tooltip.nanookmod.necromantic_chain.cooldown", remaining / 20)
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
        tooltip.add(Component.translatable("tooltip.nanookmod.necromantic_chain.desc")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}