package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record StopPlaybackPacket(BlockPos station) {

    public static void encode(StopPlaybackPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
    }

    public static StopPlaybackPacket decode(FriendlyByteBuf buf) {
        return new StopPlaybackPacket(buf.readBlockPos());
    }

    public static void handle(StopPlaybackPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                be.stopPlayback();
            }
        });
        ctx.setPacketHandled(true);
    }
}
