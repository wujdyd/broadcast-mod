# 项目结构与上手说明

## 顶层

```text
New folder/
├─ build.gradle                 主模组构建脚本(版本、依赖、jarJar)
├─ src/main/java/com/broadcast/
├─ src/main/resources/          语言文件、模型、配方、mods.toml
├─ media-support/               独立附属模组(视频音频)
├─ tools/                       JDK17 与 Gradle 8.1.1
└─ build/libs/                  主模组产物
```

## 主模组 Java 包

```text
com.broadcast
├─ BroadcastMod.java            模组入口
├─ block/                       广播站、广播器、话筒方块
├─ blockentity/                 方块实体(真正的逻辑)
├─ client/
│  ├─ audio/                    OpenAL 播放、麦克风采集
│  ├─ gui/                      所有界面
│  ├─ ClientEvents.java         客户端 tick / 字幕
│  └─ ClientDataHolder.java     客户端缓存(服务端同步来的数据)
├─ command/BroadcastCommands    /? 指令注册
├─ config/                      配置文件
├─ event/                       服务端/通用事件
├─ item/                        调试器、便携话筒
├─ network/                     SimpleChannel 网络
│  └─ packet/                   每个包一个 record 文件
├─ server/                      音乐库、上传 HTTP、预设
└─ util/                        频率、权限、名称工具
```

## 附属模组

```text
media-support/src/main/java/com/broadcast/media/
├─ MediaSupport.java            主模组通过隐藏指令调用的入口
├─ MediaSupportCommand.java     解析/下载指令
├─ ToolManager.java             yt-dlp / ffmpeg 下载与调用
├─ MediaSupportConfig.java      配置
├─ api/BroadcastMediaApi.java   给其他模组用的公开 API
└─ client/                      下载页、F9 快捷键
```

## 最重要语法

### 指令注册

`BroadcastCommands.register(...)`:

```java
dispatcher.register(Commands.literal("?")
    .then(Commands.literal("find")
        .then(Commands.argument("frequency", StringArgumentType.greedyString())
            .executes(BroadcastCommands::findFrequency))));
```

- `literal("x")` 固定子指令；
- `argument("y", 类型)` 参数；
- `executes(方法)` 真正执行；
- 方法必须返回 `int`。

### 网络包

一个包一个 `record`，至少三件套：

```java
public record XxxPacket(int value) {
    public static void encode(XxxPacket p, FriendlyByteBuf buf) { buf.writeInt(p.value); }
    public static XxxPacket decode(FriendlyByteBuf buf) { return new XxxPacket(buf.readInt()); }
    public static void handle(XxxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { /* 主线程逻辑 */ });
        ctx.get().setPacketHandled(true);
    }
}
```

在 `NetworkHandler` 里 `CHANNEL.messageBuilder(...).add()` 注册，两边版本必须一致。

### 方块实体

逻辑写得最多的是：

```text
blockentity/BroadcastStationBlockEntity.java
```

- `serverTick()`：每 tick 播放状态；
- `saveAdditional/load`：存档；
- `broadcastState()/broadcastInfo()`：同步给客户端；
- `startPlayback()`、`stopPlayback()`、`startPreset()`：播放控制。

### GUI

- 继承 `Screen` 或 `AbstractContainerScreen`；
- `init()` 创建按钮/输入框；
- `renderBg` 或 `renderBackground` 画背景；
- `mouseClicked` 处理点击；
- 输入框用 `EditBox`，空内容时自己画浅色提示文字，避免 `setHint` 颜色过暗。

### 频率

`util/FrequencyUtil.java`：

- `normalize()`：格式化并校验；
- 支持 `1`、`1.5`、`1.125`、`CH1`；
- 小数最多 3 位。

## 构建

```powershell
.\tools\gradle-8.1.1\bin\gradle.bat build --offline --no-daemon
```

附属：

```powershell
cd media-support
..\tools\gradle-8.1.1\bin\gradle.bat build --offline --no-daemon
```

产物：

```text
build/libs/broadcast-0.0.1c-all.jar
media-support/build/libs/broadcast_media_support-1.0.1-fix2.jar
```
