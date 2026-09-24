package com.nanookmod.network;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * El servidor manda esto a TODOS los jugadores cada vez que la Luna
 * Crepuscular se prende o se apaga (cuando arranca el ritual del Altar
 * Necromante, o cuando el NecroticKnight muere). A diferencia de
 * SyncFrostedNightSaltPacket (que solo viaja al conectarse), este viaja
 * cada vez que el estado realmente cambia.
 *
 * v2: además de encender/apagar, ahora también viaja CUÁNDO empezó la
 * transición ({@code transitionStartGameTime}) y CUÁNTO debería tardar
 * ({@code transitionDurationTicks}). El cliente NO recibe un paquete por
 * cada tick de la transición -- con estos dos datos alcanza para que
 * {@link com.nanookmod.event.TwilightNightHandler#getTransitionProgress}
 * calcule el progreso localmente contra el gameTime del nivel, que ya se
 * replica solo. Si active=false, esos dos campos van en 0 y no se usan.
 */
public class SyncTwilightNightPacket {

    private final boolean active;
    private final long transitionStartGameTime;
    private final int transitionDurationTicks;

    public SyncTwilightNightPacket(boolean active, long transitionStartGameTime, int transitionDurationTicks) {
        this.active = active;
        this.transitionStartGameTime = transitionStartGameTime;
        this.transitionDurationTicks = transitionDurationTicks;
    }

    public static void encode(SyncTwilightNightPacket msg, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarLong(msg.transitionStartGameTime);
        buf.writeVarInt(msg.transitionDurationTicks);
    }

    public static SyncTwilightNightPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        long start = buf.readVarLong();
        int duration = buf.readVarInt();
        return new SyncTwilightNightPacket(active, start, duration);
    }

    public static void handle(SyncTwilightNightPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.nanookmod.event.TwilightNightHandler.setClientActive(
                msg.active, msg.transitionStartGameTime, msg.transitionDurationTicks));
        ctx.setPacketHandled(true);
    }
}
