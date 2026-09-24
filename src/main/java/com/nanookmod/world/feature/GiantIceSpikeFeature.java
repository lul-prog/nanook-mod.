package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.registry.ModBlocks;
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
 * Pico de hielo gigante: una columna que se va angostando hacia arriba, con
 * bordes irregulares (no un cono perfecto), hecha principalmente de
 * FROST_ICE con algunos bloques de packed_ice/blue_ice vanilla mezclados
 * para dar variedad visual, como en la referencia. A veces también genera
 * 1-2 picos más chicos pegados al principal, para el efecto de "racimo" de
 * picos que se ve en las imágenes.
 */
public class GiantIceSpikeFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 14;
    private static final int MAX_HEIGHT = 32;
    private static final int MIN_BASE_RADIUS = 2;
    private static final int MAX_BASE_RADIUS = 4;

    public GiantIceSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
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

        boolean changed = placeSpike(level, random, worldX, surfaceY, worldZ,
                MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1),
                MIN_BASE_RADIUS + random.nextInt(MAX_BASE_RADIUS - MIN_BASE_RADIUS + 1));

        // Chance de 1-2 picos más chicos "pegados" al principal, para el look
        // de racimo/cluster que se ve en la referencia (varios picos juntos
        // de distinto tamaño, no uno solo aislado).
        int companions = random.nextFloat() < 0.5f ? (random.nextFloat() < 0.3f ? 2 : 1) : 0;
        for (int i = 0; i < companions; i++) {
            int offsetX = worldX + (random.nextInt(5) - 2) + (random.nextBoolean() ? 3 : -3);
            int offsetZ = worldZ + (random.nextInt(5) - 2) + (random.nextBoolean() ? 3 : -3);
            int companionSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, offsetX, offsetZ) - 1;

            int companionHeight = (int) ((MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1)) * 0.5);
            int companionRadius = Math.max(1, MIN_BASE_RADIUS - 1 + random.nextInt(2));

            changed |= placeSpike(level, random, offsetX, companionSurfaceY, offsetZ, companionHeight, companionRadius);
        }

        return changed;
    }

    private boolean placeSpike(WorldGenLevel level, RandomSource random, int baseX, int baseY, int baseZ,
                               int height, int baseRadius) {
        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = 0; y < height; y++) {
            // El radio se va achicando conforme sube, hasta terminar casi en
            // punta arriba (no en un cono matemáticamente perfecto: le
            // metemos ruido para que se vea tallado/irregular).
            double t = y / (double) height;
            double radius = baseRadius * (1.0 - t) + 0.4;
            int r = (int) Math.ceil(radius);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius) continue;

                    // Borde irregular, como una talla natural de hielo, no un
                    // cilindro/cono perfecto.
                    if (dist > radius - 0.6 && random.nextFloat() < 0.4f) continue;

                    pos.setWithOffset(new BlockPos(baseX, baseY, baseZ), dx, y, dz);
                    BlockState current = level.getBlockState(pos);

                    // Solo construimos sobre aire/nieve/hielo/agua para no
                    // destruir terreno sólido de otro tipo.
                    if (!current.isAir() && !current.is(Blocks.SNOW) && !current.is(Blocks.SNOW_BLOCK)
                            && current.getFluidState().isEmpty()
                            && !current.is(ModBlocks.FROST_ICE.get())) {
                        continue;
                    }

                    level.setBlock(pos, pickIceBlock(random), 3);
                    changed = true;
                }
            }
        }

        return changed;
    }

    /**
     * Sobre todo FROST_ICE (el bloque propio del mod), con un toque de
     * packed_ice/blue_ice vanilla mezclados para dar variedad visual, como
     * pediste ("quizás otros tipos de hielo vanilla").
     */
    private BlockState pickIceBlock(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.75f) {
            return ModBlocks.FROST_ICE.get().defaultBlockState();
        } else if (roll < 0.92f) {
            return Blocks.PACKED_ICE.defaultBlockState();
        } else {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
    }
}
