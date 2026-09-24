package com.nanookmod.world.feature.trunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nanookmod.registry.ModTrunkPlacerTypes;
import com.nanookmod.util.NanookMathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Tronco recto con ramas de verdad saliendo en ángulo hacia los lados (no solo
 * "ramitas" pegadas al tronco como el upwards_branching de vanilla). Adaptado
 * del enfoque que usa Eternal Starlight para su árbol lunar: cada rama se
 * traza como una línea recta 3D (Bresenham) desde un punto del tronco hasta
 * un punto calculado por rotación (ángulo + distancia), y ahí queda el punto
 * de "anclaje" para que el foliage placer ponga hojas alrededor.
 */
public class CrepuscularBranchingTrunkPlacer extends TrunkPlacer {

    public static final Codec<CrepuscularBranchingTrunkPlacer> CODEC = RecordCodecBuilder.create(
            instance -> trunkPlacerParts(instance).and(instance.group(
                    Codec.intRange(1, 12).fieldOf("branch_length").forGetter(p -> p.branchLength),
                    Codec.intRange(1, 8).fieldOf("branches_per_layer").forGetter(p -> p.branchesPerLayer),
                    Codec.intRange(1, 6).fieldOf("layer_count").forGetter(p -> p.layerCount)
            )).apply(instance, CrepuscularBranchingTrunkPlacer::new)
    );

    private final int branchLength;
    private final int branchesPerLayer;
    private final int layerCount;

    public CrepuscularBranchingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                                            int branchLength, int branchesPerLayer, int layerCount) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchLength = branchLength;
        this.branchesPerLayer = branchesPerLayer;
        this.layerCount = layerCount;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacerTypes.BRANCHING.get();
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

        // Tronco recto simple (1x1), de la base hasta la punta.
        for (int y = 0; y < freeTreeHeight; y++) {
            placeLog(level, blockSetter, random, new BlockPos(x, baseY + y, z), config);
        }

        // Las ramas empiezan a la mitad del tronco hacia arriba, repartidas en
        // "layerCount" capas de altura distintas.
        int rangeStart = freeTreeHeight / 2;
        int rangeSize = Math.max(1, freeTreeHeight - rangeStart);
        int distBetweenLayers = Math.max(1, rangeSize / this.layerCount);

        for (int i = 0; i < this.layerCount; i++) {
            int y = rangeStart + i * distBetweenLayers + random.nextInt(2);
            if (y >= freeTreeHeight) {
                y = freeTreeHeight - 1;
            }

            BlockPos layerPos = new BlockPos(x, baseY + y, z);
            Vec3 layerStart = new Vec3(layerPos.getX() + 0.5, layerPos.getY(), layerPos.getZ() + 0.5);
            float yawOffset = random.nextFloat() * 360.0f;

            for (int b = 0; b < this.branchesPerLayer; b++) {
                float yaw = (360.0f / this.branchesPerLayer) * b + yawOffset;
                // Un poco de variación de ángulo/largo para que no se vean todas idénticas
                float pitch = 15.0f + random.nextFloat() * 25.0f;
                float length = this.branchLength * (0.7f + random.nextFloat() * 0.6f);

                Vec3 endVec = NanookMathUtil.rotationToPosition(layerStart, length, pitch, yaw);
                BlockPos endPos = new BlockPos((int) Math.round(endVec.x), (int) Math.round(endVec.y), (int) Math.round(endVec.z));

                List<int[]> points = NanookMathUtil.getBresenham3DPoints(
                        layerPos.getX(), layerPos.getY(), layerPos.getZ(),
                        endPos.getX(), endPos.getY(), endPos.getZ());

                for (int[] point : points) {
                    placeLog(level, blockSetter, random, new BlockPos(point[0], point[1], point[2]), config);
                }

                attachments.add(new FoliagePlacer.FoliageAttachment(endPos, 1, false));
            }
        }

        // Copa también justo en la punta del tronco.
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, baseY + freeTreeHeight, z), 1, false));

        return attachments;
    }
}
