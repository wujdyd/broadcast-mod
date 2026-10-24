package com.broadcast.network.packet;

import com.broadcast.client.ClientMicController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端:通知放置话筒的玩家开始/停止采集自己的麦克风。
 * 仅在配置为客户端采集(专用服务器默认)时使用。
 */
public record MicCaptureControlPacket(BlockPos mic, boolean active, int volumePercent, List<BlockPos> speakers) {

    public static void encode(MicCaptureControlPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.mic);
        buf.writeBoolean(p.active);
        buf.writeInt(p.volumePercent);
        buf.writeInt(p.speakers.size());
        for (BlockPos s : p.speakers) buf.writeBlockPos(s);
    }

    public static MicCaptureControlPacket decode(FriendlyByteBuf buf) {
        BlockPos mic = buf.readBlockPos();
        boolean active = buf.readBoolean();
        int vol = buf.readInt();
        int n = Math.min(buf.readInt(), 4096);
        List<BlockPos> speakers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) speakers.add(buf.readBlockPos());
        return new MicCaptureControlPacket(mic, active, vol, speakers);
    }

    public static void handle(MicCaptureControlPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            if (p.active) {
                ClientMicController.start(p.mic, p.volumePercent / 100F, p.speakers);
            } else {
                ClientMicController.stop(p.mic);
            }
        });
        ctxSup.get().setPacketHandled(true);
    }
}
