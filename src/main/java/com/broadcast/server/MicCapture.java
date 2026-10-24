package com.broadcast.server;

import com.broadcast.BroadcastMod;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.MicAudioPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 话筒采集:在运行服务端(单机即本机)的电脑上打开麦克风输入线路,
 * 优先使用 16kHz 单声道 16bit;若声卡不支持则退回到 44.1k/48k 或立体声,
 * 并在发送前统一重采样成 16kHz 单声道 PCM,交给客户端 OpenAL 播放。
 */
public final class MicCapture {
    /** 统一广播格式:16kHz / 单声道 / 16bit signed little-endian。 */
    public static final int SAMPLE_RATE = 16000;

    private static final AudioFormat[] CANDIDATES = new AudioFormat[]{
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false),
            new AudioFormat(44100F, 16, 1, true, false),
            new AudioFormat(48000F, 16, 1, true, false),
            new AudioFormat(44100F, 16, 2, true, false),
            new AudioFormat(48000F, 16, 2, true, false),
            new AudioFormat(44100F, 16, 1, true, true)
    };

    private static final Map<String, Capture> ACTIVE = new ConcurrentHashMap<>();

    private MicCapture() {
    }

    /** 探测本机可用于采集的音频格式(没有麦克风时返回 null)。 */
    private static AudioFormat supportedFormat() {
        for (AudioFormat f : CANDIDATES) {
            try {
                if (AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, f))) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isAvailable() {
        return supportedFormat() != null;
    }

    private static String key(MicrophoneBlockEntity mic) {
        if (mic.getLevel() == null) return "unknown";
        return mic.getLevel().dimension().location() + "@" + mic.getBlockPos().toShortString();
    }

    public static void tryStart(MicrophoneBlockEntity mic) {
        String k = key(mic);
        if (ACTIVE.containsKey(k)) return;

        AudioFormat format = supportedFormat();
        if (format == null) {
            BroadcastMod.LOGGER.warn("本机没有可用的麦克风输入,话筒无法广播");
            notifyNear(mic, Component.translatable("msg.broadcast.mic_unavailable").withStyle(ChatFormatting.RED));
            mic.setActive(false);
            mic.broadcastState();
            return;
        }
        if (mic.getLinkedStation() == null && mic.getOutputSpeakers().isEmpty()) {
            BroadcastMod.LOGGER.warn("话筒没有同频率广播器,红石信号无效");
            notifyNear(mic, Component.translatable("msg.broadcast.no_speaker_mic").withStyle(ChatFormatting.RED));
            mic.setActive(false);
            mic.broadcastState();
            return;
        }

        Capture c = new Capture(mic, k, format);
        ACTIVE.put(k, c);
        mic.setActive(true);
        c.start();
        mic.broadcastState();
        notifyNear(mic, Component.translatable("msg.broadcast.mic_on").withStyle(ChatFormatting.GREEN));
        BroadcastMod.LOGGER.info("话筒 {} 开始广播(采集格式 {} Hz / {} 声道, 输出 {} 台广播器)",
                k, (int) format.getSampleRate(), format.getChannels(), mic.getOutputSpeakers().size());
    }

    public static void stop(MicrophoneBlockEntity mic) {
        Capture c = ACTIVE.remove(key(mic));
        if (c != null) {
            c.shutdown();
            mic.setActive(false);
            mic.broadcastState();
            BroadcastMod.LOGGER.info("话筒停止广播");
        }
    }

    public static void stopAll() {
        ACTIVE.values().forEach(Capture::shutdown);
        ACTIVE.clear();
    }

    private static void notifyNear(MicrophoneBlockEntity mic, Component msg) {
        if (!(mic.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = mic.getBlockPos();
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (p.level() == sl && p.blockPosition().distSqr(pos) < 32 * 32) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    private static class Capture extends Thread {
        private final MicrophoneBlockEntity mic;
        private final String id;
        private final AudioFormat format;
        private volatile boolean running = true;
        private volatile TargetDataLine line;

        Capture(MicrophoneBlockEntity mic, String id, AudioFormat format) {
            super("Broadcast-Mic-" + id);
            this.mic = mic;
            this.id = id;
            this.format = format;
            setDaemon(true);
        }

        void shutdown() {
            running = false;
            TargetDataLine l = line;
            if (l != null) {
                try {
                    l.stop();
                    l.close();
                } catch (Throwable ignored) {
                }
            }
            this.interrupt();
        }

        @Override
        public void run() {
            TargetDataLine l = null;
            ServerLevel serverLevel = mic.getLevel() instanceof ServerLevel sl ? sl : null;
            net.minecraft.server.MinecraftServer server = serverLevel == null ? null : serverLevel.getServer();
            BlockPos micPos = mic.getBlockPos();
            try {
                l = AudioSystem.getTargetDataLine(format);
                l.open(format);
                l.start();
                this.line = l;
                byte[] buf = new byte[4096];
                int srcRate = (int) format.getSampleRate();
                int srcChannels = format.getChannels();
                while (running && mic.isActive()) {
                    int n;
                    try {
                        n = l.read(buf, 0, buf.length);
                    } catch (Throwable t) {
                        break;
                    }
                    if (n <= 0) continue;
                    byte[] pcm = toBroadcastFormat(buf, n, srcRate, srcChannels);
                    if (pcm.length == 0) continue;
                    // 采集线程不直接碰世界/玩家列表,交给服务端主线程发送
                    if (server != null) {
                        try {
                            server.execute(() -> {
                                try {
                                    NetworkHandler.sendToLevel(serverLevel, new MicAudioPacket(micPos, pcm));
                                } catch (Throwable ignored) {
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable t) {
                BroadcastMod.LOGGER.warn("麦克风采集失败: {}", t.getMessage());
            } finally {
                running = false;
                if (l != null) {
                    try {
                        l.stop();
                        l.close();
                    } catch (Throwable ignored) {
                    }
                }
                this.line = null;
                ACTIVE.remove(id);
                // 采集中途失效(如麦克风被拔除)时同步状态(回主线程)
                if (server != null) {
                    try {
                        server.execute(() -> {
                            try {
                                mic.setActive(false);
                                mic.broadcastState();
                            } catch (Throwable ignored) {
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        private static byte[] toBroadcastFormat(byte[] src, int len, int srcRate, int channels) {
            int frames = len / 2 / Math.max(1, channels);
            if (frames <= 0) return new byte[0];
            int outFrames = (int) ((long) frames * SAMPLE_RATE / Math.max(1, srcRate));
            if (outFrames <= 0) return new byte[0];
            ByteArrayOutputStream out = new ByteArrayOutputStream(outFrames * 2);
            for (int i = 0; i < outFrames; i++) {
                double srcPos = (double) i * srcRate / SAMPLE_RATE;
                int i0 = Math.min(frames - 1, (int) srcPos);
                int i1 = Math.min(frames - 1, i0 + 1);
                double frac = srcPos - i0;
                int s0 = frameSample(src, i0, channels);
                int s1 = frameSample(src, i1, channels);
                int mixed = (int) Math.round(s0 + (s1 - s0) * frac);
                if (mixed > 32767) mixed = 32767;
                if (mixed < -32768) mixed = -32768;
                out.write(mixed & 0xFF);
                out.write((mixed >> 8) & 0xFF);
            }
            return out.toByteArray();
        }

        private static int frameSample(byte[] src, int frame, int channels) {
            if (channels <= 1) {
                return readShort(src, frame * 2);
            }
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += readShort(src, (frame * channels + c) * 2);
            }
            return sum / channels;
        }

        private static int readShort(byte[] b, int off) {
            if (off < 0 || off + 1 >= b.length) return 0;
            return (short) ((b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8));
        }
    }
}
