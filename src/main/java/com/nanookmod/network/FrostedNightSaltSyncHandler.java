package com.nanookmod.network;

import com.nanookmod.NanookMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FrostedNightSaltSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && serverPlayer.level() instanceof ServerLevel serverLevel) {
            ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncFrostedNightSaltPacket(serverLevel.getSeed())
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // La semilla es la misma en todas las dimensiones del mismo mundo,
        // pero por si acaso reenviamos al cambiar de dimensión (portal al
        // Nether/End, etc.), no cuesta nada y evita cualquier caso raro.
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && serverPlayer.level() instanceof ServerLevel serverLevel) {
            ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncFrostedNightSaltPacket(serverLevel.getSeed())
            );
        }
    }
}