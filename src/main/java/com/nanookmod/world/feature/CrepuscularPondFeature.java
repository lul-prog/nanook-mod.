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
 * Mini lagunas del Bosque Crepuscular: un charco poco profundo (1 bloque de
 * agua) con un anillo de barro alrededor, que solo aparece DENTRO de los
 * parches de arena/barro (nunca sobre pasto/tierra normal del bioma).
 *
 * La forma del charco NO es un círculo fijo: se calcula por inundación
 * (flood fill) a partir del centro, avanzando celda a celda solo mientras la
 * celda vecina siga siendo terreno de parche y su altura no cambie de golpe
 * respecto a la celda inmediata anterior (STEP_TOLERANCE). Eso es justo lo
 * que hace que la forma se adapte sola al terreno real: en un parche llano
 * crece más o menos redonda, y si se topa con un acantilado o un borde
 * irregular simplemente deja de crecer ahí -- nunca se toca ni se "tapa"
 * nada fuera de la forma calculada, así que no quedan bloques flotando ni
 * un anillo de barro desproporcionado quando el charco real terminó siendo
 * chico.
 */
public class CrepuscularPondFeature extends Feature<NoneFeatureConfiguration> {

    // Cuántos intentos de encontrar un buen centro por invocación (la invocación
    // en sí ya está limitada por rarity_filter en el placed_feature, así que esto
    // solo decide cuántas lagunas caben dentro de un mismo intento "afortunado").
    private static final int TRIES = 6;

    // Radio MÁXIMO que puede alcanzar la laguna desde el centro (tope de la
    // inundación, no un círculo garantizado -- el resultado real puede ser
    // más chico si el terreno no acompaña).
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 6;

    // Cuánto puede variar el terreno de una celda respecto a la INMEDIATA
    // anterior para que la inundación siga avanzando por ahí. Esto es lo que
    // frena la forma en un acantilado (un salto de más de esto = no se cruza).
    private static final int STEP_TOLERANCE = 1;

    // Tope total de variación respecto al CENTRO (además del paso a paso), para
    // que el agua siga quedando razonablemente plana aunque el camino haya sido
    // gradual.
    private static final int MAX_HEIGHT_DIFF = 2;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
    );

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_RIVER_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
    );

    public CrepuscularPondFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        boolean changed = false;

        for (int i = 0; i < TRIES; i++) {
            int centerX = origin.getX() + random.nextInt(9) - 4;
            int centerZ = origin.getZ() + random.nextInt(9) - 4;

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
            BlockPos centerPos = new BlockPos(centerX, surfaceY, centerZ);

            if (!level.getBiome(centerPos).is(BOSQUE_CREPUSCULAR_KEY) && !level.getBiome(centerPos).is(BOSQUE_CREPUSCULAR_RIVER_KEY)) {
                continue;
            }

            BlockPos belowCenter = centerPos.below();
            BlockState belowCenterState = level.getBlockState(belowCenter);

            boolean isPatchGround = belowCenterState.is(ModBlocks.CREPUSCULAR_SAND.get())
                    || belowCenterState.is(Blocks.MUD);
            if (!isPatchGround) {
                continue; // el centro tiene que caer dentro del parche de arena/barro
            }

            int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
            if (carvePond(level, random, centerX, centerZ, radius)) {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * @return true si se colocó al menos un bloque de agua (charco válido).
     */
    private boolean carvePond(WorldGenLevel level, RandomSource random, int centerX, int centerZ, int maxRadius) {
        int centerSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
        int waterLevel = centerSurfaceY - 1;

        // --- 1. Calculamos la forma real por inundación. "pool" termina
        // conteniendo SOLO las celdas por las que de verdad se puede crecer un
        // charco; todo lo que quede fuera de este mapa nunca se toca.
        Map<Long, Integer> pool = new LinkedHashMap<>();
        if (!isPondGround(level, centerX, centerZ, centerSurfaceY, centerSurfaceY)) {
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
                if (!isPondGround(level, nx, nz, nSurfaceY, centerSurfaceY)) {
                    continue;
                }
                if (Math.abs(nSurfaceY - curY) > STEP_TOLERANCE) {
                    continue; // salto brusco entre vecinos: acá se frena (acantilado, borde)
                }

                // Borde irregular: cerca del radio máximo, un poco de azar para que
                // no quede un círculo perfecto.
                if (distFromCenter > maxRadius - 1.0 && random.nextFloat() < 0.35F) {
                    continue;
                }

                pool.put(k, nSurfaceY);
                queue.add(new int[]{nx, nz, nSurfaceY});
            }
        }

        if (pool.size() < 2) {
            return false; // ni para un charquito de verdad alcanzó
        }

        // --- 2. Coloreamos: agua adentro, muro de contención en el borde real
        // (celdas del charco que tienen al menos un vecino que NO forma parte
        // de "pool"). Nada fuera de "pool" se toca jamás.
        boolean placedWater = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Map.Entry<Long, Integer> entry : pool.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            int localSurfaceY = entry.getValue();

            boolean isEdge = false;
            for (int[] d : DIRS) {
                if (!pool.containsKey(key(x + d[0], z + d[1]))) {
                    isEdge = true;
                    break;
                }
            }

            int diff = localSurfaceY - centerSurfaceY;

            if (diff > 0) {
                // El terreno local es más alto que el centro: despejamos el sobrante.
                for (int y = localSurfaceY; y > waterLevel; y--) {
                    pos.set(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            } else if (diff < 0) {
                // El terreno local es más bajo: rellenamos con barro hasta emparejar.
                for (int y = localSurfaceY; y < waterLevel; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, Blocks.MUD.defaultBlockState(), 3);
                }
            }

            if (!isEdge) {
                // Interior: despejamos encima del agua (plantas, ramas bajas...).
                for (int y = waterLevel + 1; y <= waterLevel + 2; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }

            pos.set(x, waterLevel, z);
            level.setBlock(pos, isEdge ? Blocks.MUD.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);

            // Nota: NO se agrega ningún bloque extra encima del nivel del agua en
            // el borde. Con el barro sólido que ya se puso en waterLevel alcanza
            // para contener el agua (no puede "saltar" hacia arriba y salirse por
            // encima) -- agregar algo ahí solo servía para dejar un bulto de barro
            // sobresaliendo en terreno llano, donde esa celda ya era aire de por sí.
            if (!isEdge) {
                // Piso bajo el agua: barro, para que no se transparente arena ni
                // quede un hueco justo debajo de la superficie.
                pos.set(x, waterLevel - 1, z);
                BlockState underState = level.getBlockState(pos);
                if (underState.is(ModBlocks.CREPUSCULAR_SAND.get()) || underState.isAir()) {
                    level.setBlock(pos, Blocks.MUD.defaultBlockState(), 3);
                }
                placedWater = true;

                // Programamos explícitamente el tick del fluido: colocarlo con
                // setBlock durante la generación no siempre dispara el primer
                // esparcido por sí solo, y el resultado son bloques de agua
                // "congelados" hasta que algo los golpea manualmente.
                pos.set(x, waterLevel, z);
                level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
            }
        }

        return placedWater;
    }

    private boolean isPondGround(WorldGenLevel level, int x, int z, int surfaceY, int centerSurfaceY) {
        if (Math.abs(surfaceY - centerSurfaceY) > MAX_HEIGHT_DIFF) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY - 1, z);
        BlockState groundState = level.getBlockState(pos);
        return groundState.is(ModBlocks.CREPUSCULAR_SAND.get()) || groundState.is(Blocks.MUD);
    }

    private static long key(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }
}