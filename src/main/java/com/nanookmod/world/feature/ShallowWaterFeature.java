package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Recorre cada columna del chunk que pertenezca a nuestro bioma y, si encuentra
 * un cuerpo de agua (lago/río), lo rellena con FROST_DIRT hasta dejar solo
 * MAX_WATER_DEPTH bloques de agua en superficie.
 *
 * Para evitar un muro vertical brusco en el borde del bioma, el relleno se
 * "difumina": cerca del límite del bioma se rellena poco (o nada), y a medida
 * que nos alejamos del borde (hasta TRANSITION_RADIUS bloques) el relleno
 * llega al máximo. Esto crea una rampa en vez de un escalón.
 */
public class ShallowWaterFeature extends Feature<NoneFeatureConfiguration> {

    // Cuántos bloques de agua queremos dejar en la superficie en el interior del bioma.
    private static final int MAX_WATER_DEPTH = 1;

    // Distancia (en bloques) sobre la que se difumina el relleno cerca del borde del bioma.
    private static final int TRANSITION_RADIUS = 6;

    private static final ResourceKey<Biome> MAR_PRIMIGENIO_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio")
    );

    public ShallowWaterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunkPos = new ChunkPos(context.origin());

        boolean changedAny = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                BlockPos checkPos = new BlockPos(worldX, level.getSeaLevel(), worldZ);
                if (!level.getBiome(checkPos).is(MAR_PRIMIGENIO_KEY)) {
                    continue;
                }

                int distanceToBorder = distanceToBorder(level, worldX, worldZ, level.getSeaLevel());

                if (processColumn(level, worldX, worldZ, distanceToBorder)) {
                    changedAny = true;
                }
            }
        }

        return changedAny;
    }

    /**
     * Distancia mínima (en bloques, hasta TRANSITION_RADIUS) hacia un punto
     * donde el bioma ya no es el nuestro, buscando en las 4 direcciones cardinales.
     * Si no encuentra borde dentro del radio, devuelve TRANSITION_RADIUS (bien adentro).
     */
    private int distanceToBorder(WorldGenLevel level, int x, int z, int sampleY) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int minDist = TRANSITION_RADIUS;

        for (int[] dir : dirs) {
            for (int d = 1; d <= TRANSITION_RADIUS; d++) {
                BlockPos checkPos = new BlockPos(x + dir[0] * d, sampleY, z + dir[1] * d);
                if (!level.getBiome(checkPos).is(MAR_PRIMIGENIO_KEY)) {
                    minDist = Math.min(minDist, d - 1);
                    break;
                }
            }
        }

        return minDist;
    }

    /**
     * @return true si se modificó algún bloque en esta columna
     */
    private boolean processColumn(WorldGenLevel level, int x, int z, int distanceToBorder) {
        int topY = level.getMaxBuildHeight() - 1;
        int bottomY = level.getMinBuildHeight();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. Buscamos de arriba hacia abajo la superficie del agua (primer bloque de agua).
        int waterTop = Integer.MIN_VALUE;
        for (int y = topY; y >= bottomY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) {
                waterTop = y;
                break;
            }
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return false; // no hay agua en esta columna
            }
        }

        if (waterTop == Integer.MIN_VALUE) {
            return false;
        }

        // 2. Buscamos el fondo real (primer bloque sólido debajo del agua).
        int realBottom = bottomY;
        for (int y = waterTop; y >= bottomY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                realBottom = y + 1;
                break;
            }
        }

        int originalDepth = waterTop - realBottom + 1;
        if (originalDepth <= MAX_WATER_DEPTH) {
            return false; // ya es poco profundo, no hay nada que rellenar
        }

        // 3. Calculamos la profundidad objetivo según qué tan lejos estamos del borde.
        //    t=0 en el borde (no tocar nada) -> t=1 bien adentro (relleno máximo).
        double t = distanceToBorder / (double) TRANSITION_RADIUS;
        int desiredDepth = originalDepth - (int) Math.round((originalDepth - MAX_WATER_DEPTH) * t);
        desiredDepth = Math.max(MAX_WATER_DEPTH, Math.min(originalDepth, desiredDepth));

        if (desiredDepth >= originalDepth) {
            return false; // en el borde: no rellenamos, dejamos la profundidad natural
        }

        // 4. Rellenamos desde (waterTop - desiredDepth) hacia abajo hasta el fondo real,
        //    dejando intactos los "desiredDepth" bloques de agua de la superficie.
        BlockState fill = ModBlocks.FROST_DIRT.get().defaultBlockState();
        boolean changed = false;
        int fillFromY = waterTop - desiredDepth;

        for (int y = fillFromY; y >= realBottom; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (state.getFluidState().is(FluidTags.WATER)) {
                level.setBlock(pos, fill, 3);
                changed = true;
            }
        }

        return changed;
    }
}