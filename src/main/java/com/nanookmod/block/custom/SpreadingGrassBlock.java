package com.nanookmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Pasto genérico que se comporta como el GRASS_BLOCK vanilla:
 *  - Si queda sin luz suficiente arriba (por ejemplo, le ponen un bloque
 *    encima) se apaga y se convierte en su versión de tierra después de un
 *    tiempo (random tick, igual que el vanilla - no es instantáneo).
 *  - Si tiene luz suficiente, cada random tick intenta contagiar el pasto a
 *    un bloque de tierra vecino (radio de 1 bloque en horizontal, hasta 3
 *    hacia abajo / 1 hacia arriba), siempre que ESE bloque también tenga luz.
 *
 * Es genérica a propósito: el par tierra/pasto (crepuscular_dirt <->
 * crepuscular_grass, frost_dirt <-> frost_grass, etc.) se pasa por
 * constructor, así no hace falta duplicar esta clase por cada bioma.
 *
 * IMPORTANTE: para que esto corra, el bloque necesita random ticks
 * habilitados en sus Properties. Como estos bloques usan
 * BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK), ya vienen con eso
 * activado (el vanilla GRASS_BLOCK también se propaga por random tick).
 */
public class SpreadingGrassBlock extends Block {

    // Umbral de luz (0-15) para que el pasto se mantenga vivo / se pueda
    // contagiar. No es exactamente el mismo cálculo interno que usa
    // SpreadingSnowyDirtBlock en vanilla (ese usa el light engine crudo),
    // pero el resultado práctico es equivalente: con un bloque sólido
    // encima la luz cae a 0 y el pasto se apaga; al aire libre de día o de
    // noche con cielo despejado se mantiene con luz de sobra.
    private static final int MIN_LIGHT_TO_SPREAD = 9;
    private static final int MIN_LIGHT_TO_SURVIVE = 4;

    private final Supplier<Block> dirtCounterpart;

    public SpreadingGrassBlock(Properties properties, Supplier<Block> dirtCounterpart) {
        super(properties);
        this.dirtCounterpart = dirtCounterpart;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasEnoughLightAbove(level, pos, MIN_LIGHT_TO_SURVIVE)) {
            // Tapado (bloque sólido encima, por ejemplo) -> se apaga.
            level.setBlockAndUpdate(pos, dirtCounterpart.get().defaultBlockState());
            return;
        }

        if (!hasEnoughLightAbove(level, pos, MIN_LIGHT_TO_SPREAD)) {
            // Tiene luz para sobrevivir pero no la suficiente para contagiar
            // (sombra parcial, por ejemplo).
            return;
        }

        BlockState grassState = this.defaultBlockState();

        // Igual que el vanilla: 4 intentos por random tick, cada uno a un
        // punto random cercano.
        for (int i = 0; i < 4; i++) {
            BlockPos targetPos = pos.offset(
                    random.nextInt(3) - 1,
                    random.nextInt(5) - 3,
                    random.nextInt(3) - 1
            );

            if (!level.getBlockState(targetPos).is(dirtCounterpart.get())) {
                continue;
            }

            if (hasEnoughLightAbove(level, targetPos, MIN_LIGHT_TO_SURVIVE)) {
                level.setBlockAndUpdate(targetPos, grassState);
            }
        }
    }

    private static boolean hasEnoughLightAbove(ServerLevel level, BlockPos pos, int minLight) {
        return level.getMaxLocalRawBrightness(pos.above()) >= minLight;
    }
}