package com.broadcast.network.packet;

import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端:上传本地麦克风 PCM(16kHz 单声道 16bit)。
 * 服务端校验话筒归属与激活状态后转发给所有玩家,由客户端在广播器位置播放。
 * 空数据表示客户端无可用麦克风,用于让服务端复位状态。
 */
public record MicUploadPacket(BlockPos mic, byte[] pcm) {

    public static void encode(MicUploadPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.mic);
        buf.writeByteArray(p.pcm);
    }

    public static MicUploadPacket decode(FriendlyByteBuf buf) {
        return new MicUploadPacket(buf.readBlockPos(), buf.readByteArray(65536));
    }

    public static void handle(MicUploadPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!(player.level().getBlockEntity(p.mic) instanceof MicrophoneBlockEntity mic)) return;
            if (!mic.isActive()) return;
            if (mic.getOwner() != null && !mic.getOwner().equals(player.getUUID()) && !player.hasPermissions(2)) {
                return;
            }
            if (p.pcm == null || p.pcm.length == 0) {
                // 客户端无法采集:只通知广播器附近的玩家,并复位状态
                com.broadcast.server.MicCapture.notifyFailure(mic,
                        net.minecraft.network.chat.Component.translatable("msg.broadcast.mic_unavailable_client"));
                mic.forceStop();
                return;
            }
            if (mic.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                NetworkHandler.sendToLevel(sl, new MicAudioPacket(p.mic, p.pcm));
            }
        });
        ctx.setPacketHandled(true);
    }
}
