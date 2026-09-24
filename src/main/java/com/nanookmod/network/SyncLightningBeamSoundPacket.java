package com.nanookmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto (a quien esté trackeando la entidad del rayo, ver
 * PacketDistributor.TRACKING_ENTITY_AND_SELF en NecroticKnightEntity /
 * NecroticLightningBeamEntity) cuando el rayo nace (active=true) y cuando
 * desaparece de verdad -- ver NecroticLightningBeamEntity#tick, no cuando
 * termina la ANIMACIÓN del jefe -- (active=false). Va con el id de la
 * entidad del rayo, no del jefe, porque puede haber varios jefes/rayos
 * vivos a la vez (mirror clones).
 *
 * Mismo patrón que SyncNecroticKnightMusicPacket, pero para el sonido del
 * rayo -- ver NecroticLightningBeamSoundHandler en el cliente, que es quien
 * realmente arranca/para (con fade out) el sonido.
 */
public class SyncLightningBeamSoundPacket {

    private final int beamEntityId;
    private final boolean active;

    public SyncLightningBeamSoundPacket(int beamEntityId, boolean active) {
        this.beamEntityId = beamEntityId;
        this.active = active;
    }

    public static void encode(SyncLightningBeamSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.beamEntityId);
        buf.writeBoolean(msg.active);
    }

    public static SyncLightningBeamSoundPacket decode(FriendlyByteBuf buf) {
        return new SyncLightningBeamSoundPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SyncLightningBeamSoundPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                com.nanookmod.client.sound.NecroticLightningBeamSoundHandler.setActive(msg.beamEntityId, msg.active));
        ctx.setPacketHandled(true);
    }
}