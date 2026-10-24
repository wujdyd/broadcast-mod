package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端音乐库列表:UID 与文件名一一对应。 */
public record MusicListPacket(BlockPos station, List<String> uids, List<String> files) {

    public static void encode(MusicListPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeInt(p.uids.size());
        for (String u : p.uids) buf.writeUtf(u, 32);
        buf.writeInt(p.files.size());
        for (String f : p.files) buf.writeUtf(f, 256);
    }

    public static MusicListPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int nu = Math.min(buf.readInt(), 4096);
        List<String> uids = new ArrayList<>(nu);
        for (int i = 0; i < nu; i++) uids.add(buf.readUtf(32));
        int nf = Math.min(buf.readInt(), 4096);
        List<String> files = new ArrayList<>(nf);
        for (int i = 0; i < nf; i++) files.add(buf.readUtf(256));
        return new MusicListPacket(pos, uids, files);
    }

    public static void handle(MusicListPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> ClientDataHolder.setMusicList(p.station, p.uids, p.files));
        ctxSup.get().setPacketHandled(true);
    }
}
