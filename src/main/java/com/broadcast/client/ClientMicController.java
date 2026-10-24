package com.broadcast.client;

import com.broadcast.BroadcastMod;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.MicUploadPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端麦克风采集(专用服务器模式):
 * 由放置话筒的玩家在自己的电脑上采集麦克风,发送 PCM 给服务端转发。
 * 采集统一重采样为 16kHz / 单声道 / 16bit。
 */
public final class ClientMicController {
    private static final int RATE = 16000;
    private static final AudioFormat[] CANDIDATES = new AudioFormat[]{
            new AudioFormat(RATE, 16, 1, true, false),
            new AudioFormat(44100F, 16, 1, true, false),
            new AudioFormat(48000F, 16, 1, true, false),
            new AudioFormat(44100F, 16, 2, true, false),
            new AudioFormat(48000F, 16, 2, true, false)
    };

    private static final Map<BlockPos, Capture> ACTIVE = new ConcurrentHashMap<>();

    private ClientMicController() {
    }

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

    public static synchronized void start(BlockPos mic, float volume, java.util.List<BlockPos> speakers) {
        if (ACTIVE.containsKey(mic)) return;
        AudioFormat format = supportedFormat();
        if (format == null) {
            BroadcastMod.LOGGER.warn("客户端没有可用的麦克风,话筒将以静音状态运行");
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("msg.broadcast.mic_unavailable_client"), true);
            }
            // 通知服务端复位,避免界面显示「广播中」却没有声音
            NetworkHandler.sendToServer(new MicUploadPacket(mic, new byte[0]));
            return;
        }
        Capture c = new Capture(mic, format);
        ACTIVE.put(mic, c);
        c.start();
    }

    public static synchronized void stop(BlockPos mic) {
        Capture c = ACTIVE.remove(mic);
        if (c != null) c.shutdown();
    }

    public static synchronized void stopAll() {
        ACTIVE.values().forEach(Capture::shutdown);
        ACTIVE.clear();
    }

    private static class Capture extends Thread {
        private final BlockPos mic;
        private final AudioFormat format;
        private volatile boolean running = true;
        private volatile TargetDataLine line;

        Capture(BlockPos mic, AudioFormat format) {
            super("Broadcast-ClientMic");
            this.mic = mic;
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
            interrupt();
        }

        @Override
        public void run() {
            TargetDataLine l = null;
            try {
                l = AudioSystem.getTargetDataLine(format);
                l.open(format);
                l.start();
                this.line = l;
                byte[] buf = new byte[4096];
                int srcRate = (int) format.getSampleRate();
                int srcChannels = format.getChannels();
                while (running) {
                    int n;
                    try {
                        n = l.read(buf, 0, buf.length);
                    } catch (Throwable t) {
                        break;
                    }
                    if (n <= 0) continue;
                    byte[] pcm = convert(buf, n, srcRate, srcChannels);
                    if (pcm.length == 0) continue;
                    NetworkHandler.sendToServer(new MicUploadPacket(mic, pcm));
                }
            } catch (Throwable t) {
                BroadcastMod.LOGGER.warn("客户端麦克风采集失败: {}", t.getMessage());
                // 通知服务端复位,避免一直显示「广播中」
                try {
                    NetworkHandler.sendToServer(new MicUploadPacket(mic, new byte[0]));
                } catch (Throwable ignored) {
                }
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
                ACTIVE.remove(mic);
            }
        }

        private static byte[] convert(byte[] src, int len, int srcRate, int channels) {
            int frames = len / 2 / Math.max(1, channels);
            if (frames <= 0) return new byte[0];
            int outFrames = (int) ((long) frames * RATE / Math.max(1, srcRate));
            if (outFrames <= 0) return new byte[0];
            ByteArrayOutputStream out = new ByteArrayOutputStream(outFrames * 2);
            for (int i = 0; i < outFrames; i++) {
                double srcPos = (double) i * srcRate / RATE;
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
            if (channels <= 1) return readShort(src, frame * 2);
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += readShort(src, (frame * channels + c) * 2);
            return sum / channels;
        }

        private static int readShort(byte[] b, int off) {
            if (off < 0 || off + 1 >= b.length) return 0;
            return (short) ((b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8));
        }
    }
}
