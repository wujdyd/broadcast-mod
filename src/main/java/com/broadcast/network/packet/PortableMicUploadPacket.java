package com.broadcast.network.packet;

import com.broadcast.server.PortableMicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 便携话筒 PCM 上传(客户端 → 服务端)。 */
public record PortableMicUploadPacket(byte[] pcm) {

    public static void encode(PortableMicUploadPacket p, FriendlyByteBuf buf) {
        buf.writeByteArray(p.pcm);
    }

    public static PortableMicUploadPacket decode(FriendlyByteBuf buf) {
        return new PortableMicUploadPacket(buf.readByteArray(65536));
    }

    public static void handle(PortableMicUploadPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) PortableMicManager.onAudio(player, p.pcm);
        });
        ctx.setPacketHandled(true);
    }
}
