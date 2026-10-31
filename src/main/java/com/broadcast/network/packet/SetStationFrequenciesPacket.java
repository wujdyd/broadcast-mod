package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 设置广播站同时广播的多个频率。 */
public record SetStationFrequenciesPacket(BlockPos station, List<String> frequencies) {

    public static void encode(SetStationFrequenciesPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.frequencies.size());
        for (String f : p.frequencies) buf.writeUtf(f, 32);
    }

    public static SetStationFrequenciesPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readInt(), 64);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(32));
        return new SetStationFrequenciesPacket(pos, list);
    }

    public static void handle(SetStationFrequenciesPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                var next = FrequencyUtil.normalizeList(p.frequencies);
                if (next.equals(be.getFrequencies())) return;
                if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_STATION)) return;
                be.setFrequencies(next);
                be.sendInfoTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
