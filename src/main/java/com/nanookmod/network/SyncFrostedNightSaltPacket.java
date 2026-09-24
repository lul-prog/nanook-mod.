package com.nanookmod.network;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto al cliente justo cuando se conecta, para que ambos
 * lados calculen exactamente los mismos días de Noche Escarchada (cada mundo
 * tiene su propio patrón, gracias a esto).
 */
public class SyncFrostedNightSaltPacket {

    private final long salt;

    public SyncFrostedNightSaltPacket(long salt) {
        this.salt = salt;
    }

    public static void encode(SyncFrostedNightSaltPacket msg, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeLong(msg.salt);
    }

    public static SyncFrostedNightSaltPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new SyncFrostedNightSaltPacket(buf.readLong());
    }

    public static void handle(SyncFrostedNightSaltPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.nanookmod.event.FrostedNightHandler.setClientWorldSalt(msg.salt));
        ctx.setPacketHandled(true);
    }
}