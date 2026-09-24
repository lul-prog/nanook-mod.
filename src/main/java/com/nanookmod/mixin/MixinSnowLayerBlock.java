package com.nanookmod.mixin;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla excluye ICE y PACKED_ICE (por identidad de bloque) de la mecánica
 * de "puede sobrevivir nieve encima" en SnowLayerBlock#canSurvive. Como
 * frost_ice es un bloque distinto, no queda cubierto por esa exclusión.
 * Este mixin agrega la misma exclusión para frost_ice, directamente en la
 * fuente del problema (en vez de quitar la nieve después de que ya se puso).
 */
@Mixin(SnowLayerBlock.class)
public class MixinSnowLayerBlock {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void nanookmod$noSnowOnFrostIce(BlockState state, LevelReader level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(ModBlocks.FROST_ICE.get())) {
            cir.setReturnValue(false);
        }
    }
}