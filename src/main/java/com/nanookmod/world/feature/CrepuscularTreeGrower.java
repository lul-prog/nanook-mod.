package com.nanookmod.world.feature;

import com.nanookmod.NanookMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import javax.annotation.Nullable;

/**
 * "Generador" del árbol crepuscular para el sapling.
 *
 * En 1.20.1 AbstractTreeGrower ya NO recibe el mundo/posición: solo debe
 * devolver la ResourceKey del configured_feature a colocar (Mojang cambió
 * esto en 1.19.3). No hace falta ninguna Holder ni consulta a registries
 * aquí, basta con apuntar al mismo configured_feature que ya usa la
 * placed_feature "crepuscular_tree" para la generación natural del mundo
 * (data/nanookmod/worldgen/configured_feature/crepuscular_tree.json).
 */
public class CrepuscularTreeGrower extends AbstractTreeGrower {

    public static final ResourceKey<ConfiguredFeature<?, ?>> CREPUSCULAR_TREE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    new ResourceLocation(NanookMod.MOD_ID, "crepuscular_tree"));

    @Nullable
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return CREPUSCULAR_TREE_KEY;
    }
}
