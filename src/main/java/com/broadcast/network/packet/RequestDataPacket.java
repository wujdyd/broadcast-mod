package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.network.NetworkHandler;
import com.broadcast.server.MusicLibrary;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 客户端打开 GUI 时请求音乐库列表与设备信息。 */
public record RequestDataPacket(BlockPos station) {

    public static void encode(RequestDataPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
    }

    public static RequestDataPacket decode(FriendlyByteBuf buf) {
        return new RequestDataPacket(buf.readBlockPos());
    }

    public static void handle(RequestDataPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (!(player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be)) return;

            List<String> uids = new ArrayList<>();
            List<String> files = new ArrayList<>();
            for (MusicLibrary.Entry entry : MusicLibrary.get().entries()) {
                uids.add(entry.uid());
                files.add(entry.name());
            }
            NetworkHandler.sendToPlayer(player, new MusicListPacket(p.station, uids, files));
            be.sendInfoTo(player);
            be.sendStateTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
