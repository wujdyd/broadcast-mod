package com.broadcast.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** 使用 Minecraft 自带的 JOrbis 解码 OGG Vorbis。 */
public class OggStreamDecoder implements StreamDecoder {

    private final OggAudioStream stream;
    private final int alFormat;
    private final int sampleRate;

    public OggStreamDecoder(InputStream in) throws IOException {
        this.stream = new OggAudioStream(in);
        AudioFormatHelper.Probe p = AudioFormatHelper.probe(stream.getFormat());
        this.alFormat = p.alFormat();
        this.sampleRate = p.sampleRate();
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
        ByteBuffer bb = stream.read(maxBytes);
        if (bb == null || !bb.hasRemaining()) return null;
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}