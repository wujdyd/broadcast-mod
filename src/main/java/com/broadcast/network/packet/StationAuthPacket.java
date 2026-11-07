package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 密码广播站解锁。 */
public record StationAuthPacket(BlockPos station, String password) {

    public static void encode(StationAuthPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.password, 64);
    }

    public static StationAuthPacket decode(FriendlyByteBuf buf) {
        return new StationAuthPacket(buf.readBlockPos(), buf.readUtf(64));
    }

    public static void handle(StationAuthPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!(player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity station)) return;
            if (station.tryAuthorize(player, p.password)) {
                station.sendInfoTo(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.broadcast.station_unlocked"), true);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.broadcast.station_wrong_password"), true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
