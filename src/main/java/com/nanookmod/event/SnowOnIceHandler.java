package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla excluye ICE y PACKED_ICE (por identidad de bloque, no por tag) de la
 * mecánica de acumulación de nieve por clima. Como frost_ice es un bloque
 * distinto, esa exclusión no lo alcanza y el juego le pone nieve encima con
 * el tiempo. Aquí interceptamos justo después de que el juego coloca esa
 * nieve y la quitamos si está sobre frost_ice.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID)
public class SnowOnIceHandler {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // Filtro barato primero: solo nos interesa cuando se acaba de colocar nieve.
        if (!event.getState().is(Blocks.SNOW)) {
            return;
        }

        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level level) || level.isClientSide()) {
            return;
        }

        BlockPos snowPos = event.getPos();
        BlockPos belowPos = snowPos.below();

        if (level.getBlockState(belowPos).is(ModBlocks.FROST_ICE.get())) {
            level.removeBlock(snowPos, false);
        }
    }
}