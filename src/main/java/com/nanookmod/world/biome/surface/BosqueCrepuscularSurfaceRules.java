package com.nanookmod.world.biome.surface;

import com.nanookmod.NanookMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;

public class BosqueCrepuscularSurfaceRules {

    public static SurfaceRules.RuleSource makeRules() {
        ResourceKey<Biome> BIOME_KEY = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular")
        );

        ResourceKey<Biome> RIVER_BIOME_KEY = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation(NanookMod.MOD_ID, "bosque_crepuscular_river")
        );

        // isBiome acepta varios biomas: así el río también recibe el pasto/tierra
        // custom en sus orillas y tramos secos, en vez de caer en el grass/dirt vanilla.
        SurfaceRules.ConditionSource isBosqueCrepuscular = SurfaceRules.isBiome(BIOME_KEY, RIVER_BIOME_KEY);

        // Igual que en mar_primigenio: waterBlockCheck devuelve true cuando la
        // columna está SECA, no hay que envolverlo en not().
        SurfaceRules.ConditionSource notUnderwater = SurfaceRules.waterBlockCheck(-1, 0);

        // Restringe las reglas a la altura real del terreno (evita cuevas/barrancos).
        SurfaceRules.ConditionSource aboveSurface = SurfaceRules.abovePreliminarySurface();

        // Pasto Crepuscular (PLACEHOLDER de color, ver ModBlocks.CREPUSCULAR_GRASS):
        // superficie terrestre del bioma. Antes era Podzol; se deja comentado por si
        // quieres volver atras rapido.
        SurfaceRules.RuleSource podzolTop = SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.state(com.nanookmod.registry.ModBlocks.CREPUSCULAR_GRASS.get().defaultBlockState())
        );

        // Parches de arena en tierra firme: mismo truco que usa vanilla para mezclar
        // podzol/coarse_dirt en la taiga gigante -- un ruido (Noises.SURFACE) decide,
        // sin necesidad de ningun feature/JSON nuevo, si la columna entera (arriba Y
        // abajo) es arena en vez de pasto/tierra. El rango 0.7..1.0 cubre aprox. un
        // 15% del terreno (parches, no todo el bioma). Ajusta estos dos numeros para
        // mas o menos arena.
        SurfaceRules.ConditionSource sandNoise = SurfaceRules.noiseCondition(Noises.SURFACE, 0.3D, 1.0D);

        // Dentro de cada parche de arena, un segundo ruido INDEPENDIENTE (SURFACE_SECONDARY,
        // en vez de re-cortar el mismo SURFACE) decide si ese trozo es barro (mud) vanilla en
        // vez de arena. Así el barro no queda pegado siempre al mismo borde del parche, sino
        // repartido en manchas dentro de él (como un pantano/orilla mezclada con arena).
        // Rango -1.0..0.0 = mitad del parche aprox. será barro; sube/baja el 0.0 para más o menos.
        SurfaceRules.ConditionSource mudNoise = SurfaceRules.noiseCondition(Noises.SURFACE_SECONDARY, -1.0D, 0.0D);

        SurfaceRules.RuleSource mudPatch = SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR,
                        SurfaceRules.state(Blocks.MUD.defaultBlockState())
                ),
                SurfaceRules.ifTrue(
                        SurfaceRules.UNDER_FLOOR,
                        SurfaceRules.state(Blocks.MUD.defaultBlockState())
                )
        );

        SurfaceRules.RuleSource sandPatch = SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR,
                        SurfaceRules.state(com.nanookmod.registry.ModBlocks.CREPUSCULAR_SAND.get().defaultBlockState())
                ),
                SurfaceRules.ifTrue(
                        SurfaceRules.UNDER_FLOOR,
                        SurfaceRules.state(com.nanookmod.registry.ModBlocks.CREPUSCULAR_SAND.get().defaultBlockState())
                )
        );

        SurfaceRules.RuleSource sandColumn = SurfaceRules.ifTrue(
                sandNoise,
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(mudNoise, mudPatch),
                        sandPatch
                )
        );

        // Tierra Crepuscular (PLACEHOLDER de color): 1-3 bloques abajo
        SurfaceRules.RuleSource dirtUnder = SurfaceRules.ifTrue(
                SurfaceRules.UNDER_FLOOR,
                SurfaceRules.state(com.nanookmod.registry.ModBlocks.CREPUSCULAR_DIRT.get().defaultBlockState())
        );

        SurfaceRules.RuleSource normalColumn = SurfaceRules.sequence(podzolTop, dirtUnder);

        return SurfaceRules.ifTrue(
                aboveSurface,
                SurfaceRules.ifTrue(
                        isBosqueCrepuscular,
                        SurfaceRules.ifTrue(
                                notUnderwater,
                                SurfaceRules.sequence(sandColumn, normalColumn)
                        )
                )
        );
    }
}
