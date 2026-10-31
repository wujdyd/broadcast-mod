package com.broadcast.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.Arrays;

public class BroadcastConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue UPLOAD_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> UPLOAD_DIR;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS;
    public static final ForgeConfigSpec.IntValue MAX_UPLOAD_MB;
    public static final ForgeConfigSpec.BooleanValue UPLOAD_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> MIC_CAPTURE;
    public static final ForgeConfigSpec.IntValue MIC_DETECTION_RADIUS;
    public static final ForgeConfigSpec.IntValue SPEAKER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue VIDEO_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> VIDEO_YTDLP;
    public static final ForgeConfigSpec.ConfigValue<String> VIDEO_FFMPEG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("广播站模组 - 服务端配置").push("upload");
        UPLOAD_ENABLED = b.comment("是否启用 HTTP 上传服务(开放端口接收客户端上传的音乐文件)")
                .define("enabled", true);
        UPLOAD_PORT = b.comment("上传服务监听端口(0 表示关闭)")
                .defineInRange("port", 8790, 0, 65535);
        BIND_ADDRESS = b.comment("上传服务绑定地址(0.0.0.0 表示所有网卡;127.0.0.1 仅本机)")
                .define("bind", "0.0.0.0");
        UPLOAD_DIR = b.comment("上传目录(相对游戏根目录)")
                .define("dir", "broadcast_music");
        MAX_UPLOAD_MB = b.comment("单个文件最大体积 (MB)")
                .defineInRange("maxFileMB", 64, 1, 1024);
        b.pop();

        b.push("mic");
        MIC_CAPTURE = b.comment(
                        "话筒采集来源:",
                        "  server = 只采集运行服务端那台电脑的麦克风(单机即本机)",
                        "  client = 由放置话筒的玩家客户端采集自己的麦克风(适合专用服务器)",
                        "  auto   = 单机/局域网主机用 server,专用服务器用 client")
                .defineInList("capture", "auto", Arrays.asList("auto", "server", "client"));
        MIC_DETECTION_RADIUS = b.comment("话筒检测/连接广播器的半径(格),默认 10;超出的话筒不会向该广播器讲话")
                .defineInRange("detectionRadius", 10, 1, 64);
        b.pop();

        b.push("audio");
        SPEAKER_RADIUS = b.comment("广播器声音可听半径(格):超出后完全听不到;建议 15~20,可设 4~64")
                .defineInRange("speakerRadius", 18, 4, 64);
        b.pop();

        b.comment("网络视频音频解析(需要服务器安装 yt-dlp 与 ffmpeg)").push("video");
        VIDEO_ENABLED = b.comment("是否允许解析 B站/抖音/快手/YouTube/TikTok 等视频链接")
                .define("enabled", true);
        VIDEO_YTDLP = b.comment("yt-dlp 可执行文件路径(默认从 PATH 查找)")
                .define("ytdlp", "yt-dlp");
        VIDEO_FFMPEG = b.comment("ffmpeg 可执行文件路径(默认从 PATH 查找)")
                .define("ffmpeg", "ffmpeg");
        b.pop();

        SPEC = b.build();
    }

    public static void register() {
        // COMMON:单机存档外也能读取,配置位于 config/broadcast-server.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "broadcast-server.toml");
    }

    public static int uploadPort() {
        return UPLOAD_ENABLED.get() ? UPLOAD_PORT.get() : 0;
    }

    public static String uploadDir() {
        return UPLOAD_DIR.get();
    }

    public static String bindAddress() {
        return BIND_ADDRESS.get();
    }

    public static long maxUploadBytes() {
        return (long) MAX_UPLOAD_MB.get() * 1024L * 1024L;
    }

    public static String micCapture() {
        return MIC_CAPTURE.get();
    }

    public static int speakerRadius() {
        return SPEAKER_RADIUS.get();
    }

    public static int micDetectionRadius() {
        return MIC_DETECTION_RADIUS.get();
    }

    public static boolean videoEnabled() {
        return VIDEO_ENABLED.get();
    }

    public static String ytDlp() {
        return VIDEO_YTDLP.get();
    }

    public static String ffmpeg() {
        return VIDEO_FFMPEG.get();
    }

    /** 按配置决定当前服务器是否应使用客户端采集。 */
    public static boolean useClientMicCapture(boolean dedicatedServer) {
        String mode = micCapture();
        if ("client".equalsIgnoreCase(mode)) return true;
        if ("server".equalsIgnoreCase(mode)) return false;
        return dedicatedServer;
    }
}
