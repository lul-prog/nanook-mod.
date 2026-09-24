package com.nanookmod.world.feature.foliage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nanookmod.registry.ModFoliagePlacerTypes;
import com.nanookmod.util.NanookMathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * Copa en forma de elipsoide (ovalada/redondeada), adaptada del enfoque que
 * usa Eternal Starlight para su árbol lunar.
 * Nota de versión: en 1.20.1 (mapeos oficiales que usa Forge 47.2.0), el
 * método createFoliage recibe un FoliagePlacer.FoliageSetter en vez de un
 * BiConsumer<BlockPos,BlockState> directo (eso cambió respecto a versiones
 * más viejas). Confirmado por el propio error de compilación de este
 * proyecto, que expuso la firma exacta.
 */
public class CrepuscularSpheroidFoliagePlacer extends FoliagePlacer {

    public static final Codec<CrepuscularSpheroidFoliagePlacer> CODEC = RecordCodecBuilder.create(
            instance -> foliagePlacerParts(instance).apply(instance, CrepuscularSpheroidFoliagePlacer::new)
    );

    public CrepuscularSpheroidFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModFoliagePlacerTypes.SPHEROID.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter blockSetter,
                                 RandomSource random, TreeConfiguration config, int maxFreeTreeHeight,
                                 FoliageAttachment attachment, int foliageHeight, int radius, int offset) {
        BlockPos center = attachment.pos().above(offset);
        float xzRadius = attachment.radiusOffset() + this.radius.sample(random);
        float yRadius = attachment.radiusOffset() + 1.5f + random.nextInt(2);
        placeSpheroidFoliage(level, blockSetter, random, config, center, xzRadius, yRadius);
    }

    private static void placeSpheroidFoliage(LevelSimulatedReader level, FoliageSetter blockSetter,
                                             RandomSource random, TreeConfiguration config, BlockPos center,
                                             float xzRadius, float yRadius) {
        for (int x = 0; (float) x <= xzRadius; x++) {
            for (int z = 0; (float) z <= xzRadius; z++) {
                for (int y = 0; (float) y <= yRadius; y++) {
                    if (NanookMathUtil.isPointInOrOnEllipsoid(x, y, z, xzRadius, yRadius, xzRadius)) {
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(x, y, z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(x, -y, z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(-x, y, z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(-x, -y, z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(x, y, -z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(x, -y, -z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(-x, y, -z));
                        placeFoliageCluster(level, blockSetter, random, config, center.offset(-x, -y, -z));
                    }
                }
            }
        }
    }

    private static void placeFoliageCluster(LevelSimulatedReader level, FoliageSetter blockSetter,
                                            RandomSource random, TreeConfiguration config, BlockPos pos) {
        // Aquí llamamos al método renombrado
        if (tryPlaceLeafCustom(level, blockSetter, random, config, pos)) {
            for (Direction direction : Direction.values()) {
                if (random.nextInt(5) == 0) {
                    tryPlaceLeafCustom(level, blockSetter, random, config, pos.relative(direction));
                }
            }
        }
    }

    // MÉTODO CORREGIDO: Renombrado para evitar colisión con el tryPlaceLeaf de la clase padre FoliagePlacer
    private static boolean tryPlaceLeafCustom(LevelSimulatedReader level, FoliageSetter blockSetter,
                                              RandomSource random, TreeConfiguration config, BlockPos pos) {
        if (!TreeFeature.validTreePos(level, pos)) {
            return false;
        }
        blockSetter.set(pos, config.foliageProvider.getState(random, pos));
        return true;
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return false;
    }
}