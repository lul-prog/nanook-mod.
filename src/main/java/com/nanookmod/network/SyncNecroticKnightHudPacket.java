package com.nanookmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto a un jugador puntual cuando arranca o deja de ver
 * al NecroticKnight (mismo patrón que SyncNecroticKnightMusicPacket, pero
 * para el HUD custom de Corrupción / Drenaje Vital en vez de la música).
 *
 * No manda los valores de Corrupción/Drenaje en sí -- esos ya viajan solos
 * como SynchedEntityData de la entidad (el cliente los lee directo de
 * NecroticKnightEntity una vez que la tiene cargada). Este paquete solo le
 * dice al cliente "el jefe con este entityId es el que tenés que mostrar
 * en el HUD custom" (active=true) o "dejá de mostrarlo" (active=false).
 *
 * Se apoya en que startSeenByPlayer/stopSeenByPlayer se disparan por el
 * mismo tracking de chunks que hace que la entidad esté cargada del lado
 * cliente -- si el jugador puede ver el HUD, también tiene la entidad, así
 * que no hay riesgo de leer datos de una entidad no sincronizada.
 */
public class SyncNecroticKnightHudPacket {

    private final int entityId;
    private final boolean active;

    public SyncNecroticKnightHudPacket(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public static void encode(SyncNecroticKnightHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.active);
    }

    public static SyncNecroticKnightHudPacket decode(FriendlyByteBuf buf) {
        return new SyncNecroticKnightHudPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SyncNecroticKnightHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.active) {
                com.nanookmod.client.hud.NecroticKnightHudTracker.setActive(msg.entityId);
            } else {
                com.nanookmod.client.hud.NecroticKnightHudTracker.clear(msg.entityId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
