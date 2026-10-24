package com.broadcast.client.audio;

import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * MP3 解码器(基于内置 JLayer)。
 * 关键处理:
 *   - 先跳过 ID3v2 标签(否则 Bitstream.readFrame 会读到无效头,Header 采样率索引未初始化);
 *   - 构造时探测首个有效帧,提前确定采样率/声道(避免首块按错误格式入队);
 *   - 每个帧都校验 Header,异常帧跳过;连续失败时结束本段,由播放器重新建实例。
 */
public class Mp3StreamDecoder implements StreamDecoder {

    private static final boolean AVAILABLE;
    private static Method READ_FRAME;
    private static Method UNREAD_FRAME;
    private static Method CLOSE_FRAME;
    private static Method DECODE_FRAME;
    private static Method GET_FREQUENCY;
    private static Method GET_MODE;
    private static Method GET_BUFFER;
    private static Method GET_BUFFER_LENGTH;
    private static Method BITSTREAM_CLOSE;

    static {
        boolean ok = false;
        try {
            Class<?> bs = Class.forName("javazoom.jl.decoder.Bitstream");
            Class<?> dec = Class.forName("javazoom.jl.decoder.Decoder");
            Class<?> header = Class.forName("javazoom.jl.decoder.Header");
            Class<?> sample = Class.forName("javazoom.jl.decoder.SampleBuffer");
            READ_FRAME = bs.getMethod("readFrame");
            UNREAD_FRAME = bs.getMethod("unreadFrame");
            CLOSE_FRAME = bs.getMethod("closeFrame");
            BITSTREAM_CLOSE = bs.getMethod("close");
            DECODE_FRAME = dec.getMethod("decodeFrame", header, bs);
            // JLayer 的 Header 方法是 frequency()/mode(),不是 getFrequency()
            GET_FREQUENCY = header.getMethod("frequency");
            GET_MODE = header.getMethod("mode");
            GET_BUFFER = sample.getMethod("getBuffer");
            GET_BUFFER_LENGTH = sample.getMethod("getBufferLength");
            ok = true;
        } catch (Throwable ignored) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private final Object bitstream;
    private final Object decoder;
    private int alFormat = AL10.AL_FORMAT_MONO16;
    private int sampleRate = 44100;
    private boolean formatSet;
    private int consecutiveErrors;

    public Mp3StreamDecoder(InputStream raw) throws IOException {
        if (!AVAILABLE) throw new IOException("MP3 解码器不可用");
        try {
            Class<?> bsClass = Class.forName("javazoom.jl.decoder.Bitstream");
            Class<?> decClass = Class.forName("javazoom.jl.decoder.Decoder");
            InputStream in = skipId3v2(raw);
            this.bitstream = bsClass.getConstructor(InputStream.class).newInstance(in);
            this.decoder = decClass.getConstructor().newInstance();
            probeFormat();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("无法初始化 MP3 解码器: " + t.getMessage());
        }
    }

    /** 跳过 ID3v2(含可选 footer),返回位于音频数据开始的流。 */
    private static InputStream skipId3v2(InputStream raw) throws IOException {
        BufferedInputStream in = raw instanceof BufferedInputStream b ? b : new BufferedInputStream(raw);
        in.mark(12);
        byte[] head = new byte[10];
        int n = in.read(head);
        boolean id3 = n == 10 && (head[0] & 0xFF) == 'I' && (head[1] & 0xFF) == 'D' && (head[2] & 0xFF) == '3';
        if (!id3) {
            in.reset();
            return in;
        }
        int size = ((head[6] & 0x7F) << 21) | ((head[7] & 0x7F) << 14)
                | ((head[8] & 0x7F) << 7) | (head[9] & 0x7F);
        long tagSize = 10L + size;
        if ((head[5] & 0x10) != 0) tagSize += 10L;
        long remaining = tagSize;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) break;
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
        return in;
    }

    /** 读取首个有效帧并回退,提前确定输出格式。 */
    private void probeFormat() {
        try {
            Object header = READ_FRAME.invoke(bitstream);
            if (header != null) {
                applyFormat(header);
                UNREAD_FRAME.invoke(bitstream);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyFormat(Object header) throws Exception {
        int freq = ((Number) GET_FREQUENCY.invoke(header)).intValue();
        int mode = ((Number) GET_MODE.invoke(header)).intValue();
        if (freq < 8000 || freq > 192000) return;
        this.sampleRate = freq;
        this.alFormat = mode == 3 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        this.formatSet = true;
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
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes + 4096, 65536));
        boolean any = false;
        while (out.size() < maxBytes) {
            Object header;
            try {
                header = READ_FRAME.invoke(bitstream);
            } catch (Throwable t) {
                break;
            }
            if (header == null) break; // EOF

            int freq;
            int mode;
            try {
                freq = ((Number) GET_FREQUENCY.invoke(header)).intValue();
                mode = ((Number) GET_MODE.invoke(header)).intValue();
                if (freq < 8000 || freq > 192000) {
                    CLOSE_FRAME.invoke(bitstream);
                    continue;
                }
            } catch (Throwable t) {
                CLOSE_FRAME.invoke(bitstream);
                continue;
            }

            Object ob;
            try {
                ob = DECODE_FRAME.invoke(decoder, header, bitstream);
            } catch (Throwable t) {
                CLOSE_FRAME.invoke(bitstream);
                if (++consecutiveErrors > 16) break;
                continue;
            }
            consecutiveErrors = 0;
            if (!formatSet) {
                this.sampleRate = freq;
                this.alFormat = mode == 3 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                formatSet = true;
            }
            short[] samples = (short[]) GET_BUFFER.invoke(ob);
            int len = ((Number) GET_BUFFER_LENGTH.invoke(ob)).intValue();
            any = true;
            byte[] bytes = new byte[len * 2];
            for (int i = 0; i < len; i++) {
                short s = samples[i];
                bytes[i * 2] = (byte) (s & 0xFF);
                bytes[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            out.write(bytes);
            CLOSE_FRAME.invoke(bitstream);
        }
        if (!any && out.size() == 0) return null;
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        try {
            BITSTREAM_CLOSE.invoke(bitstream);
        } catch (Throwable ignored) {
        }
    }
}
