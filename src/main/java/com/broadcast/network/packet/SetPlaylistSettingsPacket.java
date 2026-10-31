package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 播放列表设置:循环、顺序/逆序、每首间隔、单首时长。 */
public record SetPlaylistSettingsPacket(BlockPos station, boolean loop, boolean reverse,
                                        int gapSeconds, int trackSeconds) {

    public static void encode(SetPlaylistSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeBoolean(p.loop);
        buf.writeBoolean(p.reverse);
        buf.writeInt(p.gapSeconds);
        buf.writeInt(p.trackSeconds);
    }

    public static SetPlaylistSettingsPacket decode(FriendlyByteBuf buf) {
        return new SetPlaylistSettingsPacket(buf.readBlockPos(), buf.readBoolean(),
                buf.readBoolean(), buf.readInt(), buf.readInt());
    }

    public static void handle(SetPlaylistSettingsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                boolean changed = be.isPlaylistLoop() != p.loop
                        || be.isPlaylistReverse() != p.reverse
                        || be.getPlaylistGapSeconds() != p.gapSeconds
                        || be.getPlaylistTrackSeconds() != p.trackSeconds;
                if (!changed) return;
                if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_STATION)) return;
                be.setPlaylistSettings(p.loop, p.reverse, p.gapSeconds, p.trackSeconds);
                be.sendInfoTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
