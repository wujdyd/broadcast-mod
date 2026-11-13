package com.broadcast.network.packet;

import com.broadcast.network.NetworkHandler;
import com.broadcast.server.MusicLibrary;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 重命名音乐库文件(需要 OP2)。 */
public record RenameMusicPacket(BlockPos station, String uid, String newName) {

    public static void encode(RenameMusicPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.uid, 16);
        buf.writeUtf(p.newName, 128);
    }

    public static RenameMusicPacket decode(FriendlyByteBuf buf) {
        return new RenameMusicPacket(buf.readBlockPos(), buf.readUtf(16), buf.readUtf(128));
    }

    public static void handle(RenameMusicPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null) return;
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("需要 OP2 权限"), true);
                return;
            }
            if (!Access.canControl(player, p.station)) return;
            String newName = MusicLibrary.get().renameByUid(p.uid, p.newName);
            if (newName == null) {
                player.displayClientMessage(Component.literal("重命名失败: 名称重复或文件不存在"), false);
                return;
            }
            List<String> uids = new ArrayList<>();
            List<String> files = new ArrayList<>();
            for (MusicLibrary.Entry entry : MusicLibrary.get().entries()) {
                uids.add(entry.uid());
                files.add(entry.name());
            }
            NetworkHandler.sendToPlayer(player, new MusicListPacket(p.station, uids, files));
            player.displayClientMessage(Component.literal("已重命名为: " + newName), true);
        });
        ctx.setPacketHandled(true);
    }
}
