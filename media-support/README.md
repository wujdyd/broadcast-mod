# Broadcast Media Support

`broadcast_media_support` 是广播站模组的独立附属，用于把 B站、抖音、快手、YouTube、TikTok、微博、优酷等视频链接解析成音频。

## 安装

把两个 jar 都放进 `mods/`：

- `broadcast-0.0.3br-all.jar`（主模组）
- `broadcast_media_support-0.0.3br.jar`（本附属）

没有安装本附属时，主模组在播放视频链接时会提示：

```text
未安装broadcast media support，无法播放
```

## 自动下载工具

首次播放视频链接时，本附属会下载：

- `yt-dlp`：约 17 MB
- `ffmpeg`：约 80 MB（Windows 使用 gyan.dev essentials 构建）

下载到：

```text
config/broadcast_media_support/tools/
```

支持 Windows x64、Linux x64/arm64、macOS。下载完成后复用，不会每次重复下载。

配置：`config/broadcast_media_support-common.toml`

```toml
[auto]
download = true

[paths]
ytdlp = "auto"
ffmpeg = "auto"
```

如果服务器不能联网，可以把工具放到任意位置并在 `paths` 中填写绝对路径，同时把 `download` 设为 `false`。

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

产物：`media-support/build/libs/broadcast_media_support-0.0.3br.jar`

## 许可

MIT。下载的 yt-dlp/ffmpeg 不包含在本模组中，遵循其各自许可证。
