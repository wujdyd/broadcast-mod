package com.broadcast.client.audio;

import java.io.Closeable;
import java.io.IOException;

/** 音频解码器抽象:解码为 PCM,供 OpenAL 缓冲。 */
public interface StreamDecoder extends Closeable {

    /** OpenAL 缓冲区格式常量,如 AL_FORMAT_MONO16。 */
    int alFormat();

    /** 采样率(Hz)。 */
    int sampleRate();

    /**
     * 读取最多 maxBytes 字节的 PCM 数据。
     *
     * @return PCM 字节;流结束返回 null
     */
    byte[] readChunk(int maxBytes) throws Exception;

    @Override
    void close() throws IOException;
}