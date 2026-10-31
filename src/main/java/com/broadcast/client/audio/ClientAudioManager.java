package com.broadcast.client.audio;

import com.broadcast.BroadcastMod;
import com.broadcast.client.ClientDataHolder;
import com.broadcast.config.BroadcastConfig;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.RequestFilePacket;
import com.broadcast.util.NameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端音频引擎:
 * - 每个广播站一个播放会话,解码线程把 PCM 分块推给每个广播器位置的 OpenAL 音源;
 * - 每个正在广播的话筒一个音源组,网络包中的 PCM 直接入队播放;
 * 所有 OpenAL 调用都发生在客户端主线程(tick),解码只产生字节。
 */
public final class ClientAudioManager {
    private static final int CHUNK_BYTES = 16384;
    private static final int QUEUE_BYTE_LIMIT = 768 * 1024;
    private static final int MAX_QUEUED_PER_SOURCE = 24;

    private static final Map<BlockPos, MusicSession> MUSIC = new HashMap<>();
    private static final Map<BlockPos, MicSink> MICS = new HashMap<>();
    private static boolean alBroken;
    private static boolean paused;
    private static ResourceKey<Level> lastDimension;

    private ClientAudioManager() {
    }

    // ---------- 对外接口 ----------

    public static void startMusic(BlockPos station, String target, boolean url, boolean loop,
                                  float volume, List<BlockPos> speakers) {
        stopMusic(station);
        if (speakers.isEmpty()) return;
        MusicSession s = new MusicSession(station, target, url, loop, volume, speakers);
        MusicSession old = MUSIC.put(station, s);
        if (old != null) old.stop();
        s.start();
    }

    public static void stopMusic(BlockPos station) {
        MusicSession s = MUSIC.remove(station);
        if (s != null) s.stop();
    }

    public static boolean isPlaying(BlockPos station, String target) {
        MusicSession s = MUSIC.get(station);
        return s != null && s.target.equals(target) && s.running;
    }

    public static boolean hasSessionAt(BlockPos station) {
        return MUSIC.containsKey(station);
    }

    public static void setVolume(BlockPos station, float volume) {
        MusicSession s = MUSIC.get(station);
        if (s != null) s.volume = volume;
    }

    /**
     * 同一广播站、同一曲目/URL 时只更新广播器列表,不重建会话,因此新增喇叭不会重置进度。
     */
    public static boolean updateMusic(BlockPos station, String target, boolean url, boolean loop,
                                      float volume, List<BlockPos> speakers) {
        MusicSession s = MUSIC.get(station);
        if (s == null || !s.target.equals(target) || s.url != url || s.loop != loop) return false;
        s.volume = volume;
        s.updateSpeakers(speakers);
        return true;
    }

    public static void startMic(BlockPos mic, float volume, List<BlockPos> speakers) {
        stopMic(mic);
        if (speakers.isEmpty()) return;
        MICS.put(mic, new MicSink(mic, volume, speakers));
    }

    public static void stopMic(BlockPos mic) {
        MicSink s = MICS.remove(mic);
        if (s != null) s.close();
    }

    public static void feedMic(BlockPos mic, byte[] data) {
        MicSink s = MICS.get(mic);
        if (s != null) s.feed(data);
    }

    public static void stopAll() {
        MUSIC.values().forEach(MusicSession::stop);
        MUSIC.clear();
        MICS.values().forEach(MicSink::close);
        MICS.clear();
        paused = false;
    }

    /** 客户端主线程每 tick 调用:灌入缓冲区并播放。 */
    public static void tick() {
        if (alBroken) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                // 切换维度后旧的音源坐标不再有意义,直接停掉
                if (lastDimension != null && !lastDimension.equals(mc.level.dimension())) {
                    stopAll();
                }
                lastDimension = mc.level.dimension();
            } else {
                lastDimension = null;
            }
            // 单人 ESC 暂停时挂起 OpenAL 音源;恢复后继续,不丢会话
            if (mc.isPaused()) {
                if (!paused) {
                    pauseAllSources();
                    paused = true;
                }
                return;
            }
            if (paused) {
                paused = false;
                resumeAllSources();
            }
            MUSIC.values().forEach(MusicSession::tick);
            MICS.values().forEach(MicSink::tick);
        } catch (Throwable t) {
            alBroken = true;
            BroadcastMod.LOGGER.warn("客户端音频输出不可用,已停用广播声音: {}", t.getMessage());
        }
    }

    private static void pauseAllSources() {
        for (MusicSession s : MUSIC.values()) {
            for (Src src : s.sources) {
                try {
                    AL10.alSourcePause(src.id);
                } catch (Throwable ignored) {
                }
            }
        }
        for (MicSink s : MICS.values()) {
            for (Src src : s.sources) {
                try {
                    AL10.alSourcePause(src.id);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void resumeAllSources() {
        for (MusicSession s : MUSIC.values()) {
            for (Src src : s.sources) {
                if (src.queued > 0) {
                    try {
                        AL10.alSourcePlay(src.id);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        for (MicSink s : MICS.values()) {
            for (Src src : s.sources) {
                if (src.queued > 0) {
                    try {
                        AL10.alSourcePlay(src.id);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    // ---------- 距离衰减 ----------

    /** 以玩家眼睛位置计算该喇叭的增益,超出配置半径后静音。 */
    private static float distanceGain(BlockPos speaker, float volume) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0F;
        double radius = Math.max(4D, BroadcastConfig.speakerRadius());
        double dist = Math.sqrt(player.getEyePosition().distanceToSqr(
                speaker.getX() + 0.5D, speaker.getY() + 0.5D, speaker.getZ() + 0.5D));
        if (dist >= radius) return 0F;
        double inner = radius * 0.45D;
        if (dist <= inner) return volume;
        return (float) (volume * (radius - dist) / (radius - inner));
    }

    // ---------- OpenAL 音源 ----------

    private static class Src {
        final int id;
        final BlockPos pos;
        int queued;

        Src(int id, BlockPos pos) {
            this.id = id;
            this.pos = pos;
        }

        static int reclaim(int id) {
            int processed = AL10.alGetSourcei(id, AL10.AL_BUFFERS_PROCESSED);
            int reclaimed = 0;
            while (processed-- > 0) {
                int b = AL10.alSourceUnqueueBuffers(id);
                AL10.alDeleteBuffers(b);
                reclaimed++;
            }
            return reclaimed;
        }
    }

    private static List<Src> openSources(List<BlockPos> speakers) {
        List<Src> out = new ArrayList<>();
        float radius = (float) Math.max(4D, BroadcastConfig.speakerRadius());
        for (BlockPos sp : speakers) {
            try {
                int src = AL10.alGenSources();
                AL10.alSourcef(src, AL10.AL_GAIN, 0F);
                AL10.alSource3f(src, AL10.AL_POSITION, sp.getX() + 0.5F, sp.getY() + 0.5F, sp.getZ() + 0.5F);
                AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                // 距离衰减由 distanceGain 精确控制(硬半径 15~20 格)
                AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0F);
                AL10.alSourcef(src, AL10.AL_MAX_DISTANCE, radius);
                out.add(new Src(src, sp));
            } catch (Throwable t) {
                // 单个音源失败不影响其它
            }
        }
        return out;
    }

    private static void closeSources(List<Src> sources) {
        for (Src src : sources) {
            try {
                AL10.alSourceStop(src.id);
                while (src.queued > 0) {
                    int b = AL10.alSourceUnqueueBuffers(src.id);
                    AL10.alDeleteBuffers(b);
                    src.queued--;
                }
                AL10.alDeleteSources(src.id);
            } catch (Throwable ignored) {
            }
        }
        sources.clear();
    }

    // ---------- 音乐会话 ----------

    private static class MusicSession {
        final BlockPos station;
        final String target;
        final boolean url;
        final boolean loop;
        List<BlockPos> speakers;
        volatile float volume;
        volatile boolean running = true;
        final List<Src> sources;
        final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
        final AtomicInteger queuedBytes = new AtomicInteger();
        volatile int alFormat;
        volatile int sampleRate;
        volatile boolean formatReady;
        volatile String error;
        volatile boolean errorNotified;
        final AtomicLong lastChunkMs = new AtomicLong();
        volatile Thread feeder;
        byte[] pendingChunk;

        MusicSession(BlockPos station, String target, boolean url, boolean loop, float volume, List<BlockPos> speakers) {
            this.station = station;
            this.target = target;
            this.url = url;
            this.loop = loop;
            this.volume = volume;
            this.speakers = speakers;
            this.sources = openSources(speakers);
        }

        void start() {
            if (sources.isEmpty()) {
                error = "无法创建音频输出音源";
                running = false;
                return;
            }
            Thread t = new Thread(this::feedLoop, "Broadcast-Audio-" + target.hashCode());
            t.setDaemon(true);
            feeder = t;
            t.start();
        }

        /** 新增/移除喇叭时复用现有解码线程与已排队音频,不重置播放进度。 */
        void updateSpeakers(List<BlockPos> next) {
            List<Src> removed = new ArrayList<>();
            for (Src src : sources) {
                if (!next.contains(src.pos)) removed.add(src);
            }
            for (Src src : removed) {
                sources.remove(src);
                closeSource(src);
            }
            for (BlockPos pos : next) {
                boolean exists = sources.stream().anyMatch(s -> s.pos.equals(pos));
                if (!exists) sources.addAll(openSources(List.of(pos)));
            }
            speakers = new ArrayList<>(next);
        }

        private void closeSource(Src src) {
            try {
                AL10.alSourceStop(src.id);
                while (src.queued > 0) {
                    int b = AL10.alSourceUnqueueBuffers(src.id);
                    AL10.alDeleteBuffers(b);
                    src.queued--;
                }
                AL10.alDeleteSources(src.id);
            } catch (Throwable ignored) {
            }
        }

        void stop() {
            running = false;
            Thread t = feeder;
            if (t != null) t.interrupt();
        }

        // ---- 解码线程 ----

        private void feedLoop() {
            InputStream in = null;
            StreamDecoder decoder = null;
            try {
                in = openInput();
                decoder = StreamDecoders.create(target, in);
                alFormat = decoder.alFormat();
                sampleRate = decoder.sampleRate();
                formatReady = true;
                while (running) {
                    if (queuedBytes.get() > QUEUE_BYTE_LIMIT) {
                        Thread.sleep(60);
                        continue;
                    }
                    byte[] chunk = decoder.readChunk(CHUNK_BYTES);
                    if (chunk == null) {
                        if (!loop) {
                            // 播放列表单曲:通知服务端切下一首,不在这里循环
                            Minecraft.getInstance().execute(() ->
                                    NetworkHandler.sendToServer(
                                            new com.broadcast.network.packet.MusicTrackEndPacket(station)));
                            running = false;
                            break;
                        }
                        // 普通音乐循环播放
                        try {
                            decoder.close();
                        } catch (Exception ignored) {
                        }
                        decoder = null;
                        in.close();
                        in = openInput();
                        decoder = StreamDecoders.create(target, in);
                        continue;
                    }
                    queue.add(chunk);
                    queuedBytes.addAndGet(chunk.length);
                    lastChunkMs.set(System.currentTimeMillis());
                }
            } catch (Throwable t) {
                if (running) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }
            } finally {
                try {
                    if (decoder != null) decoder.close();
                } catch (Exception ignored) {
                }
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) {
                }
            }
        }

        private InputStream openInput() throws IOException {
            if (url) {
                URLConnection conn = new URL(target).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "MC-Broadcast/1.0");
                return new BufferedInputStream(conn.getInputStream());
            }
            String clean = NameUtil.sanitizeFileName(target);
            File local = new File(ClientDataHolder.clientMusicDir().toFile(), clean);
            if (!local.isFile()) {
                File cached = new File(ClientDataHolder.cacheDir().toFile(), clean);
                if (!cached.isFile()) {
                    // 请求服务端下发
                    try {
                        NetworkHandler.sendToServer(new RequestFilePacket(station, clean));
                    } catch (Throwable ignored) {
                    }
                    if (!ClientDataHolder.waitForLocalFile(clean, 120_000)) {
                        throw new IOException("等待服务端下发音乐文件超时: " + clean);
                    }
                }
                local = new File(ClientDataHolder.cacheDir().toFile(), clean);
            }
            return new BufferedInputStream(new FileInputStream(local));
        }

        // ---- 主线程 tick ----

        void tick() {
            if (error != null && !errorNotified) {
                errorNotified = true;
                stop();
                BroadcastMod.LOGGER.warn("广播播放出错: {}", error);
                ClientDataHolder.showToast("广播播放失败: " + error);
            }
            if (!running && feeder != null && feeder.isAlive()) {
                // 等待解码线程退出后再清理音源
            }
            for (Src src : sources) {
                src.queued -= Src.reclaim(src.id);
            }
            boolean playing = false;
            byte[] chunk = pendingChunk;
            pendingChunk = null;
            while (chunk != null || (chunk = queue.poll()) != null) {
                queuedBytes.addAndGet(-chunk.length);
                playing = true;
                if (!formatReady) {
                    chunk = null;
                    continue;
                }
                if (!hasRoom()) {
                    // 队列余量不足:保留这一块,下一 tick 再入队
                    pendingChunk = chunk;
                    break;
                }
                for (Src src : sources) {
                    try {
                        ByteBuffer buf = MemoryUtil.memAlloc(chunk.length);
                        buf.put(chunk).flip();
                        int b = AL10.alGenBuffers();
                        AL10.alBufferData(b, alFormat, buf, sampleRate);
                        AL10.alSourceQueueBuffers(src.id, b);
                        src.queued++;
                        MemoryUtil.memFree(buf);
                    } catch (Throwable t) {
                        if (error == null) error = "音频缓冲失败: " + t.getMessage();
                    }
                }
                chunk = null;
            }
            for (Src src : sources) {
                if (src.queued > 0) {
                    try {
                        if (AL10.alGetSourcei(src.id, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(src.id);
                        }
                        AL10.alSourcef(src.id, AL10.AL_GAIN, distanceGain(src.pos, volume));
                    } catch (Throwable ignored) {
                    }
                }
            }
            // 长期无数据(网络流卡住)时静音,避免嗡鸣
            if (running && !queue.isEmpty() && System.currentTimeMillis() - lastChunkMs.get() > 8000) {
                // 说明数据堆积(网络流被阻塞),丢弃积压数据
                queue.clear();
                queuedBytes.set(0);
            }
            // 会话结束清理
            if (!running && feeder != null && !feeder.isAlive()) {
                closeSources(sources);
                queue.clear();
                queuedBytes.set(0);
            }
        }

        private boolean hasRoom() {
            return sources.stream().allMatch(s -> s.queued < MAX_QUEUED_PER_SOURCE);
        }
    }

    // ---------- 话筒音源 ----------

    private static class MicSink {
        final BlockPos mic;
        volatile float volume;
        final List<Src> sources;
        final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
        volatile long lastFeed = System.currentTimeMillis();
        boolean closed;

        MicSink(BlockPos mic, float volume, List<BlockPos> speakers) {
            this.mic = mic;
            this.volume = volume;
            this.sources = openSources(speakers);
        }

        void feed(byte[] data) {
            if (closed) return;
            if (queue.size() > 96) {
                // 网络积压:丢弃最旧的一批
                queue.poll();
                queue.poll();
            }
            queue.add(data);
            lastFeed = System.currentTimeMillis();
        }

        void tick() {
            if (closed) return;
            for (Src src : sources) {
                src.queued -= Src.reclaim(src.id);
            }
            byte[] chunk;
            while ((chunk = queue.poll()) != null) {
                if (sources.stream().allMatch(s -> s.queued < 16)) {
                    for (Src src : sources) {
                        try {
                            ByteBuffer buf = MemoryUtil.memAlloc(chunk.length);
                            buf.put(chunk).flip();
                            int b = AL10.alGenBuffers();
                            AL10.alBufferData(b, AL10.AL_FORMAT_MONO16, buf, MicFormat.RATE);
                            AL10.alSourceQueueBuffers(src.id, b);
                            src.queued++;
                            MemoryUtil.memFree(buf);
                        } catch (Throwable t) {
                            // 忽略
                        }
                    }
                } else {
                    break;
                }
            }
            long idle = System.currentTimeMillis() - lastFeed;
            for (Src src : sources) {
                try {
                    if (src.queued > 0 && idle < 2500) {
                        if (AL10.alGetSourcei(src.id, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(src.id);
                        }
                        AL10.alSourcef(src.id, AL10.AL_GAIN, distanceGain(src.pos, volume));
                    } else if (idle > 2500 && AL10.alGetSourcei(src.id, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                        AL10.alSourceStop(src.id);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        void close() {
            closed = true;
            queue.clear();
            closeSources(sources);
        }
    }

    private static final class MicFormat {
        static final int RATE = 16000;
    }
}