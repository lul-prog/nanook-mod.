package com.nanookmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Igual que la vanilla CaveVinesBlock, pero corrige dos problemas reales de esa
 * clase: tanto getBodyBlock() (a qué crece hacia abajo) como el manejo de la
 * cosecha de bayas al hacer click derecho vienen fijos (hardcodeados) a los
 * bloques/items de vainilla (Blocks.CAVE_VINES_PLANT / Items.GLOW_BERRIES),
 * sin importar qué mod registre la clase. Aquí sobreescribimos ambos para que
 * usen nuestros propios bloque/item.
 *
 * También sobreescribimos canSurvive: en vez de depender del chequeo de "cara
 * firme" que usa vainilla internamente (que puede rechazar formas de colisión
 * no estándar), simplemente exigimos que el bloque de arriba no sea aire.
 */
public class CrepuscularVinesBlock extends CaveVinesBlock {

    private final Supplier<Block> bodyBlock;
    private final Supplier<Item> berryItem;

    public CrepuscularVinesBlock(Properties properties, Supplier<Block> bodyBlock, Supplier<Item> berryItem) {
        super(properties);
        this.bodyBlock = bodyBlock;
        this.berryItem = berryItem;
    }

    @Nonnull
    @Override
    protected Block getBodyBlock() {
        return this.bodyBlock.get();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return !above.isAir();
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
