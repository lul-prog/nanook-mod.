package com.nanookmod.world.biome.region;

import com.mojang.datafixers.util.Pair;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBiomes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

public class BosqueCrepuscularRegion extends Region {

    public static final ResourceLocation LOCATION =
            new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_region");

    public BosqueCrepuscularRegion(int weight) {
        super(LOCATION, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {

        // Igual que MarPrimigenioRegion: se reparte en dos lóbulos de weirdness
        // (negativo y positivo) para dejar fuera la franja central -0.4..0.4,
        // así el bioma no reemplaza terreno "normal" de transición.
        //
        // A diferencia de mar_primigenio (frío extremo, terreno escarpado),
        // este nicho es templado-frío, húmedo, tierra adentro, con erosión
        // suave/media (colinas de bosque normales, no montañas).
        Climate.ParameterPoint pointNegativeWeirdness = Climate.parameters(
                Climate.Parameter.span(-0.15f, 0.65f),   // temperatura: templado-frío
                Climate.Parameter.span(0.3f, 1.0f),       // humedad: media-alta (bosque húmedo)
                Climate.Parameter.span(0.3f, 1.0f),       // continentalidad: tierra adentro
                Climate.Parameter.span(-0.4f, 0.4f),      // erosión: colinas suaves/medias
                Climate.Parameter.span(-1.0f, 1.0f),       // profundidad: toda la columna (superficie + subsuelo)
                Climate.Parameter.span(-1.0f, -0.4f),     // weirdness: lóbulo negativo extremo
                0.0f                                       // offset
        );

        Climate.ParameterPoint pointPositiveWeirdness = Climate.parameters(
                Climate.Parameter.span(-0.15f, 0.65f),
                Climate.Parameter.span(0.3f, 1.0f),
                Climate.Parameter.span(0.3f, 1.0f),
                Climate.Parameter.span(-0.4f, 0.4f),
                Climate.Parameter.span(-1.0f, 1.0f),       // profundidad: toda la columna (superficie + subsuelo)
                Climate.Parameter.span(0.4f, 1.0f),       // weirdness: lóbulo positivo extremo
                0.0f
        );

        mapper.accept(Pair.of(pointNegativeWeirdness, ModBiomes.BOSQUE_CREPUSCULAR_KEY));
        mapper.accept(Pair.of(pointPositiveWeirdness, ModBiomes.BOSQUE_CREPUSCULAR_KEY));

        // Sin esto, TerraBlender no tiene ningún punto de clima definido para
        // "río" dentro de este Region, y el sistema de vecino-más-cercano cae
        // siempre en bosque_crepuscular -> el agua del río se genera igual
        // por la altura del terreno, pero la biome ahí es bosque normal, sin
        // peces ni plantas acuáticas.
        //
        // addBiomeSimilar toma TODOS los puntos climáticos donde vanilla
        // coloca minecraft:river y los remapea a nuestro propio bioma de río.
        addBiomeSimilar(mapper, Biomes.RIVER, ModBiomes.BOSQUE_CREPUSCULAR_RIVER_KEY);
    }
}