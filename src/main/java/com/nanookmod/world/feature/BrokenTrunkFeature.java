package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Tocón / tronco roto del Bosque Crepuscular, en 3 piezas bien definidas:
 *
 * 1. TRONCO CENTRAL: columna sólida 2x2 (nunca se ensancha), 2 o 3 bloques
 *    de alto (50/50). Es solo la forma del tronco, sin huecos ni astillas.
 *
 * 2. PARTE SUPERIOR (anidada arriba del tronco central, mismo 2x2, nunca
 *    se ensancha): cada una de las 4 columnas del 2x2 sube una cantidad
 *    independiente y random de 0 a 3 bloques por encima del tronco
 *    central. Como cada columna termina a una altura distinta, el remate
 *    queda parejo pero irregular/dentado — este es el que da el efecto de
 *    "roto", sin salirse nunca del 2x2.
 *
 * 3. LADOS (anidados al costado del tronco central, nunca se ensanchan
 *    hacia afuera, solo crecen en altura): hasta 2 de los 4 lados del
 *    tronco pueden tener una columna de 1 bloque de ancho pegada, de 0 a 2
 *    bloques de alto. Si terminan saliendo 2 lados, se fuerza a que uno
 *    quede en 2 y el otro en 1, para que no se vea más gordo de un lado
 *    que del otro.
 */
public class BrokenTrunkFeature extends Feature<NoneFeatureConfiguration> {

    // Las 4 celdas del tronco central 2x2.
    private static final int[][] CORE = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};

    // Las 4 direcciones laterales del tronco: offset de 1 bloque hacia
    // afuera desde el borde del tronco central 2x2.
    private static final int[][] SIDES = {
            {-1, 0}, // oeste
            {2, 0},  // este
            {0, -1}, // norte
            {0, 2},  // sur
    };

    public BrokenTrunkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int worldX = origin.getX();
        int worldZ = origin.getZ();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;

        return placeStump(level, random, worldX, surfaceY, worldZ);
    }

    private boolean placeStump(WorldGenLevel level, RandomSource random, int baseX, int baseY, int baseZ) {
        BlockPos basePos = new BlockPos(baseX, baseY, baseZ);
        BlockState ground = level.getBlockState(basePos.below());

        // Solo sobre suelo sólido, nunca sobre agua/aire.
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }

        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. Tronco central: 2x2 sólido, 2 o 3 de alto (50/50).
        int coreHeight = random.nextBoolean() ? 2 : 3;
        for (int y = 0; y < coreHeight; y++) {
            for (int[] c : CORE) {
                changed |= tryPlace(level, pos, basePos, c[0], y, c[1]);
            }
        }

        // 2. Parte superior: cada una de las 4 columnas del mismo 2x2 sube
        // una cantidad independiente (0-3) por encima del tronco central.
        // Nunca se sale del 2x2 — el efecto "roto" es el remate dentado.
        for (int[] c : CORE) {
            int extra = random.nextInt(4); // 0..3
            for (int y = 0; y < extra; y++) {
                changed |= tryPlace(level, pos, basePos, c[0], coreHeight + y, c[1]);
            }
        }

        // 3. Lados: como máximo 2 de las 4 direcciones tienen una columna
        // pegada (1 de ancho, no se ensancha), de 0 a 2 de alto. Si salen
        // 2 lados, se fuerza a que uno sea de 2 y el otro de 1 para que no
        // quede más gordo de un lado que del otro.
        List<int[]> sidesChosen = new ArrayList<>();
        List<int[]> shuffled = new ArrayList<>(List.of(SIDES));
        java.util.Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));

        int sideCount = random.nextInt(3); // 0, 1 o 2 lados
        for (int i = 0; i < sideCount && i < shuffled.size(); i++) {
            sidesChosen.add(shuffled.get(i));
        }

        if (sidesChosen.size() == 2) {
            // Alturas forzadas a {1, 2}, repartidas al azar entre los dos lados.
            boolean firstGetsTwo = random.nextBoolean();
            changed |= placeSide(level, pos, basePos, sidesChosen.get(0), firstGetsTwo ? 2 : 1);
            changed |= placeSide(level, pos, basePos, sidesChosen.get(1), firstGetsTwo ? 1 : 2);
        } else if (sidesChosen.size() == 1) {
            int height = 1 + random.nextInt(2); // 1 o 2
            changed |= placeSide(level, pos, basePos, sidesChosen.get(0), height);
        }

        return changed;
    }

    private boolean placeSide(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockPos basePos,
                              int[] side, int height) {
        boolean changed = false;
        for (int y = 0; y < height; y++) {
            changed |= tryPlace(level, pos, basePos, side[0], y, side[1]);
        }
        return changed;
    }

    private boolean tryPlace(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockPos basePos,
                             int dx, int dy, int dz) {
        pos.setWithOffset(basePos, dx, dy, dz);
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && current.getFluidState().isEmpty()) return false;

        level.setBlock(pos, ModBlocks.CREPUSCULAR_WOOD.get().defaultBlockState(), 3);
        return true;
    }
}