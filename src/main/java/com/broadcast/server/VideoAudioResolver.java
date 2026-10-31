package com.broadcast.server;

import com.broadcast.BroadcastMod;
import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.config.BroadcastConfig;
import com.broadcast.util.NameUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 网络视频音频解析:
 * 服务器安装 yt-dlp(及 ffmpeg)后,可将 B站/抖音/快手/YouTube/TikTok 等视频链接
 * 下载为 mp3 放入音乐目录,再走本模组已有的本地音乐播放链路。
 * 没有外部工具时给出明确提示,不影响普通音频直链播放。
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
        player.displayClientMessage(Component.translatable("msg.broadcast.video_resolving"), true);
        MinecraftServer server = player.getServer();
        Thread thread = new Thread(() -> {
            try {
                String fileName = resolveBlocking(player, url);
                server.execute(() -> {
                    if (!(player.level().getBlockEntity(stationPos) instanceof BroadcastStationBlockEntity station)) {
                        return;
                    }
                    MusicLibrary.get().reload();
                    station.startPlayback(fileName, false);
                    player.displayClientMessage(Component.translatable("msg.broadcast.video_ready", fileName), true);
                });
            } catch (Throwable t) {
                BroadcastMod.LOGGER.warn("视频音频解析失败 {}: {}", url, t.getMessage());
                server.execute(() -> player.displayClientMessage(
                        Component.translatable("msg.broadcast.video_failed", String.valueOf(t.getMessage())), false));
            }
        }, "Broadcast-VideoResolve");
        thread.setDaemon(true);
        thread.start();
    }

    private static String resolveBlocking(ServerPlayer player, String url) throws Exception {
        String hash = sha1(url).substring(0, 12);
        Path dir = UploadHttpServer.musicDir();
        Files.createDirectories(dir);
        Path finalMp3 = dir.resolve("video_" + hash + ".mp3");
        if (Files.isRegularFile(finalMp3)) return finalMp3.getFileName().toString();

        String prefix = "video_" + hash;
        Path template = dir.resolve(prefix + ".%(ext)s");
        ProcessBuilder yt = new ProcessBuilder(
                BroadcastConfig.ytDlp(),
                "--no-playlist", "--no-warnings", "--restrict-filenames",
                "-f", "bestaudio/best",
                "-o", template.toString(),
                url);
        yt.redirectErrorStream(true);
        Path ytLog = dir.resolve(prefix + ".ytdlp.log");
        yt.redirectOutput(ytLog.toFile());
        Process process;
        try {
            process = yt.start();
        } catch (IOException e) {
            throw new IOException("未找到 yt-dlp,请安装并加入 PATH");
        }
        if (!process.waitFor(300, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("yt-dlp 解析超时");
        }
        try { Files.deleteIfExists(ytLog); } catch (IOException ignored) {}
        if (process.exitValue() != 0) throw new IOException("yt-dlp 解析失败(退出码 " + process.exitValue() + ")");

        Path downloaded = null;
        try (Stream<Path> stream = Files.list(dir)) {
            downloaded = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix + "."))
                    .filter(p -> !p.getFileName().toString().endsWith(".log"))
                    .findFirst().orElse(null);
        }
        if (downloaded == null) throw new IOException("yt-dlp 没有生成音频文件");

        String ext = NameUtil.extension(downloaded.getFileName().toString());
        if (NameUtil.isPlayableExtension(downloaded.getFileName().toString())) {
            if (!ext.equals("mp3")) {
                Path converted = dir.resolve(prefix + ".mp3");
                convert(downloaded, converted);
                try { Files.deleteIfExists(downloaded); } catch (IOException ignored) {}
                return converted.getFileName().toString();
            }
            return downloaded.getFileName().toString();
        }

        Path converted = dir.resolve(prefix + ".mp3");
        convert(downloaded, converted);
        try { Files.deleteIfExists(downloaded); } catch (IOException ignored) {}
        return converted.getFileName().toString();
    }

    private static void convert(Path input, Path output) throws Exception {
        ProcessBuilder ff = new ProcessBuilder(
                BroadcastConfig.ffmpeg(),
                "-y", "-i", input.toString(),
                "-vn", "-ar", "44100", "-ac", "2",
                "-c:a", "libmp3lame", "-b:a", "192k",
                output.toString());
        ff.redirectErrorStream(true);
        Path ffLog = output.resolveSibling(output.getFileName() + ".ffmpeg.log");
        ff.redirectOutput(ffLog.toFile());
        Process process;
        try {
            process = ff.start();
        } catch (IOException e) {
            throw new IOException("未找到 ffmpeg,请安装并加入 PATH");
        }
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg 转码超时");
        }
        try { Files.deleteIfExists(ffLog); } catch (IOException ignored) {}
        if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
            throw new IOException("ffmpeg 转码失败");
        }
    }

    private static String sha1(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
