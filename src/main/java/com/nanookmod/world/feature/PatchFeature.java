package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBlocks;
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
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

/**
 * Dispersa un "parche" de plantas alrededor del punto de origen, dentro de
 * nuestro bioma y solo sobre suelo sólido. Reutilizable tanto para plantas
 * de 1 bloque (bushes, pasto simple) como de 2 bloques (double plants):
 * si el bloque es un DoublePlantBlock, usa su método placeAt() para colocar
 * las dos mitades correctamente en vez del feature genérico de vanilla
 * (que solo coloca la mitad de abajo y deja la planta rota).
 */
public class PatchFeature extends Feature<SimpleBlockConfiguration> {

    private static final int TRIES = 24;
    private static final int XZ_SPREAD = 6;

    private static final ResourceKey<Biome> MAR_PRIMIGENIO_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio")
    );

    public PatchFeature(Codec<SimpleBlockConfiguration> codec) {
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

            if (!level.getBiome(pos).is(MAR_PRIMIGENIO_KEY)) {
                continue;
            }

            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);

            if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
                continue; // el suelo no es sólido/plano (evita agua, aire, bordes raros)
            }

            if (belowState.is(BlockTags.ICE) || belowState.is(ModBlocks.FROST_ICE.get())) {
                continue; // no crece vegetación sobre hielo (vanilla o frost_ice)
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
