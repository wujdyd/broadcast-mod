package com.broadcast.block;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 广播站:广播系统的核心方块。
 * 打开 GUI 可选择本地音乐库或网络音乐播放,管理半径内的广播器与话筒。
 */
public class BroadcastStationBlock extends Block implements EntityBlock {

    public BroadcastStationBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // 主体 + 天线
        return Shapes.or(Block.box(0, 0, 0, 16, 16, 16));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BroadcastStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof BroadcastStationBlockEntity station) {
                station.serverTick();
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (com.broadcast.item.DebuggerItem.findDebugger(sp).getItem()
                    instanceof com.broadcast.item.DebuggerItem
                    && com.broadcast.item.DebuggerItem.openFor(sp, pos)) {
                return InteractionResult.CONSUME;
            }
            sp.displayClientMessage(Component.translatable("msg.broadcast.need_debugger"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addStation(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel sl) {
                if (level.getBlockEntity(pos) instanceof BroadcastStationBlockEntity be) {
                    be.onStationRemoved();
                }
                BroadcastRegistryData.get(sl).removeStation(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BroadcastStationBlockEntity be) {
            return be.isPlaying() ? 15 : 0;
        }
        return 0;
    }

    /** 提示附近玩家(用于上传/话筒状态等通知)。 */
    public static void notifyAround(ServerLevel level, BlockPos pos, Component msg) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() == level && p.blockPosition().distSqr(pos) < 64 * 64) {
                p.displayClientMessage(msg, true);
            }
        }
    }
}
