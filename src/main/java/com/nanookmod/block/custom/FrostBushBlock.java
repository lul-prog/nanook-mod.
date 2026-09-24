package com.nanookmod.block.custom;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

public class FrostBushBlock extends BushBlock {

    public FrostBushBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState stateBelow = level.getBlockState(below);
        Block blockBelow = stateBelow.getBlock();

        return blockBelow == ModBlocks.FROST_GRASS.get()
                || blockBelow == ModBlocks.FROST_DIRT.get()
                || blockBelow == Blocks.GRASS_BLOCK
                || blockBelow == Blocks.DIRT
                || blockBelow == Blocks.PODZOL
                || blockBelow == Blocks.COARSE_DIRT
                || blockBelow == Blocks.ROOTED_DIRT
                || blockBelow == Blocks.SNOW_BLOCK;
    }
}