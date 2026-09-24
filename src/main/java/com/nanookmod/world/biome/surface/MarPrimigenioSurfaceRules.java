package com.nanookmod.world.biome.surface;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;

public class MarPrimigenioSurfaceRules {

    public static SurfaceRules.RuleSource makeRules() {
        ResourceKey<Biome> BIOME_KEY = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio")
        );

        SurfaceRules.ConditionSource isMarPrimigenio = SurfaceRules.isBiome(BIOME_KEY);

        // EVITAR AGUA: waterBlockCheck ya devuelve true cuando la columna está SECA
        // (no hay fluido). NO hay que envolverlo en SurfaceRules.not(), o se invierte
        // la lógica y las reglas solo se aplican bajo el agua.
        SurfaceRules.ConditionSource notUnderwater = SurfaceRules.waterBlockCheck(-1, 0);

        // EVITAR CUEVAS/MINAS: ON_FLOOR y UNDER_FLOOR se activan en CUALQUIER
        // transición aire->piedra al escanear la columna (techos de cuevas, paredes
        // de barrancos, salientes flotantes...), no solo en la superficie real.
        // abovePreliminarySurface() restringe las reglas a la altura real del terreno,
        // igual que hace vanilla en SurfaceRuleData.
        SurfaceRules.ConditionSource aboveSurface = SurfaceRules.abovePreliminarySurface();

        // Frost Grass: superficie terrestre
        SurfaceRules.RuleSource frostGrass = SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.state(ModBlocks.FROST_GRASS.get().defaultBlockState())
        );

        // Frost Dirt: 1-3 bloques abajo
        SurfaceRules.RuleSource frostDirt = SurfaceRules.ifTrue(
                SurfaceRules.UNDER_FLOOR,
                SurfaceRules.state(ModBlocks.FROST_DIRT.get().defaultBlockState())
        );

        SurfaceRules.RuleSource lakeBed = SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.state(net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState())
        );

        SurfaceRules.RuleSource lakeBed2 = SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.state(Blocks.SAND.defaultBlockState())
        );

        // Aplicar SOLO si: está sobre la superficie real (no en cuevas) Y es el bioma Y no está bajo agua
        return SurfaceRules.ifTrue(
                aboveSurface,
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(
                                isMarPrimigenio,
                                SurfaceRules.ifTrue(
                                        notUnderwater,
                                        frostGrass
                                )
                        ),
                        SurfaceRules.ifTrue(
                                isMarPrimigenio,
                                SurfaceRules.ifTrue(
                                        notUnderwater,
                                        frostDirt
                                )
                        )
                )
        );
    }
}