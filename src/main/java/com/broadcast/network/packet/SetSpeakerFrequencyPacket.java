package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.SpeakerBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import com.broadcast.util.FrequencyBandManager;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** 设置广播器频率。 */
public record SetSpeakerFrequencyPacket(BlockPos pos, String frequency) {

    public static void encode(SetSpeakerFrequencyPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.frequency, 32);
    }

    public static SetSpeakerFrequencyPacket decode(FriendlyByteBuf buf) {
        return new SetSpeakerFrequencyPacket(buf.readBlockPos(), buf.readUtf(32));
    }

    public static void handle(SetSpeakerFrequencyPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.pos)) return;
            if (player.level().getBlockEntity(p.pos) instanceof SpeakerBlockEntity speaker) {
                String f = FrequencyUtil.normalize(p.frequency);
                if (f.isEmpty()) return;
                if (!FrequencyBandManager.canUse(player, f)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "msg.broadcast.freq_forbidden"), true);
                    return;
                }
                String old = speaker.getFrequency();
                if (f.equals(old)) return;
                if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_SPEAKER)) return;
                speaker.setFrequency(f);
                // 旧频率/新频率对应的广播站都刷新一次,正在播放时立刻更新出声广播器列表
                if (player.level() instanceof ServerLevel level) {
                    BroadcastRegistryData reg = BroadcastRegistryData.get(level);
                    for (String freq : List.of(old, f)) {
                        for (BlockPos stationPos : reg.stationsFor(level, freq)) {
                            if (level.getBlockEntity(stationPos) instanceof BroadcastStationBlockEntity be) {
                                be.broadcastState();
                                be.broadcastInfo();
                            }
                        }
                    }
                }
                DeviceInfoPacket.sendFor(player, p.pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
