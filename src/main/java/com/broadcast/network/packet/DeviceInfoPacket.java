package com.broadcast.network.packet;

import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.blockentity.SpeakerBlockEntity;
import com.broadcast.client.ClientDataHolder;
import com.broadcast.config.BroadcastConfig;
import com.broadcast.network.NetworkHandler;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端:广播器 / 话筒的当前设置与状态。
 */
public record DeviceInfoPacket(BlockPos pos, String kind, String frequency, boolean powered,
                               boolean active, int redstoneMode, boolean pauseMusic,
                               int speakers, int poweredSpeakers, int stations, int volumePercent,
                               int detectionRadius) {

    public static void encode(DeviceInfoPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.kind, 16);
        buf.writeUtf(p.frequency, 32);
        buf.writeBoolean(p.powered);
        buf.writeBoolean(p.active);
        buf.writeInt(p.redstoneMode);
        buf.writeBoolean(p.pauseMusic);
        buf.writeInt(p.speakers);
        buf.writeInt(p.poweredSpeakers);
        buf.writeInt(p.stations);
        buf.writeInt(p.volumePercent);
        buf.writeInt(p.detectionRadius);
    }

    public static DeviceInfoPacket decode(FriendlyByteBuf buf) {
        return new DeviceInfoPacket(buf.readBlockPos(), buf.readUtf(16), buf.readUtf(32),
                buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(DeviceInfoPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> ClientDataHolder.setDevice(p.pos, new ClientDataHolder.DeviceView(
                p.pos, p.kind, p.frequency, p.powered, p.active, p.redstoneMode, p.pauseMusic,
                p.speakers, p.poweredSpeakers, p.stations, p.volumePercent, p.detectionRadius)));
        ctxSup.get().setPacketHandled(true);
    }

    /** 服务端构造并发送设备信息(广播器或话筒)。 */
    public static void sendFor(ServerPlayer player, BlockPos pos) {
        if (player == null || player.level() == null) return;
        if (!Access.canControl(player, pos)) return;
        if (player.level().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            boolean powered = player.level().hasNeighborSignal(pos);
            NetworkHandler.sendToPlayer(player, new DeviceInfoPacket(pos, "speaker",
                    speaker.getFrequency(), powered, false, 0, false,
                    0, 0, speaker.getStations().size(), 0, 0));
        } else if (player.level().getBlockEntity(pos) instanceof MicrophoneBlockEntity mic) {
            boolean powered = player.level().hasNeighborSignal(pos);
            int speakers = mic.getOutputSpeakers().size();
            int stations = mic.getLinkedStation() == null ? 0 : 1;
            NetworkHandler.sendToPlayer(player, new DeviceInfoPacket(pos, "microphone",
                    mic.getFrequency(), powered, mic.isActive(), mic.getRedstoneMode().ordinal(),
                    mic.isPauseMusicWhileTalking(), speakers, mic.countPoweredSpeakers(), stations,
                    Math.round(mic.getStationVolume() * 100), BroadcastConfig.micDetectionRadius()));
        }
    }
}
