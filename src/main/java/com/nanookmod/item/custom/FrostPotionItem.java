package com.nanookmod.item.custom;

import com.nanookmod.registry.ModEffects;
import com.nanookmod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Poción MEJORADA: a diferencia de la básica (PotionItem), esta da
 * amplificador 1 de PERMAFROST_IMMUNITY, lo que en PermafrostDamageHandler
 * se traduce en inmunidad TOTAL (la carga no sube y no hay temblor/ralentización).
 */
public class FrostPotionItem extends Item {
    public FrostPotionItem(Properties properties) {
        super(properties.stacksTo(1)); // Solo 1 por stack
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // ✅ Solo las pociones tienen brillo
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.nanookmod.permafrost_immunity.effect")
                .append(" II (" + formatDuration(6000) + ")")
                .withStyle(ChatFormatting.AQUA));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.nanookmod.when_applied").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.nanookmod.greater_permafrost_potion.desc1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.nanookmod.greater_permafrost_potion.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.PERMAFROST_IMMUNITY.get(),
                    6000,  // 5 minutos
                    1,     // amplificador 1 = inmunidad TOTAL
                    false, // no ambiental
                    false, // sin partículas
                    true   // sí muestra ícono (inventario + HUD)
            ));
        }

        ItemStack frostBottleEmpty = new ItemStack(ModItems.FROST_BOTTLE_EMPTY.get());

        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!player.getInventory().add(frostBottleEmpty)) {
                player.drop(frostBottleEmpty, false);
            }
        }

        return frostBottleEmpty;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}