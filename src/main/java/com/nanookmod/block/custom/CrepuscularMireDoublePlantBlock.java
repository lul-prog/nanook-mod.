package com.nanookmod.block.custom;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Planta doble (2 bloques de altura) que crece sobre arena/barro crepuscular.
 * Mismo patrón que FrostDoublePlantBlock: una sola clase de bloque representa
 * las dos mitades (HALF=LOWER/UPPER), y CrepuscularPatchFeature ya sabe
 * colocarla con DoublePlantBlock.placeAt(...).
 */
public class CrepuscularMireDoublePlantBlock extends DoublePlantBlock {

    public CrepuscularMireDoublePlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this);
        } else {
            BlockPos below = pos.below();
            Block blockBelow = level.getBlockState(below).getBlock();

            return blockBelow == ModBlocks.CREPUSCULAR_SAND.get()
                    || blockBelow == Blocks.MUD;
        }
    }
}
