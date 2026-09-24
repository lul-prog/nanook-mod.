package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.NanookMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

/**
 * Igual que PatchFeature (Mar Primigenio), pero restringido al bioma
 * Bosque Crepuscular Y a que el bloque de debajo sea, específicamente,
 * arena o barro crepuscular (los "parches" que genera BosqueCrepuscularSurfaceRules).
 * Así las plantas de pantano solo aparecen dentro de esos parches, no en
 * cualquier punto del bioma.
 */
public class CrepuscularPatchFeature extends Feature<SimpleBlockConfiguration> {

    private static final int TRIES = 24;
    private static final int XZ_SPREAD = 6;

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
    );

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_RIVER_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
    );

    public CrepuscularPatchFeature(Codec<SimpleBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleBlockConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        SimpleBlockConfiguration config = context.config();

        boolean changed = false;

        for (int i = 0; i < TRIES; i++) {
            int x = origin.getX() + random.nextInt(XZ_SPREAD * 2 + 1) - XZ_SPREAD;
            int z = origin.getZ() + random.nextInt(XZ_SPREAD * 2 + 1) - XZ_SPREAD;

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos pos = new BlockPos(x, surfaceY, z);

            if (!level.getBiome(pos).is(BOSQUE_CREPUSCULAR_KEY) && !level.getBiome(pos).is(BOSQUE_CREPUSCULAR_RIVER_KEY)) {
                continue;
            }

            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);

            if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
                continue; // el suelo no es sólido/plano (evita agua, aire, bordes raros)
            }

            if (belowState.is(BlockTags.ICE)) {
                continue; // no crece vegetación sobre hielo
            }

            boolean isPatchGround = belowState.is(com.nanookmod.registry.ModBlocks.CREPUSCULAR_SAND.get())
                    || belowState.is(Blocks.MUD);
            if (!isPatchGround) {
                continue; // solo dentro de los parches de arena/barro, no en cualquier suelo del bioma
            }

            if (!level.getBlockState(pos).isAir()) {
                continue; // ya hay algo ahí
            }

            BlockState state = config.toPlace().getState(random, pos);
            Block block = state.getBlock();

            if (block instanceof DoublePlantBlock doublePlant) {
                if (!level.getBlockState(pos.above()).isAir()) {
                    continue; // no hay espacio para la mitad de arriba
                }
                DoublePlantBlock.placeAt(level, state, pos, 2);
            } else {
                level.setBlock(pos, state, 3);
            }

            changed = true;
        }

        return changed;
    }
}
