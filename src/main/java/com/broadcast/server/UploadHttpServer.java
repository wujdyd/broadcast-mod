package com.broadcast.server;

import com.broadcast.BroadcastMod;
import com.broadcast.config.BroadcastConfig;
import com.broadcast.util.NameUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音乐上传 HTTP 服务:服务端开放端口,接收客户端上传的音乐文件到指定目录。
 * 端点:
 *   PUT/POST /upload?name=文件名   —— 请求体为文件内容
 *   GET      /                     —— 状态页(含文件列表与上传说明)
 */
public final class UploadHttpServer {
    private static HttpServer server;
    private static ExecutorService executor;

    private UploadHttpServer() {
    }

    public static Path musicDir() {
        return FMLPaths.GAMEDIR.get().resolve(BroadcastConfig.uploadDir());
    }

    public static synchronized void start(MinecraftServer ms) {
        if (server != null) return;
        int port = BroadcastConfig.uploadPort();
        if (port <= 0) {
            BroadcastMod.LOGGER.info("广播上传服务已按配置关闭");
            return;
        }
        try {
            Path dir = musicDir();
            Files.createDirectories(dir);
            MusicLibrary.get().reload();
            HttpServer s = HttpServer.create(new InetSocketAddress(BroadcastConfig.bindAddress(), port), 0);
            s.createContext("/upload", UploadHttpServer::handleUpload);
            s.createContext("/", UploadHttpServer::handleIndex);
            executor = Executors.newFixedThreadPool(2);
            s.setExecutor(executor);
            s.start();
            server = s;
            BroadcastMod.LOGGER.info("广播上传服务已启动: http://{}:{}/upload 目录: {}",
                    BroadcastConfig.bindAddress(), port, dir.toAbsolutePath());
        } catch (Throwable t) {
            BroadcastMod.LOGGER.error("广播上传服务启动失败(端口 {} 被占用或权限不足): {}",
                    port, t.getMessage());
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static boolean isRunning() {
        return server != null;
    }

    // ---------- 文件访问 ----------

    public static List<String> listMusicFiles() {
        Path dir = musicDir();
        if (!Files.isDirectory(dir)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> NameUtil.isPlayableExtension(p.getFileName().toString()))
                    .forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException e) {
            BroadcastMod.LOGGER.warn("读取音乐目录失败: {}", e.getMessage());
        }
        Collections.sort(out);
        return out;
    }

    public static boolean hasMusicFile(String name) {
        String clean = NameUtil.sanitizeFileName(name);
        if (clean.isEmpty()) return false;
        Path f = musicDir().resolve(clean);
        return Files.isRegularFile(f);
    }

    public interface ChunkSender {
        void send(byte[] data, boolean last);
    }

    /** 把音乐文件分块推给客户端;文件不存在时发送空尾包并返回 false。 */
    public static boolean streamFileTo(String name, ChunkSender sender) {
        String clean = NameUtil.sanitizeFileName(name);
        Path f = musicDir().resolve(clean);
        if (clean.isEmpty() || !Files.isRegularFile(f)) {
            sender.send(new byte[0], true);
            return false;
        }
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) {
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                sender.send(chunk, false);
            }
            sender.send(new byte[0], true);
            return true;
        } catch (IOException e) {
            BroadcastMod.LOGGER.warn("下发音乐文件失败 {}: {}", clean, e.getMessage());
            sender.send(new byte[0], true);
            return false;
        }
    }

    // ---------- HTTP 处理 ----------

    private static void handleUpload(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod().toUpperCase();
            if (!method.equals("PUT") && !method.equals("POST")) {
                respond(ex, 405, "{\"ok\":false,\"error\":\"仅支持 PUT/POST 上传\"}");
                return;
            }
            String name = extractName(ex);
            if (name.isEmpty()) {
                respond(ex, 400, "{\"ok\":false,\"error\":\"缺少文件名参数 name\"}");
                return;
            }
            if (!NameUtil.isPlayableExtension(name)) {
                respond(ex, 400, "{\"ok\":false,\"error\":\"仅支持 .ogg / .wav / .mp3 音频\"}");
                return;
            }
            long max = BroadcastConfig.maxUploadBytes();
            Path dir = musicDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(name);

            long written = 0;
            boolean ok = false;
            try (InputStream in = ex.getRequestBody();
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    written += n;
                    if (written > max) {
                        respond(ex, 413, "{\"ok\":false,\"error\":\"文件超过大小限制\"}");
                        return;
                    }
                    out.write(buf, 0, n);
                }
                ok = written > 0;
            } finally {
                if (!ok) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException ignored) {
                    }
                }
            }
            if (written == 0) {
                respond(ex, 400, "{\"ok\":false,\"error\":\"空文件\"}");
                return;
            }
            BroadcastMod.LOGGER.info("收到上传音乐: {} ({} 字节)", name, written);
            String uid = MusicLibrary.get().ensure(name);
            respond(ex, 200, "{\"ok\":true,\"file\":\"" + escapeJson(name)
                    + "\",\"uid\":\"" + escapeJson(uid) + "\",\"bytes\":" + written + "}");
        } catch (Throwable t) {
            BroadcastMod.LOGGER.warn("处理上传失败: {}", t.getMessage());
            respond(ex, 500, "{\"ok\":false,\"error\":\"服务器内部错误\"}");
        }
    }

    private static String extractName(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String kv : query.split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0 && kv.substring(0, eq).equals("name")) {
                    try {
                        return NameUtil.sanitizeFileName(URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
                    } catch (Throwable t) {
                        return NameUtil.sanitizeFileName(kv.substring(eq + 1));
                    }
                }
            }
        }
        String path = ex.getRequestURI().getPath();
        String prefix = "/upload/";
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return NameUtil.sanitizeFileName(path.substring(prefix.length()));
        }
        return "";
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\">")
                .append("<title>广播站音乐上传</title><style>")
                .append("body{font-family:sans-serif;background:#14181f;color:#dbe2ea;max-width:760px;margin:40px auto}")
                .append("h1{color:#7ab8ff}input[type=file]{margin:8px 0}button{padding:6px 18px}")
                .append("li{margin:4px 0}code{background:#232a35;padding:2px 6px;border-radius:4px}")
                .append("</style></head><body>")
                .append("<h1>广播站 · 音乐上传服务</h1>")
                .append("<p>上传的音乐可在游戏内广播站 GUI 的「音乐库」中选择播放。</p>")
                .append("<p>文件目录: <code>").append(escapeHtml(musicDir().toAbsolutePath().toString())).append("</code></p>")
                .append("<h2>网页上传</h2>")
                .append("<input type=\"file\" id=\"f\"><button onclick=\"up()\">上传</button>")
                .append("<p id=\"st\"></p>")
                .append("<h2>命令行上传</h2>")
                .append("<p><code>curl -T 文件 \"http://本机IP:")
                .append(BroadcastConfig.uploadPort()).append("/upload?name=文件名\"</code></p>")
                .append("<h2>已有音乐</h2><ul>");
        for (String f : listMusicFiles()) {
            sb.append("<li>").append(escapeHtml(f)).append("</li>");
        }
        sb.append("</ul><script>async function up(){var f=document.getElementById('f').files[0];")
                .append("if(!f){return}document.getElementById('st').textContent='上传中…';")
                .append("try{var r=await fetch('/upload?name='+encodeURIComponent(f.name),{method:'PUT',body:f});")
                .append("var j=await r.json();document.getElementById('st').textContent=j.ok?('上传成功: '+j.file):('失败: '+j.error);}")
                .append("catch(e){document.getElementById('st').textContent='失败: '+e}}</script>")
                .append("</body></html>");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respond(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
