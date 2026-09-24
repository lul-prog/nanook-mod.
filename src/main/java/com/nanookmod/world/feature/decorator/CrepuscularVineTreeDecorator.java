package com.nanookmod.world.feature.decorator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nanookmod.registry.ModBlocks;
import com.nanookmod.registry.ModTreeDecoratorTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * Cuelga lianas crepusculares (crepuscular_vines / crepuscular_vines_plant,
 * nuestro equivalente a las cave vines de vainilla) directamente desde las
 * hojas de CADA árbol mientras el árbol se está generando.
 *
 * Esto reemplaza el enfoque anterior (un placed_feature independiente tipo
 * "minecraft:crepuscular_vine" que recorría el chunk al azar 48 veces
 * buscando hojas por encima con environment_scan). Ese approach es el mismo
 * que usa vainilla para cosas como el weeping vines dentro del Nether, pero
 * aquí es ineficiente porque:
 *  - No todas las tiradas aciertan a caer bajo un árbol real.
 *  - No hay forma de garantizar que TODOS los árboles tengan lianas: es pura
 *    probabilidad de "acertar" la posición correcta desde un scan externo.
 *
 * Un TreeDecorator, en cambio, corre como parte del propio TreeFeature justo
 * después de colocar tronco y hojas (igual que "minecraft:leave_vine" hace
 * con las vides normales de la jungla), así que:
 *  - Se ejecuta una vez por cada hoja de cada árbol generado, garantizando
 *    cobertura pareja en absolutamente todos los árboles del bioma.
 *  - No hace falta un placed_feature ni un count/in_square/heightmap aparte:
 *    todo el trabajo se resuelve en la misma pasada de generación del árbol.
 */
public class CrepuscularVineTreeDecorator extends TreeDecorator {

    public static final Codec<CrepuscularVineTreeDecorator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("vine_chance").forGetter(d -> d.vineChance),
                    Codec.intRange(1, 32).fieldOf("max_length").forGetter(d -> d.maxLength),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("berry_chance").forGetter(d -> d.berryChance)
            ).apply(instance, CrepuscularVineTreeDecorator::new)
    );

    /** Probabilidad, por cada bloque de hoja del árbol, de que nazca ahí una liana. */
    private final float vineChance;
    /** Longitud máxima (en bloques) que puede alcanzar el tramo "cuerpo" de la liana. */
    private final int maxLength;
    /** Probabilidad de que cada bloque individual de la liana nazca con bayas. */
    private final float berryChance;

    public CrepuscularVineTreeDecorator(float vineChance, int maxLength, float berryChance) {
        this.vineChance = vineChance;
        this.maxLength = maxLength;
        this.berryChance = berryChance;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return ModTreeDecoratorTypes.CREPUSCULAR_VINE.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource random = context.random();
        context.leaves().forEach(leafPos -> {
            if (random.nextFloat() >= this.vineChance) {
                return;
            }
            BlockPos below = leafPos.below();
            if (context.isAir(below)) {
                placeVineColumn(context, below);
            }
        });
    }

    /**
     * Coloca la columna colgante: N bloques de "cuerpo" (crepuscular_vines_plant)
     * y, si hay hueco, un último bloque "cabeza" (crepuscular_vines) en la
     * punta -- exactamente la misma estructura de dos capas que tenía el
     * block_column viejo, solo que ahora anclada de verdad a una hoja real.
     */
    private void placeVineColumn(TreeDecorator.Context context, BlockPos start) {
        RandomSource random = context.random();
        int bodyLength = random.nextInt(this.maxLength) + 1;
        BlockPos.MutableBlockPos cursor = start.mutable();
        int placed = 0;

        while (placed < bodyLength && context.isAir(cursor)) {
            boolean berries = random.nextFloat() < this.berryChance;
            context.setBlock(cursor, ModBlocks.CREPUSCULAR_VINES_PLANT.get().defaultBlockState()
                    .setValue(BlockStateProperties.BERRIES, berries));
            placed++;
            cursor.move(Direction.DOWN);
        }

        if (placed > 0 && context.isAir(cursor)) {
            boolean berries = random.nextFloat() < this.berryChance;
            context.setBlock(cursor, ModBlocks.CREPUSCULAR_VINES.get().defaultBlockState()
                    .setValue(BlockStateProperties.BERRIES, berries)
                    .setValue(BlockStateProperties.AGE_25, 0));
        }
    }
}
