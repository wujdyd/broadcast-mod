package com.broadcast.client.audio;

import org.lwjgl.openal.AL10;

import java.io.IOException;
import java.io.InputStream;

/** WAV (PCM) 解码器:解析 RIFF 头,直接透传 PCM 数据。支持 8/16bit、单/双声道。 */
public class WavStreamDecoder implements StreamDecoder {

    private final InputStream in;
    private final int alFormat;
    private final int sampleRate;
    private long dataRemaining;

    public WavStreamDecoder(InputStream in) throws IOException {
        this.in = in;
        byte[] head = new byte[12];
        if (readFully(head) < 12) throw new IOException("不是有效的 WAV 文件");
        if (!"RIFF".equals(new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("不是有效的 WAV 文件(缺少 RIFF/WAVE)");
        }
        int channels = -1;
        int rate = -1;
        int bits = -1;
        while (true) {
            byte[] chunkHead = new byte[8];
            int got = readFully(chunkHead);
            if (got < 8) throw new IOException("WAV 文件缺少 data 块");
            String id = new String(chunkHead, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long len = leUInt(chunkHead, 4);
            if ("fmt ".equals(id)) {
                byte[] fmt = new byte[(int) Math.min(len, 64)];
                int fmtGot = readFully(fmt);
                if (fmtGot < 16) throw new IOException("WAV fmt 块损坏");
                int audioFormat = leUShort(fmt, 0);
                channels = leUShort(fmt, 2);
                rate = leInt(fmt, 4);
                bits = leUShort(fmt, 14);
                if (audioFormat != 1) throw new IOException("仅支持 PCM WAV(格式号 " + audioFormat + ")");
                skip(len - fmtGot);
            } else if ("data".equals(id)) {
                dataRemaining = len;
                break;
            } else {
                skip(len + (len & 1));
            }
        }
        if (channels <= 0 || channels > 2 || rate <= 0 || (bits != 8 && bits != 16)) {
            throw new IOException("不支持的 WAV 参数: " + channels + "ch/" + rate + "Hz/" + bits + "bit");
        }
        this.sampleRate = rate;
        if (channels == 2) {
            this.alFormat = bits == 16 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_STEREO8;
        } else {
            this.alFormat = bits == 16 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_MONO8;
        }
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
    public byte[] readChunk(int maxBytes) throws IOException {
        if (dataRemaining <= 0) return null;
        int want = (int) Math.min(maxBytes, dataRemaining);
        byte[] out = new byte[want];
        int got = readFully(out);
        dataRemaining -= got;
        if (got == 0) return null;
        if (got < want) {
            byte[] trimmed = new byte[got];
            System.arraycopy(out, 0, trimmed, 0, got);
            return trimmed;
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private int readFully(byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private void skip(long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) break;
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static int leUShort(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long leUInt(byte[] b, int off) {
        return leInt(b, off) & 0xFFFFFFFFL;
    }
}