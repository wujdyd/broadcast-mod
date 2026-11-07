package com.broadcast.media.api;

import com.broadcast.media.MediaSupport;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 给其他模组使用的公开 API。
 *
 * <pre>{@code
 * BroadcastMediaApi.resolve(player, "https://www.bilibili.com/video/BV...", outputDir)
 *     .thenAccept(fileName -> { ... });
 * }</pre>
 *
 * 返回的文件名位于 outputDir 中,可直接作为广播站本地音乐播放。
 * 调用线程是异步线程;回到主线程请使用 server.execute(...)。
 */
public final class BroadcastMediaApi {
    private BroadcastMediaApi() {
    }

    /** 异步解析视频链接,返回生成的音频文件名。 */
    public static CompletableFuture<String> resolve(ServerPlayer player, String url, Path outputDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return MediaSupport.resolveBlocking(player, url, outputDir.toString());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /** 同步解析;不要在服务器主线程调用。 */
    public static String resolveBlocking(ServerPlayer player, String url, Path outputDir) throws Exception {
        return MediaSupport.resolveBlocking(player, url, outputDir.toString());
    }
}
