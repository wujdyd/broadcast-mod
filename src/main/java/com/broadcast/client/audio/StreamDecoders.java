package com.broadcast.client.audio;

import com.broadcast.util.NameUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 按文件头(优先)与扩展名选择解码器,支持没有扩展名的网络直链。 */
public final class StreamDecoders {
    private StreamDecoders() {
    }

    public static StreamDecoder create(String target, InputStream raw) throws IOException {
        BufferedInputStream in = raw instanceof BufferedInputStream b ? b : new BufferedInputStream(raw);
        String type = sniff(in);
        if (type == null) {
            String ext = NameUtil.extension(target);
            if (ext.equals("wav")) type = "wav";
            else if (ext.equals("mp3")) type = "mp3";
            else if (ext.equals("flac")) type = "flac";
            else type = "ogg";
        }
        switch (type) {
            case "wav" -> {
                return new WavStreamDecoder(in);
            }
            case "mp3" -> {
                if (!Mp3StreamDecoder.isAvailable()) {
                    throw new IOException("MP3 解码器不可用:请重新安装完整模组 jar");
                }
                return new Mp3StreamDecoder(in);
            }
            case "flac" -> {
                return new FlacStreamDecoder(in);
            }
            default -> {
                return new OggStreamDecoder(in);
            }
        }
    }

    /** 读取前 12 字节判断格式,读取后复位。 */
    private static String sniff(BufferedInputStream in) {
        try {
            in.mark(16);
            byte[] head = new byte[12];
            int n = in.read(head);
            in.reset();
            if (n >= 4) {
                String tag = new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if (tag.equals("OggS")) return "ogg";
                if (tag.equals("fLaC")) return "flac";
                if (tag.equals("RIFF") && n >= 12
                        && new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")) {
                    return "wav";
                }
                if (tag.equals("ID3")) return "mp3";
                if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
                    return "mp3";
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
