package com.nanookmod.block.custom;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

public class FrostDoublePlantBlock extends DoublePlantBlock {

    public FrostDoublePlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            // Para la parte superior, verifica que la parte inferior sea del mismo tipo
            BlockState below = level.getBlockState(pos.below());
            return below.is(this);
        } else {
            // Para la parte inferior, verifica que pueda colocarse sobre el bloque de abajo
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
}