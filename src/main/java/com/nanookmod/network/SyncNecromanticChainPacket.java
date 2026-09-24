package com.nanookmod.network;

import com.nanookmod.capability.NecromanticChainCapability;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncNecromanticChainPacket {
    public final boolean active;
    public final float accumulatedDamage;
    public final long cooldownEndTick;
    public final long activeEndTick;

    public SyncNecromanticChainPacket(boolean active, float accumulatedDamage, long cooldownEndTick, long activeEndTick) {
        this.active = active;
        this.accumulatedDamage = accumulatedDamage;
        this.cooldownEndTick = cooldownEndTick;
        this.activeEndTick = activeEndTick;
    }

    public static void encode(SyncNecromanticChainPacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeFloat(packet.accumulatedDamage);
        buf.writeLong(packet.cooldownEndTick);
        buf.writeLong(packet.activeEndTick);
    }

    public static SyncNecromanticChainPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        float accumulatedDamage = buf.readFloat();
        long cooldownEndTick = buf.readLong();
        long activeEndTick = buf.readLong();
        return new SyncNecromanticChainPacket(active, accumulatedDamage, cooldownEndTick, activeEndTick);
    }

    public static void handle(SyncNecromanticChainPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(mc.player);
                if (cap != null) {
                    cap.readClientData(packet);
                }
            }
        });
        context.setPacketHandled(true);
    }
}