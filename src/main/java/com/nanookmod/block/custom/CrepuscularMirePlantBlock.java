package com.nanookmod.block.custom;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base compartida para las plantitas que crecen en los parches de
 * arena/barro del Bosque Crepuscular (junco y flor de fango). Igual que
 * FrostBushBlock hace para el bioma de hielo: una sola clase, varios
 * RegistryObject en ModBlocks.
 */
public class CrepuscularMirePlantBlock extends BushBlock {

    public CrepuscularMirePlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        Block blockBelow = level.getBlockState(below).getBlock();

        return blockBelow == ModBlocks.CREPUSCULAR_SAND.get()
                || blockBelow == Blocks.MUD;
    }
}
