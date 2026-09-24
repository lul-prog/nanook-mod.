package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.NanookMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Congela el agua expuesta al cielo con hielo vanilla, y pone capas de nieve
 * (vanilla, sin bloque propio) sobre bloques sólidos expuestos, con una
 * profundidad que varía suavemente en el espacio (ver smoothNoise) para
 * formar ventisqueros en vez de puntitos al azar.
 *
 * Reemplaza por completo a "minecraft:freeze_top_layer" en el bioma (no se
 * usan los dos juntos, para no competir por la misma columna).
 */
public class FrostFreezeFeature extends Feature<NoneFeatureConfiguration> {

    // DIAGNÓSTICO TEMPORAL: en false, no coloca capas de nieve sobre el suelo expuesto
    // (el hielo sobre agua expuesta sigue funcionando igual). Solo afecta CHUNKS NUEVOS,
    // generados después de este cambio - los chunks ya generados conservan su nieve.
    // Volver a true una vez terminada la prueba de spawn día/noche.
    private static final boolean PLACE_SNOW_LAYERS = true;

    // Cuántas capas de nieve (1-8) se colocan como mínimo/máximo, para dar más relieve.
    private static final int MIN_SNOW_LAYERS = 1;
    private static final int MAX_SNOW_LAYERS = 1;

    private static final ResourceKey<Biome> MAR_PRIMIGENIO_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio")
    );

    public FrostFreezeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunkPos = new ChunkPos(context.origin());
        RandomSource random = context.random();

        boolean changedAny = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                BlockPos surfacePos = new BlockPos(worldX, topY, worldZ);
                BlockPos belowPos = surfacePos.below();

                if (!level.getBiome(belowPos).is(MAR_PRIMIGENIO_KEY)) {
                    continue;
                }

                if (!level.getBlockState(surfacePos).isAir()) {
                    continue; // no está expuesto al cielo
                }

                BlockState belowState = level.getBlockState(belowPos);

                // Agua expuesta al cielo -> hielo vanilla (frost_ice ahora es un
                // parche decorativo dentro de las montañas, no el hielo del mar).
                if (belowState.getFluidState().is(FluidTags.WATER) && belowState.getFluidState().isSource()) {
                    level.setBlock(belowPos, Blocks.ICE.defaultBlockState(), 3);
                    changedAny = true;
                }
                // Bloque sólido expuesto -> capa de nieve encima (vanilla, sin bloque propio),
                // salvo que sea uno de los bloques que usan los cúmulos de piedra
                // (cobblestone/stone/andesite quedan pelados, como roca expuesta).
                else if (PLACE_SNOW_LAYERS
                        && belowState.isSolid()
                        && !isPileBlock(belowState)
                        && Blocks.SNOW.defaultBlockState().canSurvive(level, surfacePos)) {
                    double n = smoothNoise(worldX, worldZ);
                    int layers = MIN_SNOW_LAYERS
                            + (int) Math.round(n * (MAX_SNOW_LAYERS - MIN_SNOW_LAYERS));
                    BlockState snowState = Blocks.SNOW.defaultBlockState()
                            .setValue(SnowLayerBlock.LAYERS, layers);
                    level.setBlock(surfacePos, snowState, 3);
                    changedAny = true;
                }
            }
        }

        return changedAny;
    }

    // Los mismos bloques que usa BigStonePileFeature para los cúmulos.
    private static boolean isPileBlock(BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE);
    }

    // ------------------------------------------------------------------
    // Ruido de valor suavizado (determinístico, sin dependencias externas).
    // Da profundidades de nieve que varían gradualmente en el espacio en
    // vez de saltar al azar bloque a bloque, formando "ventisqueros".
    // ------------------------------------------------------------------

    // Distancia (en bloques) entre picos/valles del ventisquero. Más alto = dunas más anchas.
    private static final int NOISE_PERIOD = 10;

    private static double smoothNoise(int worldX, int worldZ) {
        double gx = worldX / (double) NOISE_PERIOD;
        double gz = worldZ / (double) NOISE_PERIOD;

        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double sx = smoothstep(gx - x0);
        double sz = smoothstep(gz - z0);

        double n00 = gridValue(x0, z0);
        double n10 = gridValue(x1, z0);
        double n01 = gridValue(x0, z1);
        double n11 = gridValue(x1, z1);

        double nx0 = lerp(n00, n10, sx);
        double nx1 = lerp(n01, n11, sx);
        return lerp(nx0, nx1, sz);
    }

    private static double gridValue(int gx, int gz) {
        long h = gx * 374761393L + gz * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }
}