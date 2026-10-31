package com.broadcast.network.packet;

import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.item.DebuggerItem;
import com.broadcast.util.Access;
import com.broadcast.util.FrequencyBandManager;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 设置话筒频率、红石控制模式、讲话时暂停音乐、开启提示音。 */
public record SetMicSettingsPacket(BlockPos pos, String frequency, int redstoneMode,
                                   boolean pauseMusic, boolean startSound) {

    public static void encode(SetMicSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.frequency, 32);
        buf.writeInt(p.redstoneMode);
        buf.writeBoolean(p.pauseMusic);
        buf.writeBoolean(p.startSound);
    }

    public static SetMicSettingsPacket decode(FriendlyByteBuf buf) {
        return new SetMicSettingsPacket(buf.readBlockPos(), buf.readUtf(32), buf.readInt(),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SetMicSettingsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.pos)) return;
            if (player.level().getBlockEntity(p.pos) instanceof MicrophoneBlockEntity mic) {
                String f = FrequencyUtil.normalize(p.frequency);
                if (f.isEmpty()) return;
                if (!FrequencyBandManager.canUse(player, f)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "msg.broadcast.freq_forbidden"), true);
                    return;
                }
                MicrophoneBlockEntity.RedstoneMode mode =
                        MicrophoneBlockEntity.RedstoneMode.byOrdinal(p.redstoneMode);
                boolean changed = !f.equals(mic.getFrequency())
                        || mode != mic.getRedstoneMode()
                        || p.pauseMusic != mic.isPauseMusicWhileTalking()
                        || p.startSound != mic.isStartSoundEnabled();
                if (!changed) return;
                if (!DebuggerItem.consumeDurability(player, DebuggerItem.COST_MIC)) return;
                mic.setFrequency(f);
                mic.setRedstoneMode(mode);
                mic.setPauseMusicWhileTalking(p.pauseMusic);
                mic.setStartSoundEnabled(p.startSound);
                mic.checkRedstone();
                DeviceInfoPacket.sendFor(player, p.pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
