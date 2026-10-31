package com.broadcast.media;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class MediaSupportConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_DOWNLOAD;
    public static final ForgeConfigSpec.ConfigValue<String> YTDLP;
    public static final ForgeConfigSpec.ConfigValue<String> FFMPEG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Broadcast Media Support - 视频音频解析工具").push("auto");
        AUTO_DOWNLOAD = b.comment("首次播放视频链接时自动下载 yt-dlp/ffmpeg 到 config/broadcast_media_support/tools")
                .define("download", true);
        b.pop();
        b.push("paths");
        YTDLP = b.comment("yt-dlp 路径;auto 表示使用自动下载的工具").define("ytdlp", "auto");
        FFMPEG = b.comment("ffmpeg 路径;auto 表示使用自动下载的工具").define("ffmpeg", "auto");
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
