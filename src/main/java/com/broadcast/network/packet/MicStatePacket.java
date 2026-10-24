package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.audio.ClientAudioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 话筒广播状态。station 可为空(未连接广播站)。 */
public record MicStatePacket(BlockPos mic, boolean active, BlockPos station,
                             int volumePercent, List<BlockPos> speakers) {

    public static void encode(MicStatePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.mic);
        buf.writeBoolean(p.active);
        buf.writeBoolean(p.station != null);
        if (p.station != null) buf.writeBlockPos(p.station);
        buf.writeInt(p.volumePercent);
        buf.writeInt(p.speakers.size());
        for (BlockPos s : p.speakers) buf.writeBlockPos(s);
    }

    public static MicStatePacket decode(FriendlyByteBuf buf) {
        BlockPos mic = buf.readBlockPos();
        boolean active = buf.readBoolean();
        BlockPos station = buf.readBoolean() ? buf.readBlockPos() : null;
        int vol = buf.readInt();
        int n = Math.min(buf.readInt(), 4096);
        List<BlockPos> speakers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) speakers.add(buf.readBlockPos());
        return new MicStatePacket(mic, active, station, vol, speakers);
    }

    public static void handle(MicStatePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            ClientDataHolder.setMicActive(p.mic, p.active, p.station);
            if (p.active) {
                ClientAudioManager.startMic(p.mic, p.volumePercent / 100F, p.speakers);
            } else {
                ClientAudioManager.stopMic(p.mic);
            }
        });
        ctxSup.get().setPacketHandled(true);
    }
}
