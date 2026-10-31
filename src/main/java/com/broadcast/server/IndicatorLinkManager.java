package com.broadcast.server;

import com.broadcast.block.IndicatorLightBlock;
import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.IndicatorLightBlockEntity;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指示灯的连接模式:调试器右键指示灯开始选择,再右键广播站/广播器/话筒完成连接。
 * 选择阶段和连接瞬间有粒子;完成后不再产生粒子。整个过程不消耗调试器耐久。
 */
public final class IndicatorLinkManager {
    private static final long SELECT_TIMEOUT_MS = 60_000L;
    private static final double MAX_DISTANCE = 64.0D;
    private static final Map<UUID, Selection> SELECTING = new ConcurrentHashMap<>();

    private IndicatorLinkManager() {
    }

    private record Selection(BlockPos indicator, long expiresAt) {
    }

    /** 由 DebuggerItem 调用;处理成功返回 true。 */
    public static boolean handleDebuggerUse(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long now = System.currentTimeMillis();
        Selection selection = SELECTING.get(player.getUUID());
        if (selection != null && selection.expiresAt < now) {
            SELECTING.remove(player.getUUID());
            selection = null;
        }

        if (level.getBlockState(pos).getBlock() instanceof IndicatorLightBlock) {
            if (selection != null && selection.indicator.equals(pos)) {
                SELECTING.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("msg.broadcast.indicator_link_cancel"), true);
                return true;
            }
            if (player.blockPosition().distSqr(pos) > MAX_DISTANCE * MAX_DISTANCE) {
                player.displayClientMessage(Component.translatable("msg.broadcast.too_far"), true);
                return true;
            }
            SELECTING.put(player.getUUID(), new Selection(pos.immutable(), now + SELECT_TIMEOUT_MS));
            burst(level, pos, 24);
            player.displayClientMessage(Component.translatable("msg.broadcast.indicator_link_start"), true);
            return true;
        }

        if (selection == null) return false;
        if (!isLinkable(level, pos)) return false;
        if (selection.indicator.distSqr(pos) > MAX_DISTANCE * MAX_DISTANCE) {
            player.displayClientMessage(Component.translatable("msg.broadcast.too_far"), true);
            return true;
        }
        if (level.getBlockEntity(selection.indicator) instanceof IndicatorLightBlockEntity indicator) {
            indicator.setLinkedPos(pos);
            line(level, selection.indicator, pos);
            SELECTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("msg.broadcast.indicator_linked"), true);
            return true;
        }
        SELECTING.remove(player.getUUID());
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        SELECTING.remove(player.getUUID());
    }

    /** 每 10 tick 在选择中的指示灯周围生成少量粒子;连接完成后 map 已清空。 */
    public static void tick(MinecraftServer server) {
        if (SELECTING.isEmpty()) return;
        long now = System.currentTimeMillis();
        SELECTING.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        for (Selection selection : SELECTING.values()) {
            if (server.overworld() == null) continue;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.isLoaded(selection.indicator)) {
                    burst(level, selection.indicator, 2);
                    break;
                }
            }
        }
    }

    private static boolean isLinkable(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BroadcastStationBlockEntity
                || be instanceof SpeakerBlockEntity
                || be instanceof MicrophoneBlockEntity;
    }

    private static void burst(ServerLevel level, BlockPos pos, int count) {
        level.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                count, 0.35D, 0.35D, 0.35D, 0.01D);
    }

    private static void line(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.getX() + 0.5D + (to.getX() - from.getX()) * t;
            double y = from.getY() + 0.5D + (to.getY() - from.getY()) * t;
            double z = from.getZ() + 0.5D + (to.getZ() - from.getZ()) * t;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
