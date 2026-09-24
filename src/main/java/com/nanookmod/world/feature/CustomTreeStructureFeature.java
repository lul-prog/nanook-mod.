package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Carga un árbol hecho a mano con el Structure Block (guardado como .nbt en
 * assets/nanookmod/structures/) y lo "estampa" en el mundo durante la
 * decoración normal del bioma.
 *
 * Es genérica: qué archivo .nbt cargar y dónde está el tronco dentro de esa
 * estructura viene de CustomTreeConfiguration, así que cada variante de
 * árbol es solo un configured_feature nuevo (ver twisted_spruce_custom.json
 * como ejemplo), sin tocar esta clase.
 *
 * IMPORTANTE: esto NO usa el sistema de Structures de Minecraft (el de
 * aldeas/templos/etc con structure_set y spacing) — es una Feature normal,
 * igual que un árbol o el pico de hielo. Por eso NO aparece con
 * /locate structure: para el juego es indistinguible de cualquier otro
 * elemento decorativo del paisaje.
 */
public class CustomTreeStructureFeature extends Feature<CustomTreeConfiguration> {

    // Cuántos bloques de diferencia de altura se toleran en el terreno bajo
    // el árbol antes de cancelar la plantación. Esto NO es para exigir
    // suelo perfectamente plano (los árboles reales también crecen en
    // laderas) — es solo para evitar casos extremos tipo borde de
    // acantilado, donde sí quedarían claramente flotando. Si lo subes,
    // permites laderas más empinadas; si lo bajas, solo terreno más plano.
    private static final int MAX_TERRAIN_UNEVENNESS = 7;

    public CustomTreeStructureFeature(Codec<CustomTreeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<CustomTreeConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        CustomTreeConfiguration config = context.config();

        if (!canPlantHere(level, origin)) {
            return false;
        }

        StructureTemplateManager templateManager = level.getLevel().getStructureManager();
        StructureTemplate template = templateManager.getOrCreate(config.structureId);

        if (template.getSize().getX() == 0) {
            // No se encontró el archivo .nbt (mal puesto o mal nombrado).
            return false;
        }

        // Rotación y espejo al azar para que no todos los árboles se vean
        // idénticos/orientados igual.
        Rotation rotation = Rotation.getRandom(random);
        Mirror mirror = random.nextBoolean() ? Mirror.LEFT_RIGHT : Mirror.NONE;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(true)
                // Esto evita que el aire guardado en la estructura borre el
                // terreno real (los cráteres escalonados de antes): el aire
                // del .nbt se salta, dejando lo que ya había ahí sin tocar.
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);

        // OJO: placeInWorld() rota/espeja la estructura alrededor de
        // placePos (el punto local (0,0,0), o sea la esquina), NO alrededor
        // del tronco. Si restamos trunkOffset sin transformarlo, el tronco
        // solo cae en "origin" cuando la rotación es NONE; con cualquier
        // otra rotación (75% de las veces) el tronco se desplaza varios
        // bloques a una columna cuya altura nunca comprobamos -> esos son
        // los árboles que aparecen flotando o hundidos, sobre todo notorio
        // en terreno escalonado como el de Mar Primigenio.
        // Solución: transformar trunkOffset con la MISMA rotación/mirror
        // antes de restarlo, usando el mismo cálculo que usa el motor de
        // structures para transformar cada bloque.
        BlockPos rotatedTrunkOffset = StructureTemplate.calculateRelativePosition(settings, config.trunkOffset);
        BlockPos placePos = origin.subtract(rotatedTrunkOffset);

        return template.placeInWorld(level, placePos, placePos, settings, random, 2);
    }

    /**
     * Revisa el terreno alrededor de donde caería el árbol: cancela la
     * plantación si hay demasiada diferencia de altura (ladera empinada,
     * borde de acantilado) O si hay agua/lava en cualquiera de los puntos
     * muestreados (lago, río, mar congelado...). Antes solo chequeábamos la
     * altura, y un lago es perfectamente parejo -> por eso plantaba árboles
     * flotando sobre el agua.
     */
    private boolean canPlantHere(WorldGenLevel level, BlockPos origin) {
        int baseHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());

        int minHeight = baseHeight;
        int maxHeight = baseHeight;

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);

                minHeight = Math.min(minHeight, h);
                maxHeight = Math.max(maxHeight, h);

                // Chequeo directo del bloque de terreno real (no del
                // heightmap, que a veces no distingue bien agua de tierra):
                // si hay agua o lava justo bajo la superficie, cancelamos.
                BlockPos groundPos = new BlockPos(x, h - 1, z);
                if (!level.getFluidState(groundPos).isEmpty()) {
                    return false;
                }
            }
        }

        return (maxHeight - minHeight) <= MAX_TERRAIN_UNEVENNESS;
    }
}