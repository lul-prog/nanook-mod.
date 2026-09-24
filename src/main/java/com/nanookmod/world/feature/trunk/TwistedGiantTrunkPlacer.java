package com.nanookmod.world.feature.trunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nanookmod.registry.ModTrunkPlacerTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Tronco grueso (2x2, igual que los abetos gigantes de vanilla) que además
 * "camina" un poco al azar en X/Z mientras sube, dando sensación de tronco
 * torcido/inclinado en vez de perfectamente recto y vertical.
 */
public class TwistedGiantTrunkPlacer extends TrunkPlacer {

    public static final Codec<TwistedGiantTrunkPlacer> CODEC = RecordCodecBuilder.create(
            instance -> trunkPlacerParts(instance).apply(instance, TwistedGiantTrunkPlacer::new)
    );

    // Probabilidad, en cada capa, de desplazarse 1 bloque en X/Z (el "torcido").
    private static final float TWIST_CHANCE = 0.25f;

    public TwistedGiantTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacerTypes.TWISTED_GIANT.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level,
                                                            BiConsumer<BlockPos, BlockState> blockSetter,
                                                            RandomSource random, int freeTreeHeight,
                                                            BlockPos pos, TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();

        int x = pos.getX();
        int z = pos.getZ();
        int baseY = pos.getY();

        for (int y = 0; y < freeTreeHeight; y++) {
            // No torcemos las primeras capas (raíz) ni las últimas (donde arranca la copa),
            // para que la base se vea firme y la copa quede centrada.
            if (y > 1 && y < freeTreeHeight - 2 && random.nextFloat() < TWIST_CHANCE) {
                x += random.nextInt(3) - 1; // -1, 0 o 1
                z += random.nextInt(3) - 1;
            }

            placeLog(level, blockSetter, random, new BlockPos(x, baseY + y, z), config);
            placeLog(level, blockSetter, random, new BlockPos(x + 1, baseY + y, z), config);
            placeLog(level, blockSetter, random, new BlockPos(x, baseY + y, z + 1), config);
            placeLog(level, blockSetter, random, new BlockPos(x + 1, baseY + y, z + 1), config);
        }

        int topY = baseY + freeTreeHeight;
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, topY, z), 0, true));
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x + 1, topY, z), 0, true));
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, topY, z + 1), 0, true));
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x + 1, topY, z + 1), 0, true));

        return attachments;
    }
}
