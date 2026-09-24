package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PermafrostHandler {

    private static final ResourceKey<Biome> MAR_PRIMIGENIO =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio"));

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        // Verificar si el jugador está en el bioma
        var biome = player.level().getBiome(player.blockPosition());
        if (biome.is(MAR_PRIMIGENIO)) {
            // Aplicar el efecto Permafrost (duración infinita, amplificador 0)
            if (!player.hasEffect(ModEffects.PERMAFROST.get())) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.PERMAFROST.get(),
                        MobEffectInstance.INFINITE_DURATION, // Duración infinita real (-1): el juego la trata como ∞
                        0,                 // Amplificador
                        false,             // No es ambiental (no atenúa el ícono)
                        false,             // No muestra partículas
                        true               // Sí muestra ícono (inventario + HUD)
                ));
            }
        } else {
            // Remover el efecto si el jugador sale del bioma
            if (player.hasEffect(ModEffects.PERMAFROST.get())) {
                player.removeEffect(ModEffects.PERMAFROST.get());
            }
        }
    }
}