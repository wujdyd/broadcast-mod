package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 设置广播站播放列表(UID 列表)。 */
public record SetPlaylistPacket(BlockPos station, List<String> playlist) {

    public static void encode(SetPlaylistPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.playlist.size());
        for (String u : p.playlist) buf.writeUtf(u, 32);
    }

    public static SetPlaylistPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readInt(), 128);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(32));
        return new SetPlaylistPacket(pos, list);
    }

    public static void handle(SetPlaylistPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                if (be.getPlaylist().equals(p.playlist)) return;
                if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_STATION)) return;
                be.setPlaylist(p.playlist);
                be.sendInfoTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
