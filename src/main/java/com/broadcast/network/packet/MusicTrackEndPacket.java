package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端通知服务端:播放列表中的当前单曲已经真正播放结束。 */
public record MusicTrackEndPacket(BlockPos station) {

    public static void encode(MusicTrackEndPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
    }

    public static MusicTrackEndPacket decode(FriendlyByteBuf buf) {
        return new MusicTrackEndPacket(buf.readBlockPos());
    }

    public static void handle(MusicTrackEndPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity station) {
                station.onTrackEnded();
            }
        });
        ctx.setPacketHandled(true);
    }
}
