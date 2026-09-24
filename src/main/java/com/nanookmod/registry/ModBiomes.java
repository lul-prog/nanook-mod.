package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class ModBiomes {
    public static final ResourceKey<Biome> MAR_PRIMIGENIO_KEY =
            ResourceKey.create(
                    Registries.BIOME,
                    new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio")
            );

    public static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_KEY =
            ResourceKey.create(
                    Registries.BIOME,
                    new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
            );

    public static final ResourceKey<Biome> BOSQUE_CREPUSCULAR_RIVER_KEY =
            ResourceKey.create(
                    Registries.BIOME,
                    new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
            );
}