package com.nanookmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto a un jugador puntual cuando arranca o deja de ver a
 * Nanook (mismo patrón exacto que SyncNecroticKnightHudPacket).
 *
 * No manda la vida ni nada del estado del jefe: eso ya viaja solo
 * (getHealth/getMaxHealth son vanilla y ya están sincronizados). Este
 * paquete solo dice "el jefe con este entityId es el que tenés que dibujar
 * en el HUD custom" (active=true) o "dejá de dibujarlo" (active=false).
 */
public class SyncNanookHudPacket {

    private final int entityId;
    private final boolean active;

    public SyncNanookHudPacket(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public static void encode(SyncNanookHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.active);
    }

    public static SyncNanookHudPacket decode(FriendlyByteBuf buf) {
        return new SyncNanookHudPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SyncNanookHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.active) {
                com.nanookmod.client.hud.NanookHudTracker.setActive(msg.entityId);
            } else {
                com.nanookmod.client.hud.NanookHudTracker.clear(msg.entityId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
