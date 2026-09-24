package com.nanookmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor le dice a un jugador "sacudite la cámara tanto, durante
 * tantos ticks". Lo usan el golpe al suelo de Nanook (double_ground), el
 * aterrizaje del jump_smash y el impacto de la embestida.
 *
 * Por qué un paquete y no calcularlo solo en cliente: el cliente no sabe
 * en qué tick exacto impactó el ataque (la máquina de estados corre en el
 * servidor), y queremos que el temblor arranque EXACTAMENTE en el frame
 * del impacto. Mandar un pulso puntual es más barato y más preciso que
 * sincronizar todo el estado del ataque.
 *
 * La intensidad ya viene atenuada por distancia desde el servidor (ver
 * NanookEntity#shakeCamerasNearby), así que el cliente solo la aplica.
 *
 * Mismo patrón de handle() que SyncNanookHudPacket: se llama derecho a la
 * clase de cliente dentro del enqueueWork. Es seguro porque este paquete
 * solo se manda con PacketDistributor.NEAR (servidor -> cliente), así que
 * la clase de cliente nunca se carga en un servidor dedicado.
 */
public class NanookScreenShakePacket {

    private final float intensity;
    private final int durationTicks;

    public NanookScreenShakePacket(float intensity, int durationTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
    }

    public static void encode(NanookScreenShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeVarInt(msg.durationTicks);
    }

    public static NanookScreenShakePacket decode(FriendlyByteBuf buf) {
        return new NanookScreenShakePacket(buf.readFloat(), buf.readVarInt());
    }

    public static void handle(NanookScreenShakePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                com.nanookmod.client.ScreenShakeHandler.push(msg.intensity, msg.durationTicks));
        ctx.setPacketHandled(true);
    }
}
