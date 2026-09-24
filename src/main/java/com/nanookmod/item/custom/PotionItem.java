package com.nanookmod.item.custom;

import com.nanookmod.registry.ModEffects;
import com.nanookmod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;

import javax.annotation.Nullable;
import java.util.List;

public class PotionItem extends Item {
    public PotionItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // ✅ Esto hace que tenga el brillo de encantamiento
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // Línea del efecto + duración, igual que una poción vanilla
        tooltip.add(Component.translatable("tooltip.nanookmod.permafrost_immunity.effect")
                .append(" (" + formatDuration(6000) + ")")
                .withStyle(ChatFormatting.BLUE));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.nanookmod.when_applied").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.nanookmod.permafrost_potion.desc1").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.nanookmod.permafrost_potion.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    // Convierte ticks a formato mm:ss (igual que muestran las pociones vanilla)
    private static String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // ✅ Sin esto, el clic derecho no arranca la animación de beber
        // y finishUsingItem nunca se llega a ejecutar
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Aplicar efecto de inmunidad al permafrost (5 minutos = 6000 ticks)
        if (entity instanceof Player player && !level.isClientSide) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.PERMAFROST_IMMUNITY.get(),
                    6000,  // 5 minutos
                    0,     // nivel 1
                    false, // no ambiental
                    false, // sin partículas
                    true   // sí muestra ícono (inventario + HUD)
            ));
        }

        // ✅ Devolver la botella vacía del mod (no la vanilla)
        ItemStack frostBottleEmpty = new ItemStack(ModItems.FROST_BOTTLE_EMPTY.get());

        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            // Si es creativo, no consumir el item
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (!player.getInventory().add(frostBottleEmpty)) {
                    player.drop(frostBottleEmpty, false);
                }
            }
        }

        return frostBottleEmpty;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32; // Tiempo para beber (1.6 segundos)
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}