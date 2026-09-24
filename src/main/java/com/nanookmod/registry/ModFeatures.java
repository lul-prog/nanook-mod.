package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.world.feature.*;
import com.nanookmod.world.feature.trunk.CrepuscularScatterFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, NanookMod.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SHALLOW_WATER_FILL =
            FEATURES.register("shallow_water_fill",
                    () -> new ShallowWaterFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> FROST_FREEZE =
            FEATURES.register("frost_freeze",
                    () -> new FrostFreezeFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BIG_STONE_PILE =
            FEATURES.register("big_stone_pile",
                    () -> new BigStonePileFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GIANT_ICE_SPIKE =
            FEATURES.register("giant_ice_spike",
                    () -> new GiantIceSpikeFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BROKEN_TRUNK =
            FEATURES.register("broken_trunk",
                    () -> new BrokenTrunkFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<CustomTreeConfiguration>> TWISTED_SPRUCE_CUSTOM =
            FEATURES.register("twisted_spruce_custom_feature",
                    () -> new CustomTreeStructureFeature(CustomTreeConfiguration.CODEC));

    public static final RegistryObject<Feature<SimpleBlockConfiguration>> PATCH_FEATURE =
            FEATURES.register("patch_feature",
                    () -> new PatchFeature(SimpleBlockConfiguration.CODEC));

    public static final RegistryObject<Feature<SimpleBlockConfiguration>> CREPUSCULAR_PATCH_FEATURE =
            FEATURES.register("crepuscular_patch_feature",
                    () -> new CrepuscularPatchFeature(SimpleBlockConfiguration.CODEC));
    public static final RegistryObject<Feature<SimpleBlockConfiguration>> CREPUSCULAR_SCATTER_FEATURE =

            FEATURES.register("crepuscular_scatter_feature",
                    () -> new CrepuscularScatterFeature(SimpleBlockConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CREPUSCULAR_POND =
            FEATURES.register("crepuscular_pond",
                    () -> new CrepuscularPondFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CREPUSCULAR_PUDDLE =
            FEATURES.register("crepuscular_puddle",
                    () -> new CrepuscularPuddleFeature(NoneFeatureConfiguration.CODEC));
}