package com.nanookmod.network;

import com.nanookmod.NanookMod;
import com.nanookmod.event.TwilightNightHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Si un jugador se conecta (o cambia de dimensión) mientras la Luna
 * Crepuscular ya está activa, sin esto se quedaría viendo la luna/cielo
 * normal hasta el próximo cambio de estado. Este handler le manda el
 * estado actual apenas entra.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TwilightNightSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.level() instanceof ServerLevel serverLevel) {
            boolean active = TwilightNightHandler.isTwilightNight(serverLevel);
            long startGameTime = active ? TwilightNightHandler.getServerTransitionStartGameTime() : 0L;
            int durationTicks = active ? TwilightNightHandler.getServerTransitionDurationTicks() : 0;
            ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncTwilightNightPacket(active, startGameTime, durationTicks)
            );
        }
    }
}