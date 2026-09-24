package com.nanookmod.block.custom;

import net.minecraft.core.BlockPos;
import com.nanookmod.registry.ModBlocks;
import com.nanookmod.registry.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sprout Crepuscular: planta pequeña sin colisión que emite luz. Solo se
 * puede plantar sobre terreno crepuscular o tierra/pasto vanilla, igual
 * que FrostBushBlock hace para el bioma de hielo.
 */
public class CrepuscularSproutBlock extends BushBlock {

    public CrepuscularSproutBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState stateBelow = level.getBlockState(below);
        Block blockBelow = stateBelow.getBlock();

        return blockBelow == ModBlocks.CREPUSCULAR_GRASS.get()
                || blockBelow == ModBlocks.CREPUSCULAR_DIRT.get()
                || blockBelow == Blocks.GRASS_BLOCK
                || blockBelow == Blocks.DIRT
                || blockBelow == Blocks.PODZOL
                || blockBelow == Blocks.COARSE_DIRT
                || blockBelow == Blocks.ROOTED_DIRT
                || blockBelow == Blocks.MOSS_BLOCK;
    }

    // Refuerza las motas de luz ambientales del bioma (nanookmod:crepuscular_mote)
    // justo alrededor de cada sprout, para que se note la conexión "esto brilla
    // y por eso atrae luciérnagas" sin depender solo de la partícula ambiental
    // global del bioma.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;

        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;
        double y = pos.getY() + 0.2D + random.nextDouble() * 0.5D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;

        level.addParticle(ModParticles.CREPUSCULAR_MOTE.get(), x, y, z, 0.0D, 0.0D, 0.0D);
    }
}
