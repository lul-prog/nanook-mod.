package com.nanookmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Estandarte antiguo: se cuelga en la CARA de otro bloque (como el estandarte
 * de pared vanilla), no se para solo en el piso.
 *
 * Antes esto era un Block normal sin ninguna BlockState property, por eso
 * siempre se veía igual sin importar el lado donde lo pusieras, y la
 * VoxelShape estaba centrada en el bloque en vez de pegada a la pared
 * (por eso se veía "flotando" lejos de la pared).
 */
public class AncientBannerBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Una caja distinta por dirección, pegada a la cara de la pared
    // correspondiente (grosor de 3 bloques de textura, ajustable si el
    // modelo final queda más fino o más grueso).
    protected static final VoxelShape SOUTH_SHAPE = Block.box(4.0, 0.0, 0.0, 12.0, 16.0, 3.0);
    protected static final VoxelShape NORTH_SHAPE = Block.box(4.0, 0.0, 13.0, 12.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 4.0, 3.0, 16.0, 12.0);
    protected static final VoxelShape WEST_SHAPE = Block.box(13.0, 0.0, 4.0, 16.0, 16.0, 12.0);

    public AncientBannerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Solo se puede colgar en la cara lateral de un bloque (no en el
        // piso ni en el techo), igual que el estandarte de pared vanilla.
        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }

        BlockState state = this.defaultBlockState().setValue(FACING, clickedFace);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // "facing" = hacia dónde mira el estandarte (alejándose de la
        // pared), así que la pared que lo sostiene está del lado opuesto.
        Direction facing = state.getValue(FACING);
        BlockPos wallPos = pos.relative(facing.getOpposite());
        return level.getBlockState(wallPos).isFaceSturdy(level, wallPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}