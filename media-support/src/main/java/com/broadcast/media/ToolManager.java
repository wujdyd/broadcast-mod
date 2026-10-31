package com.broadcast.media;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 自动获取/管理 yt-dlp 与 ffmpeg。 */
final class ToolManager {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private ToolManager() {
    }

    record Tools(Path ytdlp, Path ffmpeg) {
    }

    static synchronized Tools ensure(ServerPlayer player) throws Exception {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("broadcast_media_support").resolve("tools");
        Files.createDirectories(dir);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win");
        boolean mac = os.contains("mac");
        String ytName = windows ? "yt-dlp.exe" : "yt-dlp";
        String ffName = windows ? "ffmpeg.exe" : "ffmpeg";
        Path yt = dir.resolve(ytName);
        Path ff = dir.resolve(ffName);

        String configuredYt = MediaSupportConfig.YTDLP.get();
        if (!"auto".equalsIgnoreCase(configuredYt) && !configuredYt.isBlank()) {
            Path p = Path.of(configuredYt);
            if (!Files.isRegularFile(p)) throw new IllegalStateException("yt-dlp 路径不存在: " + configuredYt);
            yt = p;
        }
        String configuredFf = MediaSupportConfig.FFMPEG.get();
        if (!"auto".equalsIgnoreCase(configuredFf) && !configuredFf.isBlank()) {
            Path p = Path.of(configuredFf);
            if (!Files.isRegularFile(p)) throw new IllegalStateException("ffmpeg 路径不存在: " + configuredFf);
            ff = p;
        }

        boolean needYt = !Files.isRegularFile(yt);
        boolean needFf = !Files.isRegularFile(ff);
        if (!needYt && !needFf) return new Tools(yt, ff);
        if (!MediaSupportConfig.AUTO_DOWNLOAD.get()) {
            throw new IllegalStateException("缺少 yt-dlp/ffmpeg,且自动下载已关闭");
        }

        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (needYt) {
            String url = windows
                    ? "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
                    : mac
                    ? "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
                    : (arm
                    ? "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
                    : "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux");
            notify(player, "正在下载 yt-dlp…");
            download(url, yt);
            setExecutable(yt);
        }
        if (needFf) {
            String url;
            boolean zip;
            if (windows) {
                url = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
                zip = true;
            } else if (mac) {
                url = macFfmpegUrl();
                zip = true;
            } else {
                url = arm
                        ? "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-arm64-static.tar.xz"
                        : "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz";
                zip = false;
            }
            notify(player, "正在下载 ffmpeg(约 80MB)…");
            Path archive = dir.resolve(zip ? "ffmpeg.zip" : "ffmpeg.tar.xz");
            download(url, archive);
            Path temp = Files.createTempDirectory(dir, "ffmpeg-");
            try {
                if (zip) unzip(archive, ff);
                else untar(archive, temp, ff);
            } finally {
                delete(archive);
                deleteTree(temp);
            }
            setExecutable(ff);
        }
        if (!Files.isRegularFile(yt) || !Files.isRegularFile(ff)) {
            throw new IllegalStateException("媒体工具下载不完整");
        }
        return new Tools(yt, ff);
    }

    /** evermeet 的固定 getrelease 入口已失效,改为从 info API 取当前 snapshot zip。 */
    private static String macFfmpegUrl() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://evermeet.cx/ffmpeg/info/ffmpeg/snapshot"))
                .header("User-Agent", "BroadcastMediaSupport/0.0.3")
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        String json = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"zip\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", java.util.regex.Pattern.DOTALL)
                .matcher(json);
        if (!m.find()) throw new IllegalStateException("无法获取 macOS ffmpeg 下载地址");
        return m.group(1).replace("\\/", "/");
    }

    private static void download(String url, Path target) throws Exception {        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(tmp);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "BroadcastMediaSupport/0.0.3")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
        if (response.statusCode() / 100 != 2 || !Files.isRegularFile(tmp)) {
            delete(tmp);
            throw new IllegalStateException("下载失败 HTTP " + response.statusCode() + ": " + url);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void unzip(Path archive, Path target) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) continue;
                if (name.endsWith("/ffmpeg") || name.endsWith("/ffmpeg.exe")
                        || name.equals("ffmpeg") || name.equals("ffmpeg.exe")) {
                    try (OutputStream out = Files.newOutputStream(target)) {
                        zip.transferTo(out);
                    }
                    return;
                }
            }
        }
        throw new IllegalStateException("压缩包中未找到 ffmpeg");
    }

    private static void untar(Path archive, Path temp, Path target) throws Exception {
        Process process = new ProcessBuilder("tar", "-xJf", archive.toString(), "-C", temp.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg 解压超时");
        }
        if (process.exitValue() != 0) throw new IllegalStateException("ffmpeg 解压失败");
        try (Stream<Path> stream = Files.walk(temp)) {
            Path found = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("ffmpeg"))
                    .findFirst().orElse(null);
            if (found == null) throw new IllegalStateException("压缩包中未找到 ffmpeg");
            Files.copy(found, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setExecutable(Path file) {
        try {
            file.toFile().setExecutable(true, false);
        } catch (Throwable ignored) {
        }
    }

    private static void notify(ServerPlayer player, String text) {
        if (player.getServer() != null) {
            player.getServer().execute(() -> player.displayClientMessage(Component.literal(text), true));
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(ToolManager::delete);
        } catch (Exception ignored) {
        }
    }
}
