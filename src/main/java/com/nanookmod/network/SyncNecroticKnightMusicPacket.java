package com.nanookmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto a los jugadores que están trackeando al
 * NecroticKnight cada vez que arranca o termina la pelea (mismo patrón que
 * SyncTwilightNightPacket, pero para la música de batalla en vez del cielo
 * -- ver NecroticKnightMusicHandler en el cliente, que es quien realmente
 * arranca/para el sonido con fade).
 */
public class SyncNecroticKnightMusicPacket {

    private final int entityId;
    private final boolean active;

    public SyncNecroticKnightMusicPacket(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public static void encode(SyncNecroticKnightMusicPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.active);
    }

    public static SyncNecroticKnightMusicPacket decode(FriendlyByteBuf buf) {
        return new SyncNecroticKnightMusicPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SyncNecroticKnightMusicPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                com.nanookmod.client.sound.NecroticKnightMusicHandler.setActive(msg.entityId, msg.active));
        ctx.setPacketHandled(true);
    }
}