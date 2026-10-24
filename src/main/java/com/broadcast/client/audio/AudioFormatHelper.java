package com.broadcast.client.audio;

import org.lwjgl.openal.AL10;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 解析 MC 自带 OGG 解码器返回的音频格式(1.20.1 中为 javax.sound.sampled.AudioFormat),
 * 通过反射读取采样率/声道数/位深,转换为 OpenAL 的 buffer 格式常量。
 */
public final class AudioFormatHelper {
    private AudioFormatHelper() {
    }

    public record Probe(int alFormat, int sampleRate) {
    }

    public static Probe probe(Object audioFormat) {
        if (audioFormat == null) {
            throw new IllegalStateException("音频格式为空");
        }

        int sampleRate = -1;
        Integer sr = callInt(audioFormat, "getSampleRate");
        if (sr == null || sr <= 0) sr = fieldInt(audioFormat, "sampleRate");
        if (sr != null && sr > 0) sampleRate = sr;
        if (sampleRate <= 0) {
            throw new IllegalStateException("无法解析音频采样率");
        }

        int channels = 1;
        Integer ch = callInt(audioFormat, "getChannels");
        if (ch == null || ch <= 0) ch = fieldInt(audioFormat, "channels");
        if (ch != null && ch > 0) channels = ch;

        int bits = 16;
        Integer b = callInt(audioFormat, "getSampleSizeInBits");
        if (b == null || b <= 0) b = fieldInt(audioFormat, "sampleSizeInBits");
        if (b != null && b > 0) bits = b;

        int al;
        if (channels >= 2) {
            al = bits == 8 ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;
        } else {
            al = bits == 8 ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
        }
        return new Probe(al, sampleRate);
    }

    private static Object callObj(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Integer callInt(Object target, String method) {
        Object v = callObj(target, method);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Integer fieldInt(Object target, String field) {
        Object v = fieldObj(target, field);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Object fieldObj(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
