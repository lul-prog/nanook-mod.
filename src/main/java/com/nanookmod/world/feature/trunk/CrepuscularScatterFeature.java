package com.nanookmod.world.feature.trunk;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

/**
 * A diferencia de CrepuscularPatchFeature (que agrupa varias plantas
 * alrededor de un mismo punto, tipo "parche"), este feature coloca UN solo
 * bloque por invocación en la posición que le pasa el placement. Usado
 * junto con el placement "minecraft:count" (que llama al feature N veces
 * por chunk, cada vez con una posición X/Z independiente dentro del
 * chunk), el resultado es un reparto parejo por todo el bioma en vez de
 * cúmulos.
 *
 * Válido tanto en Bosque Crepuscular como en su variante de río.
 */
public class CrepuscularScatterFeature extends Feature<SimpleBlockConfiguration> {

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
    );

    private static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_RIVER_KEY = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
    );

    public CrepuscularScatterFeature(Codec<SimpleBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleBlockConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        SimpleBlockConfiguration config = context.config();

        int x = origin.getX();
        int z = origin.getZ();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos pos = new BlockPos(x, surfaceY, z);

        if (!level.getBiome(pos).is(BOSQUE_CREPUSCULAR_KEY) && !level.getBiome(pos).is(BOSQUE_CREPUSCULAR_RIVER_KEY)) {
            return false;
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            return false; // el suelo no es sólido/plano (evita agua, aire, bordes raros)
        }

        if (belowState.is(BlockTags.ICE)) {
            return false; // no crece vegetación sobre hielo
        }

        boolean isPatchGround = belowState.is(com.nanookmod.registry.ModBlocks.CREPUSCULAR_SAND.get())
                || belowState.is(net.minecraft.world.level.block.Blocks.MUD);
        if (isPatchGround) {
            return false; // los parches de arena/barro (y la orilla de las lagunas) tienen
            // sus propias plantas via CrepuscularPatchFeature, no pasto vainilla
        }

        if (!level.getBlockState(pos).isAir()) {
            return false; // ya hay algo ahí
        }

        BlockState state = config.toPlace().getState(random, pos);
        level.setBlock(pos, state, 3);
        return true;
    }
}
