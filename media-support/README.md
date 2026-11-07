# Broadcast Media Support

`broadcast_media_support` 是广播站模组的独立附属，用于把 B站、抖音、快手、YouTube、TikTok、微博、优酷等视频链接解析成音频。

## 安装

把两个 jar 都放进 `mods/`：

- `broadcast-0.0.4br-all.jar`（主模组）
- `broadcast_media_support-1.0.1.jar`（推荐）
- `broadcast_media_support-1.0.0-fix2.jar`（客户端下载页版本）

没有安装本附属时，主模组在播放视频链接时会提示：

```text
未安装broadcast media support，无法播放
```

## 下载工具（必须由用户主动请求）

为符合 Modrinth 的安全要求，**不会在播放视频时静默下载任何文件**。

首次使用前，客户端按 `F9` 或输入 `/broadcastmedia` 打开下载请求页，点击“请求服务端下载工具”；也可以由管理员在服务器执行：

```text
/broadcastmediasupport download
```

确认后才会下载：

- `yt-dlp`：约 17 MB
- `ffmpeg`：约 80 MB（Windows 使用 gyan.dev essentials 构建）

下载到：

```text
config/broadcast_media_support/tools/
```

支持 Windows x64、Linux x64/arm64、macOS。下载完成后复用。

配置：`config/broadcast_media_support-common.toml`

```toml
[paths]
ytdlp = "auto"
ffmpeg = "auto"

[network]
insecureSsl = true
```

如果服务器不能联网，可把工具放到任意位置并在 `paths` 中填写绝对路径。

## 指令

主模组内部使用隐藏指令调用：

```text
/broadcastmediasupport resolve <pos> <dir> <url>
```

一般不需要手动输入。

## 构建

```powershell
cd media-support
..\tools\gradle-8.1.1\bin\gradle.bat build --no-daemon
```

产物：`media-support/build/libs/broadcast_media_support-1.0.1.jar`

## 许可

MIT。下载的 yt-dlp/ffmpeg 不包含在本模组中，遵循其各自许可证。


## 客户端下载请求页

快捷键 `F9` 或客户端指令 `/broadcastmedia` 打开。页面可请求服务端下载 yt-dlp/ffmpeg，也可以打开官方下载页。

## 其他模组 API

其他模组依赖本附属后可使用：

```java
import com.broadcast.media.api.BroadcastMediaApi;
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> future = BroadcastMediaApi.resolve(
        serverPlayer, videoUrl, outputDirectory);
```

返回生成的音频文件名，位于 `outputDirectory` 中。异步回调里请用 `server.execute(...)` 回到主线程。

## 版本

- `1.0.0-fix2`：客户端下载请求页（服务端逻辑不变）
- `1.0.1`：在 fix2 基础上增加公开 API、SSL 证书兼容与下载指令（推荐）
