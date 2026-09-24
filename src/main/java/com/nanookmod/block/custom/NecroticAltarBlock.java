package com.nanookmod.block.custom;

import com.nanookmod.block.entity.NecroticAltarBlockEntity;
import com.nanookmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Altar Necromante. Antes era un {@code Block} plano sin BlockEntity (puramente decorativo, con
 * su modelo 3D y sus propiedades ya elegidas -- inrompible en survival, sonido de madera del
 * Nether). Ahora extiende {@link BaseEntityBlock} para poder llevar la ofrenda del ritual, pero
 * las propiedades con las que se registra en {@code ModBlocks} NO cambiaron: mismo
 * {@code strength}, mismo sonido, mismo {@code noOcclusion}.
 *
 * La lógica de negocio (qué ítems acepta, cuándo se completa, cuándo aparece el Knight) vive
 * entera en {@link NecroticAltarBlockEntity}; esta clase solo enruta el clic derecho hacia ahí.
 */
public class NecroticAltarBlock extends BaseEntityBlock {

    public NecroticAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Sigue dibujándose con su modelo 3D normal (blockstates/crepuscular_altar.json); el
        // BlockEntity no tiene renderer propio, así que no reemplaza nada visual.
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NecroticAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // toda la lógica del ritual es autoridad de servidor
        }
        return createTickerHelper(type, ModBlockEntities.NECROTIC_ALTAR.get(),
                (lvl, pos, st, be) -> be.tick(lvl, pos, st, be));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS; // evita procesar el clic dos veces (una por cada mano)
        }
        if (!(level.getBlockEntity(pos) instanceof NecroticAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        boolean handled;
        if (stack.isEmpty()) {
            // PEDIDO: "todo lo digamos por mensaje". Clic con la mano vacía ya no hace nada (PASS);
            // ahora es la forma de "consultar" el altar -- muestra el estado de las ofrendas como
            // mensaje de acción en vez del panel flotante que había antes.
            handled = altar.showStatus(player);
        } else {
            handled = altar.interactWithItem(player, stack);
        }

        if (handled) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }
}
