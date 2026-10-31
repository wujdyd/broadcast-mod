package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.network.NetworkHandler;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.util.Access;
import com.broadcast.util.NameUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求播放:音乐库文件或网络直链。 */
public record PlayRequestPacket(BlockPos station, String target, boolean url, int volumePercent) {

    public static void encode(PlayRequestPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.target, 1024);
        buf.writeBoolean(p.url);
        buf.writeInt(p.volumePercent);
    }

    public static PlayRequestPacket decode(FriendlyByteBuf buf) {
        return new PlayRequestPacket(buf.readBlockPos(), buf.readUtf(1024), buf.readBoolean(), buf.readInt());
    }

    public static void handle(PlayRequestPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!(player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be)) return;
            if (!Access.canControl(player, p.station)) {
                player.displayClientMessage(Component.translatable("msg.broadcast.too_far")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }

            if (p.volumePercent >= 0 && p.volumePercent <= 100) {
                be.setVolume(p.volumePercent / 100F);
            }

            String target = p.target == null ? "" : p.target.trim();
            if (target.isEmpty()) return;

            if (p.url) {
                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                    player.displayClientMessage(Component.translatable("msg.broadcast.bad_url")
                            .withStyle(ChatFormatting.RED), false);
                    return;
                }
                if (com.broadcast.server.VideoAudioResolver.isVideoUrl(target)) {
                    com.broadcast.server.VideoAudioResolver.resolve(player, p.station, target);
                } else {
                    be.startPlayback(target, true);
                }
            } else {
                String name = NameUtil.sanitizeFileName(target);
                if (!UploadHttpServer.hasMusicFile(name)) {
                    player.displayClientMessage(Component.translatable("msg.broadcast.file_missing")
                            .withStyle(ChatFormatting.RED), false);
                    return;
                }
                be.startPlayback(name, false);
            }
        });
        ctx.setPacketHandled(true);
    }
}
