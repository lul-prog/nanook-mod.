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
 * Tronco de 1 bloque de ancho (como un abeto normal), con una probabilidad
 * MUY baja de desplazarse 1 bloque en X/Z mientras sube. Por defecto queda
 * casi recto (para parecerse a la referencia de troncos rectos), pero se
 * puede subir TWIST_CHANCE si se quiere más inclinación/torcido.
 */
public class TwistedTrunkPlacer extends TrunkPlacer {

    public static final Codec<TwistedTrunkPlacer> CODEC = RecordCodecBuilder.create(
            instance -> trunkPlacerParts(instance).apply(instance, TwistedTrunkPlacer::new)
    );

    // Probabilidad, en cada capa, de desplazarse 1 bloque en X/Z. Bájalo a 0 para un tronco 100% recto.
    private static final float TWIST_CHANCE = 0.08f;

    public TwistedTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacerTypes.TWISTED_SINGLE.get();
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
            // No torcemos la base (primeras 2 capas) para que se vea bien plantado.
            if (y > 1 && random.nextFloat() < TWIST_CHANCE) {
                x += random.nextInt(3) - 1; // -1, 0 o 1
                z += random.nextInt(3) - 1;
            }

            placeLog(level, blockSetter, random, new BlockPos(x, baseY + y, z), config);
        }

        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, baseY + freeTreeHeight, z), 0, false));

        return attachments;
    }
}