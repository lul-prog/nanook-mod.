package com.nanookmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Igual que la vanilla CaveVinesPlantBlock, pero corrige los mismos dos
 * problemas que CrepuscularVinesBlock: getHeadBlock() y la cosecha de bayas
 * al hacer click derecho vienen fijos a Blocks.CAVE_VINES / Items.GLOW_BERRIES.
 */
public class CrepuscularVinesPlantBlock extends CaveVinesPlantBlock {

    private final Supplier<GrowingPlantHeadBlock> headBlock;
    private final Supplier<Item> berryItem;

    public CrepuscularVinesPlantBlock(Properties properties, Supplier<GrowingPlantHeadBlock> headBlock, Supplier<Item> berryItem) {
        super(properties);
        this.headBlock = headBlock;
        this.berryItem = berryItem;
    }

    @Nonnull
    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this.headBlock.get();
    }

    @Nonnull
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BlockStateProperties.BERRIES)) {
            Block.popResource(level, pos, new ItemStack(this.berryItem.get()));
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            BlockState newState = state.setValue(BlockStateProperties.BERRIES, false);
            level.setBlock(pos, newState, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
