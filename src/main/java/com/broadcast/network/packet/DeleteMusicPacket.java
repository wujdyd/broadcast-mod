package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.network.NetworkHandler;
import com.broadcast.server.MusicLibrary;
import com.broadcast.util.Access;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 从音乐库删除一个 UID 对应的文件,并刷新同维度打开中的音乐列表。 */
public record DeleteMusicPacket(BlockPos station, String uid) {

    public static void encode(DeleteMusicPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.uid, 32);
    }

    public static DeleteMusicPacket decode(FriendlyByteBuf buf) {
        return new DeleteMusicPacket(buf.readBlockPos(), buf.readUtf(32));
    }

    public static void handle(DeleteMusicPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("msg.broadcast.delete_need_op")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            String uid = MusicLibrary.sanitizeUid(p.uid);
            if (uid == null) return;
            String removed = MusicLibrary.get().deleteByUid(uid);
            if (removed == null) {
                player.displayClientMessage(Component.translatable("msg.broadcast.delete_missing")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity be) {
                List<String> next = new ArrayList<>(be.getPlaylist());
                next.remove(uid);
                be.setPlaylist(next);
            }
            if (player.level() instanceof ServerLevel level) {
                List<String> uids = new ArrayList<>();
                List<String> files = new ArrayList<>();
                for (MusicLibrary.Entry entry : MusicLibrary.get().entries()) {
                    uids.add(entry.uid());
                    files.add(entry.name());
                }
                NetworkHandler.sendToLevel(level, new MusicListPacket(p.station, uids, files));
            }
            player.displayClientMessage(Component.translatable("msg.broadcast.delete_done", removed)
                    .withStyle(ChatFormatting.GREEN), true);
        });
        ctx.setPacketHandled(true);
    }
}
