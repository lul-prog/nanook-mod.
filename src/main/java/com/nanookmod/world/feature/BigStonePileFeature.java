package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Cúmulo de piedra redondeado y más grande que el minecraft:block_pile vanilla.
 * Se genera como un blob esférico irregular (no una esfera perfecta) alrededor
 * de un punto, con tamaño aleatorio entre MIN_RADIUS y MAX_RADIUS.
 */
public class BigStonePileFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;

    public BigStonePileFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int worldX = origin.getX();
        int worldZ = origin.getZ();
        // WORLD_SURFACE_WG da la posición de AIRE justo encima del suelo; restamos 1
        // para centrar el cúmulo en el bloque sólido de la superficie.
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
        BlockPos center = new BlockPos(worldX, surfaceY, worldZ);

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius / 2; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Distancia "aplastada" en Y para que el cúmulo sea más ancho que alto,
                    // como una roca apoyada en el suelo en vez de una esfera perfecta.
                    double distance = (dx * dx) / (double) (radius * radius)
                            + (dy * dy * 2.2) / (double) (radius * radius)
                            + (dz * dz) / (double) (radius * radius);

                    if (distance > 1.0) {
                        continue;
                    }

                    // Borde irregular: cerca del límite del radio, a veces saltamos el bloque.
                    if (distance > 0.75 && random.nextFloat() < 0.35f) {
                        continue;
                    }

                    pos.setWithOffset(center, dx, dy, dz);
                    BlockState current = level.getBlockState(pos);

                    // Solo evitamos reemplazar agua (para no dejar piedra flotando en un lago).
                    // El aire SÍ se reemplaza: ahí es donde se construye la parte visible
                    // del cúmulo por encima del suelo.
                    if (!current.getFluidState().isEmpty()) {
                        continue;
                    }

                    BlockState toPlace = random.nextFloat() < 0.7f
                            ? Blocks.COBBLESTONE.defaultBlockState()
                            : (random.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());

                    level.setBlock(pos, toPlace, 3);
                    changed = true;
                }
            }
        }

        return changed;
    }
}