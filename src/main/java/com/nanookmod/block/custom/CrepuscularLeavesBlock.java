package com.nanookmod.block.custom;

import com.nanookmod.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Igual que LeavesBlock vanilla, pero de vez en cuando deja caer una
 * partícula de hoja (nanookmod:crepuscular_leaf) hacia el bloque de abajo,
 * al estilo de las hojas de cerezo. Solo cae si debajo hay aire, para no
 * generar hojas "cayendo" dentro de otro follaje o del tronco.
 */
public class CrepuscularLeavesBlock extends LeavesBlock {

    public CrepuscularLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        // Poco frecuente: si fuera en cada tick se vería como una tormenta de hojas.
        if (random.nextInt(20) != 0) return;

        BlockPos below = pos.below();
        if (!level.getBlockState(below).isAir()) return;

        double x = pos.getX() + 0.3D + random.nextDouble() * 0.4D;
        double y = pos.getY() - 0.05D;
        double z = pos.getZ() + 0.3D + random.nextDouble() * 0.4D;

        level.addParticle(ModParticles.CREPUSCULAR_LEAF.get(), x, y, z, 0.0D, 0.0D, 0.0D);
    }
}
