package com.nanookmod.world.biome.region;

import com.mojang.datafixers.util.Pair;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBiomes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

public class MarPrimigenioRegion extends Region {

    public static final ResourceLocation LOCATION =
            new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio_region");

    public MarPrimigenioRegion(int weight) {
        super(LOCATION, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {

        // TerraBlender automáticamente mezcla con biomas vanilla
        // Solo necesitamos añadir nuestro bioma con los parámetros correctos

        // Orden correcto: temperature, humidity, continentalness, erosion, DEPTH, WEIRDNESS, offset
        //
        // El weirdness cercano a 0 da terreno "intermedio" incluso con erosión escarpada,
        // así que lo partimos en dos franjas separadas (negativa y positiva) dejando fuera
        // el centro -0.4..0.4, que es lo que suaviza los picos.
        Climate.ParameterPoint pointNegativeWeirdness = Climate.parameters(
                Climate.Parameter.span(-1.0f, -0.45f),  // temperatura: muy fría (zona frozen completa de vanilla)
                Climate.Parameter.span(-1.0f, 0.1f),     // humedad: baja-media (tundra)
                Climate.Parameter.span(0.3f, 1.0f),      // continentalidad: tierra adentro (evita zona de costa)
                Climate.Parameter.span(-1.0f, -0.7f),    // erosión: banda más escarpada (Jagged Peaks)
                Climate.Parameter.span(-1.0f, 1.0f),      // profundidad: toda la columna (superficie + subsuelo)
                Climate.Parameter.span(-1.0f, -0.4f),    // weirdness: lóbulo negativo extremo
                0.0f                                      // offset
        );

        Climate.ParameterPoint pointPositiveWeirdness = Climate.parameters(
                Climate.Parameter.span(-1.0f, -0.45f),
                Climate.Parameter.span(-1.0f, 0.1f),
                Climate.Parameter.span(0.3f, 1.0f),
                Climate.Parameter.span(-1.0f, -0.7f),
                Climate.Parameter.span(-1.0f, 1.0f),      // profundidad: toda la columna (superficie + subsuelo)
                Climate.Parameter.span(0.4f, 1.0f),      // weirdness: lóbulo positivo extremo
                0.0f
        );

        mapper.accept(Pair.of(pointNegativeWeirdness, ModBiomes.MAR_PRIMIGENIO_KEY));
        mapper.accept(Pair.of(pointPositiveWeirdness, ModBiomes.MAR_PRIMIGENIO_KEY));
    }
}