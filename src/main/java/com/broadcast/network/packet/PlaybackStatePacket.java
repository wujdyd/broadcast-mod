package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.audio.ClientAudioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 广播站播放状态同步:开始/停止、曲目、音量、出声的广播器列表。 */
public record PlaybackStatePacket(BlockPos station, boolean playing, String target, boolean url,
                                  boolean loop, int volumePercent, List<BlockPos> speakers) {

    public static void encode(PlaybackStatePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeBoolean(p.playing);
        buf.writeUtf(p.target, 1024);
        buf.writeBoolean(p.url);
        buf.writeBoolean(p.loop);
        buf.writeInt(p.volumePercent);
        buf.writeInt(p.speakers.size());
        for (BlockPos s : p.speakers) buf.writeBlockPos(s);
    }

    public static PlaybackStatePacket decode(FriendlyByteBuf buf) {
        BlockPos station = buf.readBlockPos();
        boolean playing = buf.readBoolean();
        String target = buf.readUtf(1024);
        boolean url = buf.readBoolean();
        boolean loop = buf.readBoolean();
        int vol = buf.readInt();
        int n = Math.min(buf.readInt(), 4096);
        List<BlockPos> speakers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) speakers.add(buf.readBlockPos());
        return new PlaybackStatePacket(station, playing, target, url, loop, vol, speakers);
    }

    public static void handle(PlaybackStatePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            ClientDataHolder.setPlaybackState(p.station, p.playing, p.target, p.url,
                    p.volumePercent, p.speakers);
            float vol = p.volumePercent / 100F;
            if (p.playing) {
                // 同曲目/同 URL 时只更新音量与喇叭列表;新增同频喇叭不会重置进度
                if (!ClientAudioManager.updateMusic(p.station, p.target, p.url, p.loop, vol, p.speakers)) {
                    ClientAudioManager.startMusic(p.station, p.target, p.url, p.loop, vol, p.speakers);
                }
            } else {
                ClientAudioManager.stopMusic(p.station);
            }
        });
        ctxSup.get().setPacketHandled(true);
    }
}
