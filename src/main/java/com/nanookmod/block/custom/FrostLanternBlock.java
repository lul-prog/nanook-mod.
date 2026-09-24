package com.nanookmod.block.custom;

import com.nanookmod.block.entity.FrostLanternBlockEntity;
import com.nanookmod.registry.ModBlockEntities;
import com.nanookmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FrostLanternBlock extends BaseEntityBlock {

    // 0 = Apagada, 1 = Baja energía, 2 = Encendida
    public static final IntegerProperty FUEL_STATE = IntegerProperty.create("fuel_state", 0, 2);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public FrostLanternBlock(Properties pProperties) {
        super(pProperties);
        // Por defecto, la linterna se coloca apagada (estado 0)
        this.registerDefaultState(this.stateDefinition.any().setValue(FUEL_STATE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FUEL_STATE);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer,
                                 InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
        if (blockEntity instanceof FrostLanternBlockEntity lantern) {
            ItemStack heldItem = pPlayer.getItemInHand(pHand);

            if (heldItem.is(ModItems.FROST_FUEL.get())) {
                lantern.addFuel(heldItem);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock()) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof FrostLanternBlockEntity) {
                // Aquí podríamos dropear el combustible restante en el futuro
            }
            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new FrostLanternBlockEntity(pPos, pState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide) return null;
        return createTickerHelper(pBlockEntityType, ModBlockEntities.FROST_LANTERN.get(),
                (level, pos, state, entity) -> entity.tick(level, pos, state, entity));
    }
}