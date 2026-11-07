package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 设置广播站访问模式:公开/私人/密码。 */
public record SetStationAccessPacket(BlockPos station, int mode, String password) {

    public static void encode(SetStationAccessPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.mode);
        buf.writeUtf(p.password, 64);
    }

    public static SetStationAccessPacket decode(FriendlyByteBuf buf) {
        return new SetStationAccessPacket(buf.readBlockPos(), buf.readInt(), buf.readUtf(64));
    }

    public static void handle(SetStationAccessPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (!(player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity station)) return;
            if (!station.isOwnerOrOp(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.broadcast.station_owner_only"), true);
                return;
            }
            BroadcastStationBlockEntity.AccessMode mode =
                    p.mode >= 0 && p.mode < BroadcastStationBlockEntity.AccessMode.values().length
                            ? BroadcastStationBlockEntity.AccessMode.values()[p.mode]
                            : BroadcastStationBlockEntity.AccessMode.PUBLIC;
            if (mode == BroadcastStationBlockEntity.AccessMode.PASSWORD && p.password.isBlank()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.broadcast.station_password_required"), true);
                return;
            }
            if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_STATION)) return;
            station.setAccess(mode, p.password, player);
            station.sendInfoTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
