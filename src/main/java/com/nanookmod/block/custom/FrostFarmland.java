package com.nanookmod.block.custom;

import com.nanookmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;

public class FrostFarmland extends FarmBlock {

    public FrostFarmland(Properties pProperties) {
        super(pProperties);
    }

    // Copiamos el método privado de Minecraft para poder usarlo aquí
    private static boolean isNearWater(LevelReader pLevel, BlockPos pPos) {
        for (BlockPos blockpos : BlockPos.betweenClosed(pPos.offset(-4, 0, -4), pPos.offset(4, 1, 4))) {
            if (pLevel.getFluidState(blockpos).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    // Evita que se convierta en tierra normal al ser pisada
    @Override
    public void fallOn(Level pLevel, BlockState pState, BlockPos pPos, Entity pEntity, float pFallDistance) {
        if (!pLevel.isClientSide && ForgeHooks.onFarmlandTrample(pLevel, pPos, ModBlocks.FROST_ICE.get().defaultBlockState(), pFallDistance, pEntity)) {
            turnToFrostIce(pEntity, pState, pLevel, pPos);
        }
        // NO llamamos a super.fallOn() para evitar la lógica vanilla
    }

    // Evita que se convierta en tierra normal al secarse
    // Evita que se convierta en hielo al secarse (La dejamos vacía para que nunca se seque sola)
    @Override
    public void randomTick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        // La tierra de permafrost nunca se seca por sí sola.
        // Solo se convierte en hielo si la pisas (esto ya está manejado en el método fallOn).
    }

    // Este es el tick "programado" (no el aleatorio): Minecraft lo dispara
    // automáticamente 1 tick después de que updateShape() detecta que ya no
    // puede sobrevivir (por ejemplo, pusiste un bloque sólido encima). El
    // FarmBlock vanilla, en este tick, convierte a Blocks.DIRT directo — por
    // eso aparecía tierra normal en vez de FROST_ICE. Lo sobreescribimos
    // para que use nuestro propio bloque.
    @Override
    public void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        if (!pState.canSurvive(pLevel, pPos)) {
            turnToFrostIce(null, pState, pLevel, pPos);
        }
    }

    // Método personalizado para que se convierta en FROST_ICE
    public static void turnToFrostIce(Entity pEntity, BlockState pState, Level pLevel, BlockPos pPos) {
        pLevel.setBlock(pPos, Block.pushEntitiesUp(pState, ModBlocks.FROST_ICE.get().defaultBlockState(), pLevel, pPos), 3);
    }
}