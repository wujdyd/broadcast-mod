package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 音乐文件分块传输(服务端 → 客户端)。last=true 表示传输结束。 */
public record MusicChunkPacket(String name, byte[] data, boolean last) {

    public static void encode(MusicChunkPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.name, 256);
        buf.writeByteArray(p.data);
        buf.writeBoolean(p.last);
    }

    public static MusicChunkPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(256);
        byte[] data = buf.readByteArray(200_000);
        boolean last = buf.readBoolean();
        return new MusicChunkPacket(name, data, last);
    }

    public static void handle(MusicChunkPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> ClientDataHolder.appendMusicChunk(p.name, p.data, p.last));
        ctxSup.get().setPacketHandled(true);
    }
}
