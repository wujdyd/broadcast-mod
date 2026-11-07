package com.broadcast.media;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class MediaSupportConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> YTDLP;
    public static final ForgeConfigSpec.ConfigValue<String> FFMPEG;
    public static final ForgeConfigSpec.BooleanValue INSECURE_SSL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Broadcast Media Support - 视频音频解析工具").push("paths");
        YTDLP = b.comment("yt-dlp 路径;auto 表示使用自动下载的工具").define("ytdlp", "auto");
        FFMPEG = b.comment("ffmpeg 路径;auto 表示使用自动下载的工具").define("ffmpeg", "auto");
        b.pop();
        b.push("network");
        INSECURE_SSL = b.comment("下载工具/解析视频时跳过证书校验(解决 Unable to find valid Certification Path)",
                        "默认开启;服务器 CA 正常时可关闭")
                .define("insecureSsl", true);
        b.pop();
        SPEC = b.build();
    }

    private MediaSupportConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC,
                "broadcast_media_support-common.toml");
    }
}
