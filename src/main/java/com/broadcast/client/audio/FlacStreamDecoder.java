package com.broadcast.client.audio;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;
import org.lwjgl.openal.AL10;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * FLAC 解码器(内置 jFLAC)。
 * 解码在独立线程进行,主线程只从队列取 PCM,避免阻塞客户端 tick。
 */
public class FlacStreamDecoder implements StreamDecoder {
    private static final int MAX_QUEUED = 96;

    private final InputStream input;
    private final int alFormat;
    private final int sampleRate;
    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final Thread decoderThread;
    private volatile boolean finished;
    private volatile boolean closed;

    public FlacStreamDecoder(InputStream raw) throws IOException {
        this.input = raw;
        FLACDecoder decoder = new FLACDecoder(raw);
        try {
            decoder.readMetadata();
        } catch (Throwable t) {
            throw new IOException("不是有效的 FLAC 文件: " + t.getMessage());
        }
        StreamInfo info = decoder.getStreamInfo();
        if (info == null) throw new IOException("FLAC 缺少 StreamInfo");
        int channels = info.getChannels();
        int bits = info.getBitsPerSample();
        if (channels < 1 || channels > 2) throw new IOException("FLAC 声道数不支持: " + channels);
        if (bits != 8 && bits != 16) {
            throw new IOException("FLAC 仅支持 8/16 bit(当前 " + bits + " bit)");
        }
        this.sampleRate = info.getSampleRate();
        if (channels == 1) this.alFormat = bits == 8 ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
        else this.alFormat = bits == 8 ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;

        decoder.addPCMProcessor(new PCMProcessor() {
            @Override
            public void processStreamInfo(StreamInfo streamInfo) {
            }

            @Override
            public void processPCM(ByteData pcm) {
                if (closed) return;
                byte[] src = pcm.getData();
                int len = Math.min(pcm.getLen(), src.length);
                if (len <= 0) return;
                byte[] copy = new byte[len];
                System.arraycopy(src, 0, copy, 0, len);
                while (!closed && queue.size() >= MAX_QUEUED) {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!closed) queue.add(copy);
            }
        });

        this.decoderThread = new Thread(() -> {
            try {
                decoder.decodeFrames();
            } catch (Throwable ignored) {
            } finally {
                finished = true;
            }
        }, "Broadcast-FLAC-" + System.identityHashCode(this));
        this.decoderThread.setDaemon(true);
        this.decoderThread.start();
    }

    @Override
    public int alFormat() {
        return alFormat;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public byte[] readChunk(int maxBytes) throws Exception {
        while (!closed) {
            byte[] data = queue.poll(100, TimeUnit.MILLISECONDS);
            if (data != null) {
                if (data.length <= maxBytes) return data;
                // 单帧通常不大;若超过上限则截断,剩余部分丢弃
                byte[] out = new byte[maxBytes];
                System.arraycopy(data, 0, out, 0, maxBytes);
                return out;
            }
            if (finished) return null;
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        decoderThread.interrupt();
        input.close();
    }
}
