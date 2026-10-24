package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 音量 / 红石模式设置。 */
public record SetSettingsPacket(BlockPos station, int volumePercent, boolean redstoneMode) {

    public static void encode(SetSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.volumePercent);
        buf.writeBoolean(p.redstoneMode);
    }

    public static SetSettingsPacket decode(FriendlyByteBuf buf) {
        return new SetSettingsPacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(SetSettingsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                if (p.volumePercent >= 0 && p.volumePercent <= 100) {
                    be.setVolume(p.volumePercent / 100F);
                }
                boolean wasOn = be.isRedstoneMode();
                be.setRedstoneMode(p.redstoneMode);
                // 刚打开红石模式且此时已通电:立即开始播放已有曲目
                if (!wasOn && p.redstoneMode && !be.isPlaying()
                        && player.level().hasNeighborSignal(p.station) && !be.getTarget().isEmpty()) {
                    be.startPlayback(be.getTarget(), be.isTargetUrl());
                }
                be.broadcastState();
            }
        });
        ctx.setPacketHandled(true);
    }
}
