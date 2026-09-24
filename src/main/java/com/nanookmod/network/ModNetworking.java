package com.nanookmod.network;

import com.nanookmod.NanookMod;
import com.nanookmod.network.SyncNecromanticChainPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NanookMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextId(),
                SyncFrostedNightSaltPacket.class,
                SyncFrostedNightSaltPacket::encode,
                SyncFrostedNightSaltPacket::decode,
                SyncFrostedNightSaltPacket::handle

        );
        CHANNEL.registerMessage(
                nextId(),
                SyncTwilightNightPacket.class,
                SyncTwilightNightPacket::encode,
                SyncTwilightNightPacket::decode,
                SyncTwilightNightPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                SyncNecroticKnightMusicPacket.class,
                SyncNecroticKnightMusicPacket::encode,
                SyncNecroticKnightMusicPacket::decode,
                SyncNecroticKnightMusicPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                SyncNecroticKnightHudPacket.class,
                SyncNecroticKnightHudPacket::encode,
                SyncNecroticKnightHudPacket::decode,
                SyncNecroticKnightHudPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                SyncNecromanticChainPacket.class,
                SyncNecromanticChainPacket::encode,
                SyncNecromanticChainPacket::decode,
                SyncNecromanticChainPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                SyncLightningBeamSoundPacket.class,
                SyncLightningBeamSoundPacket::encode,
                SyncLightningBeamSoundPacket::decode,
                SyncLightningBeamSoundPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                SyncNanookHudPacket.class,
                SyncNanookHudPacket::encode,
                SyncNanookHudPacket::decode,
                SyncNanookHudPacket::handle
        );
        // Temblor de camara de los impactos pesados de Nanook
        // (double_ground, jump_smash, embestida). Ver ScreenShakeHandler.
        CHANNEL.registerMessage(
                nextId(),
                NanookScreenShakePacket.class,
                NanookScreenShakePacket::encode,
                NanookScreenShakePacket::decode,
                NanookScreenShakePacket::handle
        );
    }
}