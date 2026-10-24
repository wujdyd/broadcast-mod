package com.broadcast.network.packet;

import com.broadcast.network.NetworkHandler;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.util.NameUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端本地没有该音乐文件时,请求服务端分块下发。 */
public record RequestFilePacket(BlockPos station, String name) {

    public static void encode(RequestFilePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.name, 256);
    }

    public static RequestFilePacket decode(FriendlyByteBuf buf) {
        return new RequestFilePacket(buf.readBlockPos(), buf.readUtf(256));
    }

    public static void handle(RequestFilePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            String name = NameUtil.sanitizeFileName(p.name);
            // 文件可能很大:放到独立线程分块下发,避免卡住服务端主线程
            Thread t = new Thread(() -> UploadHttpServer.streamFileTo(name, (data, last) -> {
                NetworkHandler.sendToPlayer(player, new MusicChunkPacket(name, data, last));
                if (!last) {
                    try {
                        Thread.sleep(4);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }), "Broadcast-FileSend-" + player.getGameProfile().getName());
            t.setDaemon(true);
            t.start();
        });
        ctx.setPacketHandled(true);
    }
}
