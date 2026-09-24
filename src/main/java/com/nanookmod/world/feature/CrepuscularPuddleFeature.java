package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charquitos sueltos: a diferencia de CrepuscularPondFeature (que solo entra
 * DENTRO de los parches de arena/barro), esta feature aparece sobre el pasto
 * o tierra crepuscular NORMAL del bioma, en cualquier parte, para que el
 * suelo del bosque en general se sienta un poco más húmedo, no solo en las
 * zonas de pantano.
 *
 * Igual que CrepuscularPondFeature, la forma NO es un radio fijo: se calcula
 * por inundación (flood fill) célula a célula desde el centro, avanzando solo
 * mientras el terreno siga siendo pasto/tierra crepuscular y no cambie de
 * golpe de altura respecto a la celda inmediata anterior. Si el terreno no
 * acompaña, el charquito simplemente sale más chico -- nunca se toca nada
 * fuera de la forma calculada, así que no aparecen bultos de tierra
 * flotando ni charcos que no cubren donde de verdad hacía falta sellar.
 */
public class CrepuscularPuddleFeature extends Feature<NoneFeatureConfiguration> {

    // Varios intentos por invocación, igual que CrepuscularPondFeature, para
    // no depender de acertar el chunk exacto en cada llamada del placed_feature.
    private static final int TRIES = 3;
    private static final int XZ_SPREAD = 5;

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 2;

    // Charco chico: nada de saltos entre celdas vecinas.
    private static final int STEP_TOLERANCE = 1;
    private static final int MAX_HEIGHT_DIFF = 1;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
    );

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_RIVER_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
    );

    public CrepuscularPuddleFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        boolean changed = false;

        for (int i = 0; i < TRIES; i++) {
            int centerX = origin.getX() + random.nextInt(XZ_SPREAD * 2 + 1) - XZ_SPREAD;
            int centerZ = origin.getZ() + random.nextInt(XZ_SPREAD * 2 + 1) - XZ_SPREAD;

            BlockPos surfacePos = new BlockPos(centerX,
                    level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ), centerZ);

            if (!level.getBiome(surfacePos).is(BOSQUE_CREPUSCULAR_KEY)
                    && !level.getBiome(surfacePos).is(BOSQUE_CREPUSCULAR_RIVER_KEY)) {
                continue;
            }

            BlockPos belowCenter = surfacePos.below();
            BlockState belowCenterState = level.getBlockState(belowCenter);

            // Solo sobre pasto/tierra crepuscular normal: si el punto cae en un
            // parche de arena/barro, ahí ya se encarga CrepuscularPondFeature.
            boolean isNormalGround = belowCenterState.is(ModBlocks.CREPUSCULAR_GRASS.get())
                    || belowCenterState.is(ModBlocks.CREPUSCULAR_DIRT.get());
            if (!isNormalGround) {
                continue;
            }

            int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
            if (carvePuddle(level, random, centerX, centerZ, radius)) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean carvePuddle(WorldGenLevel level, RandomSource random, int centerX, int centerZ, int maxRadius) {
        int centerSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
        int waterLevel = centerSurfaceY - 1;

        Map<Long, Integer> pool = new LinkedHashMap<>();
        if (!isPuddleGround(level, centerX, centerZ, centerSurfaceY, centerSurfaceY)) {
            return false;
        }
        pool.put(key(centerX, centerZ), centerSurfaceY);

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{centerX, centerZ, centerSurfaceY});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cz = cur[1];
            int curY = cur[2];

            for (int[] d : DIRS) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long k = key(nx, nz);
                if (pool.containsKey(k)) {
                    continue;
                }

                double distFromCenter = Math.sqrt((double) (nx - centerX) * (nx - centerX) + (double) (nz - centerZ) * (nz - centerZ));
                if (distFromCenter > maxRadius) {
                    continue;
                }

                int nSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, nx, nz);
                if (!isPuddleGround(level, nx, nz, nSurfaceY, centerSurfaceY)) {
                    continue;
                }
                if (Math.abs(nSurfaceY - curY) > STEP_TOLERANCE) {
                    continue;
                }

                // Borde bien irregular (charco chico, no un círculo perfecto).
                if (distFromCenter > maxRadius - 0.6 && random.nextFloat() < 0.5F) {
                    continue;
                }

                pool.put(k, nSurfaceY);
                queue.add(new int[]{nx, nz, nSurfaceY});
            }
        }

        if (pool.size() < 2) {
            return false;
        }

        boolean placedWater = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Map.Entry<Long, Integer> entry : pool.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());

            boolean isEdge = false;
            for (int[] d : DIRS) {
                if (!pool.containsKey(key(x + d[0], z + d[1]))) {
                    isEdge = true;
                    break;
                }
            }

            if (!isEdge) {
                // Despeja lo que haya justo encima del nivel de agua (pasto alto, sprouts...).
                pos.set(x, waterLevel + 1, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }

            if (isEdge) {
                // Anillo: tierra pelada (sin pasto), como si el agua hubiera matado
                // el pasto justo alrededor del charco. Con este único bloque sólido
                // en waterLevel ya alcanza para contener el agua -- NO se agrega
                // nada encima: eso solo dejaba un bulto de tierra sobresaliendo en
                // terreno llano, donde esa celda ya era aire de por sí.
                pos.set(x, waterLevel, z);
                level.setBlock(pos, ModBlocks.CREPUSCULAR_DIRT.get().defaultBlockState(), 3);
            } else {
                pos.set(x, waterLevel, z);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                placedWater = true;

                // Igual que en CrepuscularPondFeature: forzamos el primer tick del
                // fluido para que no se quede "congelado" sin esparcirse.
                level.scheduleTick(pos.immutable(), Fluids.WATER, 1);

                // Piso: tapa cualquier hueco justo debajo del agua.
                pos.set(x, waterLevel - 1, z);
                if (isOpen(level.getBlockState(pos))) {
                    level.setBlock(pos, ModBlocks.CREPUSCULAR_DIRT.get().defaultBlockState(), 3);
                }
            }
        }

        return placedWater;
    }

    private boolean isPuddleGround(WorldGenLevel level, int x, int z, int surfaceY, int centerSurfaceY) {
        if (Math.abs(surfaceY - centerSurfaceY) > MAX_HEIGHT_DIFF) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY - 1, z);
        BlockState groundState = level.getBlockState(pos);
        return groundState.is(ModBlocks.CREPUSCULAR_GRASS.get()) || groundState.is(ModBlocks.CREPUSCULAR_DIRT.get());
    }

    private boolean isOpen(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    private static long key(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }
}