package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 广播站的设备信息:广播器、话筒、频率、上传端口、播放列表与循环设置。 */
public record StationInfoPacket(BlockPos station, List<BlockPos> speakers, List<BlockPos> mics,
                                List<String> frequencies, int uploadPort,
                                List<String> playlist, boolean playlistLoop, boolean playlistReverse,
                                int playlistGap, int playlistTrackSeconds, int playlistIndex,
                                boolean playlistPlaying) {

    public static void encode(StationInfoPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.speakers.size());
        for (BlockPos s : p.speakers) buf.writeBlockPos(s);
        buf.writeInt(p.mics.size());
        for (BlockPos m : p.mics) buf.writeBlockPos(m);
        buf.writeInt(p.frequencies.size());
        for (String f : p.frequencies) buf.writeUtf(f, 32);
        buf.writeInt(p.uploadPort);
        buf.writeInt(p.playlist.size());
        for (String u : p.playlist) buf.writeUtf(u, 32);
        buf.writeBoolean(p.playlistLoop);
        buf.writeBoolean(p.playlistReverse);
        buf.writeInt(p.playlistGap);
        buf.writeInt(p.playlistTrackSeconds);
        buf.writeInt(p.playlistIndex);
        buf.writeBoolean(p.playlistPlaying);
    }

    public static StationInfoPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int ns = Math.min(buf.readInt(), 4096);
        List<BlockPos> speakers = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) speakers.add(buf.readBlockPos());
        int nm = Math.min(buf.readInt(), 4096);
        List<BlockPos> mics = new ArrayList<>(nm);
        for (int i = 0; i < nm; i++) mics.add(buf.readBlockPos());
        int nf = Math.min(buf.readInt(), 64);
        List<String> freqs = new ArrayList<>(nf);
        for (int i = 0; i < nf; i++) freqs.add(buf.readUtf(32));
        int port = buf.readInt();
        int np = Math.min(buf.readInt(), 4096);
        List<String> playlist = new ArrayList<>(np);
        for (int i = 0; i < np; i++) playlist.add(buf.readUtf(32));
        boolean loop = buf.readBoolean();
        boolean reverse = buf.readBoolean();
        int gap = buf.readInt();
        int trackSeconds = buf.readInt();
        int index = buf.readInt();
        boolean playing = buf.readBoolean();
        return new StationInfoPacket(pos, speakers, mics, freqs, port, playlist, loop, reverse,
                gap, trackSeconds, index, playing);
    }

    public static void handle(StationInfoPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> ClientDataHolder.setStationInfo(
                p.station, p.speakers, p.mics, p.frequencies, p.uploadPort, p.playlist,
                p.playlistLoop, p.playlistReverse, p.playlistGap, p.playlistTrackSeconds,
                p.playlistIndex, p.playlistPlaying));
        ctxSup.get().setPacketHandled(true);
    }
}
