package com.broadcast.media;

import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** 主模组通过反射调用的入口。 */
public final class MediaSupport {
    private MediaSupport() {
    }

    public static String resolveBlocking(ServerPlayer player, String url, String outputDir) throws Exception {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        ToolManager.Tools tools = ToolManager.ensure(player);
        String hash = sha1(url).substring(0, 12);
        Path out = dir.resolve("video_" + hash + ".mp3");
        if (Files.isRegularFile(out)) return out.getFileName().toString();
        String prefix = "video_" + hash;
        Path template = dir.resolve(prefix + ".%(ext)s");

        List<String> command = new ArrayList<>();
        command.add(tools.ytdlp().toString());
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--restrict-filenames");
        command.add("--ffmpeg-location");
        command.add(tools.ffmpeg().getParent().toString());
        command.add("-f");
        command.add("bestaudio/best");
        command.add("-o");
        command.add(template.toString());
        command.add(url);
        run(command, dir.resolve(prefix + ".ytdlp.log"), 10);

        Path downloaded = null;
        try (Stream<Path> stream = Files.list(dir)) {
            downloaded = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix + "."))
                    .filter(p -> !p.getFileName().toString().endsWith(".log"))
                    .findFirst().orElse(null);
        }
        if (downloaded == null) throw new IOException("yt-dlp 没有生成音频文件");
        String ext = extension(downloaded.getFileName().toString());
        if (ext.equals("mp3")) return downloaded.getFileName().toString();
        if (ext.equals("ogg") || ext.equals("wav") || ext.equals("flac")) {
            return downloaded.getFileName().toString();
        }

        List<String> ff = new ArrayList<>();
        ff.add(tools.ffmpeg().toString());
        ff.add("-y");
        ff.add("-i");
        ff.add(downloaded.toString());
        ff.add("-vn");
        ff.add("-ar");
        ff.add("44100");
        ff.add("-ac");
        ff.add("2");
        ff.add("-c:a");
        ff.add("libmp3lame");
        ff.add("-b:a");
        ff.add("192k");
        ff.add(out.toString());
        run(ff, dir.resolve(prefix + ".ffmpeg.log"), 5);
        if (!Files.isRegularFile(out)) throw new IOException("ffmpeg 没有生成 mp3");
        try { Files.deleteIfExists(downloaded); } catch (IOException ignored) {}
        return out.getFileName().toString();
    }

    private static void run(List<String> command, Path log, int timeoutMinutes) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
        } catch (IOException e) {
            throw new IOException("媒体工具启动失败: " + e.getMessage());
        }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("媒体工具执行超时");
        }
        try { Files.deleteIfExists(log); } catch (IOException ignored) {}
        if (process.exitValue() != 0) {
            throw new IOException("媒体工具执行失败(退出码 " + process.exitValue() + ")");
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static String sha1(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
