package com.broadcast.block;

import com.broadcast.blockentity.SpeakerBlockEntity;
import com.broadcast.config.BroadcastConfig;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 广播器(挂墙式 / 吸顶式 / 落地式)。
 * 放置后自动注册进维度设备表,由最近的广播站检索连接。
 */
public class SpeakerBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public enum Type { WALL, CEILING, FLOOR, PILLAR }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Type type;

    public SpeakerBlock(Type type, Properties props) {
        super(props);
        this.type = type;
        Direction defaultDir = type == Type.CEILING ? Direction.DOWN : Direction.NORTH;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, defaultDir)
                .setValue(WATERLOGGED, false));
    }

    public Type getSpeakerType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        FluidState fluid = level.getFluidState(pos);
        BlockState state = this.defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);

        if (type == Type.CEILING) {
            Direction dir = (face == Direction.UP || face == Direction.DOWN) ? face : Direction.DOWN;
            BlockPos supportPos = pos.relative(dir.getOpposite());
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, dir)) {
                return null;
            }
            return state.setValue(FACING, dir);
        }

        if (type == Type.WALL || type == Type.PILLAR) {
            Direction dir = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
            BlockPos supportPos = pos.relative(dir.getOpposite());
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, dir)) {
                return null;
            }
            return state.setValue(FACING, dir);
        }

        // 落地式:下方必须有结实支撑
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        return state.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        Direction supportSide = type == Type.FLOOR ? Direction.DOWN : state.getValue(FACING).getOpposite();
        if (dir == supportSide && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (type == Type.FLOOR) {
            // 落地式音柱:需要下方有结实的支撑面
            BlockPos below = pos.below();
            return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        }
        Direction facingDir = state.getValue(FACING);
        BlockPos behind = pos.relative(facingDir.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facingDir);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---------- 碰撞盒 ----------

    private static final VoxelShape WALL_SOUTH = box(2, 2, 0, 14, 14, 14);
    private static final VoxelShape WALL_NORTH = box(2, 2, 2, 14, 14, 16);
    private static final VoxelShape WALL_EAST = box(2, 2, 2, 16, 14, 14);
    private static final VoxelShape WALL_WEST = box(0, 2, 2, 14, 14, 14);
    private static final VoxelShape CEILING_DOWN = box(2.5, 10, 2.5, 13.5, 16, 13.5);
    private static final VoxelShape CEILING_UP = box(2.5, 0, 2.5, 13.5, 6, 13.5);
    private static final VoxelShape FLOOR_SHAPE = Shapes.or(box(4, 0, 4, 12, 1, 12), box(4.5, 1, 4.5, 11.5, 16, 11.5));
    private static final VoxelShape PILLAR_SOUTH = box(5, 0, 0, 11, 16, 5);
    private static final VoxelShape PILLAR_NORTH = box(5, 0, 11, 11, 16, 16);
    private static final VoxelShape PILLAR_EAST = box(0, 0, 5, 5, 16, 11);
    private static final VoxelShape PILLAR_WEST = box(11, 0, 5, 16, 16, 11);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction dir = state.getValue(FACING);
        if (type == Type.CEILING) {
            return dir == Direction.DOWN ? CEILING_DOWN : CEILING_UP;
        }
        if (type == Type.FLOOR) {
            return FLOOR_SHAPE;
        }
        if (type == Type.PILLAR) {
            return switch (dir) {
                case SOUTH -> PILLAR_SOUTH;
                case NORTH -> PILLAR_NORTH;
                case EAST -> PILLAR_EAST;
                default -> PILLAR_WEST;
            };
        }
        switch (dir) {
            case SOUTH: return WALL_SOUTH;
            case NORTH: return WALL_NORTH;
            case EAST: return WALL_EAST;
            default: return WALL_WEST;
        }
    }

    // ---------- 交互 ----------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (com.broadcast.item.DebuggerItem.findDebugger(sp).getItem()
                    instanceof com.broadcast.item.DebuggerItem
                    && com.broadcast.item.DebuggerItem.openFor(sp, pos)) {
                return InteractionResult.CONSUME;
            }
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.broadcast.need_debugger"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------- 方块实体 ----------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel sl) {
                BroadcastRegistryData.get(sl).removeSpeaker(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addSpeaker(pos);
            if (placer instanceof Player player
                    && level.getBlockEntity(pos) instanceof SpeakerBlockEntity be) {
                int permission = player.hasPermissions(3) ? 3 : player.hasPermissions(2) ? 2 : 0;
                be.setOwner(player, permission);
            }
        }
    }
}
