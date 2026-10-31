package com.broadcast.client;

import com.broadcast.util.NameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧缓存:广播站的音乐列表 / 设备信息 / 播放状态,
 * 以及音乐文件分块接收(服务端下发到本机缓存目录)。
 */
public final class ClientDataHolder {
    public record StationView(List<String> music, List<String> musicUids, List<BlockPos> speakers,
                              List<BlockPos> mics, List<String> frequencies, int uploadPort,
                              List<String> playlist, boolean playlistLoop, boolean playlistReverse,
                              int playlistGap, int playlistTrackSeconds, int playlistIndex,
                              boolean playlistPlaying) {
    }

    public record DeviceView(BlockPos pos, String kind, String frequency, boolean powered,
                             boolean active, int redstoneMode, boolean pauseMusic, boolean startSound,
                             int speakers, int poweredSpeakers, int stations, int volumePercent,
                             int detectionRadius) {
    }

    public record PlayState(boolean playing, String target, boolean url, int volumePercent,
                            List<BlockPos> speakers) {
    }

    private static final Map<BlockPos, StationView> VIEWS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, DeviceView> DEVICES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, PlayState> PLAY_STATES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Boolean> MIC_ACTIVE = new ConcurrentHashMap<>();

    private static final Set<String> CACHED = ConcurrentHashMap.newKeySet();
    private static final Map<String, ReceiveHandle> RECEIVES = new ConcurrentHashMap<>();

    /** 网络音乐播放历史。 */
    private static final List<String> URL_HISTORY = new ArrayList<>();
    private static volatile boolean historyLoaded;

    private ClientDataHolder() {
    }

    // ---------- 视图 ----------

    public static StationView getView(BlockPos station) {
        return VIEWS.get(station);
    }

    public static void setMusicList(BlockPos station, List<String> uids, List<String> files) {
        VIEWS.compute(station, (k, v) -> new StationView(
                files, uids,
                v == null ? List.of() : v.speakers(),
                v == null ? List.of() : v.mics(),
                v == null ? List.of() : v.frequencies(),
                v == null ? 0 : v.uploadPort(),
                v == null ? List.of() : v.playlist(),
                v != null && v.playlistLoop(),
                v != null && v.playlistReverse(),
                v == null ? 60 : v.playlistGap(),
                v == null ? 30 : v.playlistTrackSeconds(),
                v == null ? 0 : v.playlistIndex(),
                v != null && v.playlistPlaying()));
    }

    public static void setStationInfo(BlockPos station, List<BlockPos> speakers, List<BlockPos> mics,
                                      List<String> frequencies, int uploadPort,
                                      List<String> playlist, boolean playlistLoop, boolean playlistReverse,
                                      int playlistGap, int playlistTrackSeconds, int playlistIndex,
                                      boolean playlistPlaying) {
        VIEWS.compute(station, (k, v) -> new StationView(
                v == null ? List.of() : v.music(),
                v == null ? List.of() : v.musicUids(),
                speakers, mics, frequencies, uploadPort,
                playlist, playlistLoop, playlistReverse, playlistGap, playlistTrackSeconds,
                playlistIndex, playlistPlaying));
    }

    public static DeviceView getDevice(BlockPos pos) {
        return DEVICES.get(pos);
    }

    public static void setDevice(BlockPos pos, DeviceView view) {
        DEVICES.put(pos, view);
    }

    public static PlayState getPlayState(BlockPos station) {
        return PLAY_STATES.get(station);
    }

    public static void setPlaybackState(BlockPos station, boolean playing, String target, boolean url,
                                        int volumePercent, List<BlockPos> speakers) {
        PLAY_STATES.put(station, new PlayState(playing, target, url, volumePercent, speakers));
    }

    public static void setMicActive(BlockPos mic, boolean active, BlockPos station) {
        MIC_ACTIVE.put(mic, active);
    }

    public static boolean isMicActive(BlockPos mic) {
        return Boolean.TRUE.equals(MIC_ACTIVE.get(mic));
    }

    // ---------- 音乐文件接收 ----------

    public static Path clientMusicDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("broadcast_music");
    }

    public static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("broadcast_cache");
    }

    /** 客户端本地是否有该文件(音乐库命中)。 */
    public static boolean hasLocalFile(String name) {
        String clean = NameUtil.sanitizeFileName(name);
        if (clean.isEmpty()) return false;
        return Files.isRegularFile(clientMusicDir().resolve(clean))
                || Files.isRegularFile(cacheDir().resolve(clean))
                || CACHED.contains(clean);
    }

    public static void appendMusicChunk(String name, byte[] data, boolean last) {
        String clean = NameUtil.sanitizeFileName(name);
        if (clean.isEmpty()) return;
        if (last && data.length == 0) {
            finishReceive(clean);
            return;
        }
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            ReceiveHandle handle = RECEIVES.get(clean);
            if (handle == null) {
                // 新的传输:清掉上次中断留下的 .part,避免拼接损坏文件
                Path part = dir.resolve(clean + ".part");
                Files.deleteIfExists(part);
                handle = new ReceiveHandle(part);
                RECEIVES.put(clean, handle);
            }
            synchronized (handle) {
                try (OutputStream out = new FileOutputStream(handle.partFile.toFile(), true)) {
                    out.write(data);
                }
            }
            if (last) {
                // 结束:改名后标记就绪
                synchronized (handle) {
                    File finalFile = new File(dir.toFile(), clean);
                    Files.move(handle.partFile, finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                CACHED.add(clean);
                RECEIVES.remove(clean);
            }
        } catch (IOException e) {
            com.broadcast.BroadcastMod.LOGGER.warn("接收音乐文件失败 {}: {}", clean, e.getMessage());
        }
    }

    private static void finishReceive(String clean) {
        // 空尾包:视为接收结束但无内容,清理残留的分块文件
        RECEIVES.remove(clean);
        try {
            Files.deleteIfExists(cacheDir().resolve(clean + ".part"));
        } catch (IOException ignored) {
        }
    }

    /** 等待服务端下发完成(供播放线程阻塞)。返回 true 表示就绪。 */
    public static boolean waitForLocalFile(String name, long timeoutMillis) {
        String clean = NameUtil.sanitizeFileName(name);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (hasLocalFile(clean)) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return hasLocalFile(clean);
    }

    private record ReceiveHandle(Path partFile) {
    }

    // ---------- URL 历史 ----------

    public static List<String> urlHistory() {
        ensureHistory();
        return URL_HISTORY;
    }

    public static void pushUrlHistory(String url) {
        ensureHistory();
        URL_HISTORY.remove(url);
        URL_HISTORY.add(0, url);
        while (URL_HISTORY.size() > 12) URL_HISTORY.remove(URL_HISTORY.size() - 1);
        saveHistory();
    }

    private static void ensureHistory() {
        if (historyLoaded) return;
        synchronized (URL_HISTORY) {
            if (historyLoaded) return;
            try {
                Path f = FMLPaths.CONFIGDIR.get().resolve("broadcast-client-urls.txt");
                if (Files.isRegularFile(f)) {
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String s = line.trim();
                        if (!s.isEmpty() && URL_HISTORY.size() < 12) URL_HISTORY.add(s);
                    }
                }
            } catch (IOException ignored) {
            }
            historyLoaded = true;
        }
    }

    private static void saveHistory() {
        try {
            Path f = FMLPaths.CONFIGDIR.get().resolve("broadcast-client-urls.txt");
            Files.write(f, String.join("\n", URL_HISTORY).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    // ---------- 上传 ----------

    /** 判断当前连的是不是本机服务器(单机)。 */
    public static boolean isLocalServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null;
    }

    public static String currentServerHost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) return "127.0.0.1";
        ServerData sd = mc.getCurrentServer();
        if (sd == null) return "127.0.0.1";
        String ip = sd.ip;
        int colon = ip.indexOf(':');
        return colon > 0 ? ip.substring(0, colon) : ip;
    }

    /** 通过 HTTP 上传文件,返回用户可读的结果文案。 */
    public static String uploadFile(File file, int port) throws IOException {
        String name = NameUtil.sanitizeFileName(file.getName());
        if (name.isEmpty()) throw new IOException("文件名不合法");
        String host = isLocalServer() ? "127.0.0.1" : currentServerHost();
        String urlStr = "http://" + host + ":" + port + "/upload?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(60000);
        long size = file.length();
        try (InputStream in = new FileInputStream(file);
             OutputStream out = conn.getOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        int code = conn.getResponseCode();
        String body;
        try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in == null
                     ? new java.io.ByteArrayInputStream(new byte[0]) : in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            body = sb.toString();
        } finally {
            conn.disconnect();
        }
        if (code >= 200 && code < 300) {
            return net.minecraft.network.chat.Component.translatable(
                    "gui.broadcast.upload_success", name, size / 1024).getString();
        }
        return net.minecraft.network.chat.Component.translatable(
                "gui.broadcast.upload_fail", code, body).getString();
    }

    /** 打开系统文件选择器并上传(在独立线程执行,避免阻塞渲染线程)。 */
    public static void pickAndUploadAsync(int port, java.util.function.Consumer<String> onResult) {
        Thread t = new Thread(() -> {
            String result;
            final File[] picked = new File[1];
            try {
                // Swing 对话框必须在 EDT 上创建/显示
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    try {
                        javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                    } catch (Throwable ignored) {
                    }
                    JFrame frame = new JFrame();
                    frame.setAlwaysOnTop(true);
                    javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                    fc.setDialogTitle(net.minecraft.network.chat.Component.translatable(
                            "gui.broadcast.upload_dialog").getString());
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                            "Audio (*.ogg, *.wav, *.mp3)", "ogg", "wav", "mp3"));
                    int choice = fc.showOpenDialog(frame);
                    if (choice == javax.swing.JFileChooser.APPROVE_OPTION) {
                        picked[0] = fc.getSelectedFile();
                    }
                });
                if (picked[0] == null) {
                    result = net.minecraft.network.chat.Component.translatable(
                            "gui.broadcast.upload_cancel").getString();
                } else {
                    result = uploadFile(picked[0], port);
                }
            } catch (Throwable t2) {
                result = net.minecraft.network.chat.Component.translatable(
                        "gui.broadcast.upload_error", String.valueOf(t2.getMessage())).getString();
            }
            onResult.accept(result);
        }, "Broadcast-Upload");
        t.setDaemon(true);
        t.start();
    }

    /** 显示一句 GUI 提示(复用标题栏提示)。 */
    public static void showToast(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.getChatListener().handleSystemMessage(
                net.minecraft.network.chat.Component.literal(text),
                false);
    }
}