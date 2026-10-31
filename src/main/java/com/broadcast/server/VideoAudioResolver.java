package com.broadcast.server;

import com.broadcast.config.BroadcastConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 网络视频音频入口。真正的 yt-dlp/ffmpeg 逻辑在独立附属模组
 * broadcast_media_support 中;主模组通过隐藏指令调用它,不产生类加载依赖。
 */
public final class VideoAudioResolver {
    private static final List<String> HOSTS = List.of(
            "youtube.com", "youtu.be", "bilibili.com", "b23.tv",
            "douyin.com", "iesdouyin.com", "tiktok.com",
            "kuaishou.com", "kuaishouapp.com",
            "vimeo.com", "twitch.tv", "twitter.com", "x.com",
            "facebook.com", "instagram.com", "weibo.com",
            "iqiyi.com", "youku.com", "music.163.com", "y.qq.com", "kuwo.cn"
    );

    private VideoAudioResolver() {
    }

    public static boolean isVideoUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String h : HOSTS) {
                if (host.equals(h) || host.endsWith("." + h)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void resolve(ServerPlayer player, BlockPos stationPos, String url) {
        if (!BroadcastConfig.videoEnabled()) {
            player.displayClientMessage(Component.translatable("msg.broadcast.video_disabled"), true);
            return;
        }
        if (!ModList.get().isLoaded("broadcast_media_support")) {
            player.displayClientMessage(Component.literal("未安装broadcast media support，无法播放"), false);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.displayClientMessage(Component.translatable("msg.broadcast.video_resolving"), true);
        String dirHex = HexFormat.of().formatHex(
                UploadHttpServer.musicDir().toString().getBytes(StandardCharsets.UTF_8));
        String command = "broadcastmediasupport resolve "
                + stationPos.getX() + " " + stationPos.getY() + " " + stationPos.getZ()
                + " " + dirHex + " " + url;
        try {
            int result = server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            if (result == 0) {
                // 附属模组指令未注册/执行失败
                player.displayClientMessage(Component.literal("broadcast media support 指令执行失败"), false);
            }
        } catch (Throwable t) {
            player.displayClientMessage(Component.translatable(
                    "msg.broadcast.video_failed", String.valueOf(t.getMessage())), false);
        }
    }
}
