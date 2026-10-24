# 广播站 Broadcast（Minecraft 1.20.1 + Forge 模组）

校园 / 火车站风格广播系统模组：**广播站**、**三种广播器**（挂墙式 / 吸顶式 / 落地式）与**话筒**。
当前版本：**0.0.1br**

构建产物：`build/libs/broadcast-0.0.1br-all.jar`（内置 MP3 解码器，已 reobf，直接放进 `mods/`）。

---

## 功能一览

| 方块 / 功能 | 说明 |
| --- | --- |
| 广播站 | 广播核心。多频率、音乐搜索、在线音乐、播放列表（二级 GUI） |
| 挂墙式 / 吸顶式 / 落地式广播器 | 三种独立模型；右键用「广播调试器」打开频率 GUI |
| 话筒 | 独立 GUI：频率、红石控制模式、讲话暂停音乐；检测半径默认 10 格 |
| 频率系统 | 1~12 位英文/数字（不区分大小写），同频率自动连接 |
| 音乐 UID | 音乐库导入时分配唯一 8 位 UID（小写字母开头，仅小写字母/数字），用于显示与删除 |
| 音乐搜索 | 音乐库页签按文件名/UID 实时搜索 |
| 删除音乐 | 主 GUI 两段确认删除；也可用指令 `/? delete musichub sure <UID>` |
| 播放列表（二级 GUI） | 循环、顺序/逆序、每首间隔、单首时长；滚轮调数值（类似机械动力） |
| 原生 MP3 | 内置 JLayer；已跳过 ID3v2、校验 Header、修复 `Header.frequency()` 调用 |
| 定时循环 | 旧版「固定定时循环」已移除，改由播放列表 GUI 管理 |
| 声音半径 | 默认 18 格（4~64），距离线性衰减、超出静音 |
| 调试器 | 右键广播站/广播器/话筒打开 GUI；无配方、不在创造栏，仅命令获取 |
| 指令 | `/?` 开头的调试指令，见下方 |
| 配方书 | 玩家登录时自动解锁本模组的 5 个配方 |

前置模组：**无**（JLayer 以 Jar-in-Jar 库形式内置，不计入模组依赖，也不超过 3 个）。

---

## 构建

需要 JDK 17 + Gradle 8.1.1。仓库 `tools/` 已附带 JDK17、Gradle 8.1.1 与一键脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

或手动：

```powershell
$env:JAVA_HOME = "$PWD\tools\jdk17\jdk-17.0.20.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\tools\gradle-8.1.1\bin\gradle.bat build --no-daemon
```

产物：

- `build/libs/broadcast-0.0.1br-all.jar` —— **发布用**，含内置 MP3 解码器
- `build/libs/broadcast-0.0.1br.jar` —— 不含内置依赖，一般不用

---

## 安装

1. 安装 Forge 1.20.1（47.x）。
2. 把 `broadcast-0.0.1br-all.jar` 放进客户端与服务端的 `mods/`（两边都要，版本一致）。
3. 启动游戏。许可证见 jar 内 `LICENSE`（MIT）。

---

## 游戏内使用

### 合成表

| 物品 | 配方（3×3，从左到右） |
| --- | --- |
| 广播站 | `铁锭 铁锭 铁锭` / `红石 红石 红石` / `铜锭 空 空` |
| 吸顶式广播器 | `铁锭 铁锭 铁锭` / `空 铜锭 空` / `空 空 空` |
| 挂墙式广播器 | `铁锭 空 空` / `铁锭 铁锭 铁锭` / `铁锭 空 空` |
| 落地式广播器 | `铁锭 铁锭 铁锭` / `铁锭 铁锭 铁锭` / `空 铜锭 空` |
| 话筒 | `空 铁粒 空` / `铁粒 铁锭 铁粒` / `空 红石 空` |

> 所有配方会在玩家进入服务器时自动解锁到配方书中。

### 调试器（打开 GUI 的唯一方式）

- **普通右键方块不会打开 GUI**，会提示需要调试器。
- 使用「广播调试器」右键广播站 / 广播器 / 话筒即可打开对应界面。
- 调试器没有合成配方，也不在创造模式物品栏中，只能通过：
  ```
  /? give TSQ <玩家名>
  ```
  获取（需要 OP）。

### 频率

1. 用调试器打开广播站 →「频率」页签；默认 `CH1`。
2. 添加最多 8 个频率；用调试器打开广播器/话筒设置相同频率。
3. 广播站播放会送到所有持有这些频率的广播器；话筒向同频率且 **10 格内**的广播器讲话。

### 音乐库与 UID

- 服务端 `broadcast_music/` 中每个音频文件在导入/扫描时分配一个 UID：
  **8 位，小写字母开头，只含小写字母和数字**（最长限制 10 位）。
- 音乐库列表显示 `UID + 文件名`，搜索框同时匹配两者。
- **删除**：
  - 主 GUI：选中歌曲 → 点「删除」→ 按钮变红并显示「确认删除」→ 再点一次才真正删除；
  - 指令（需要 OP）：`/? delete musichub sure <UID>`。
  - 删除会同时删除服务端文件并更新 UID 索引。

### 播放列表（二级 GUI）

主 GUI 音乐库页点「播放列表」打开：

- 左侧音乐库，右侧播放列表；`>` 添加当前歌曲，`<` 移除选中项。
- **循环**：开关循环播放整个列表。
- **顺序/逆序**：列表播放方向。
- **间隔**：每首之间等待秒数（默认 60）。
- **每首时长**：单曲播放秒数（默认 30）。
- 「间隔」「每首时长」数值框支持：**鼠标滚轮调整**（类似机械动力），左键 +、右键 -。
- 红石模式开启时，播放列表受广播站通电控制；断电停止并重置计时。
- 旧版「固定定时循环」已报废，所有循环逻辑统一放到此 GUI。

### 广播站主 GUI

四个页签：

1. **音乐库**：搜索、UID、删除（两段确认）、播放列表入口、播放/停止/刷新。
2. **网络音乐**：URL 直链（无扩展名时按文件头自动识别 ogg/wav/mp3）。
3. **频率**：多频率添加/删除。
4. **设备管理**：广播器/话筒列表、上传地址与上传入口。

### 上传音乐

服务端 HTTP 端口（默认 8790），保存到服务端 `游戏目录/broadcast_music/`：

1. 主 GUI「设备管理」→ 点击上传卡片；
2. 浏览器 `http://<服务端IP>:8790/`；
3. 命令行 `curl -T 歌.ogg "http://<服务端IP>:8790/upload?name=歌.ogg"`。

上传成功后自动分配 UID。仅接受 `.ogg/.wav/.mp3`。

### 声音与距离

- 广播器声音按玩家距离线性衰减，超过 `audio.speakerRadius`（默认 18）完全静音。
- 话筒检测/连接广播器半径 `mic.detectionRadius`（默认 10，中心点精确距离）。

---

## 指令（`/?` 开头）

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/? list command` | 列出指令 | 所有人 |
| `/? list music` | 列出音乐库 UID + 文件名 | 所有人 |
| `/? delete musichub sure <UID>` | 删除音乐（同时删文件） | OP |
| `/? restart` | 重启上传服务并重扫音乐库索引 | OP |
| `/? stopweb` | 停止上传服务 | OP |
| `/? startweb` | 启动上传服务 | OP |
| `/? give TSQ <玩家>` | 给玩家一个广播调试器 | OP |

GUI 已有的功能不在指令里重复实现。

---

## 配置

`config/broadcast-server.toml`（COMMON，改完重启生效）：

```toml
[upload]
enabled = true
port = 8790
bind = "0.0.0.0"
dir = "broadcast_music"
maxFileMB = 64

[mic]
capture = "auto"        # auto / server / client
detectionRadius = 10    # 话筒检测广播器半径

[audio]
speakerRadius = 18      # 广播器可听半径
```

---

## 多人联机 / 常见问题

### 「无效会话」（Invalid session）

这是 **Mojang 正版会话验证失败**，不是本模组网络包错误：

1. 离线联机/朋友服：服务端 `server.properties` 设 `online-mode=false` 后重启；
2. 正版服：使用正版账号启动器，并确保能访问 `sessionserver.mojang.com`；
3. 客户端与服务端都要装**同一个** `broadcast-0.0.1br-all.jar`，删除旧版本；
4. 局域网开放由主机账号验证，离线账号同样会失败。

网络协议版本已设置为 `0.0.1br`，版本不一致会被 Forge 拒绝。

### MP3 播放失败

0.0.1br 已修复：
- 先跳过 `ID3v2` 标签再交给 JLayer；
- 修正 `Header.getFrequency()`（JLayer 实际方法为 `frequency()`）；
- 构造时探测首帧采样率/声道；
- Header 无效帧跳过，连续失败结束该段由播放器重建实例。

### 音乐/话筒无声

- 检查频率是否一致；
- 话筒只向 10 格内的同频率广播器讲话；
- 检查 `speakerRadius`；
- MP3 无需额外安装 JLayer。

---

## 项目结构

```
src/main/java/com/broadcast/
  BroadcastMod.java              模组主类
  registry/                      方块/物品/方块实体/菜单/创造页签
  item/DebuggerItem.java         调试器
  block/                         广播站、三种广播器、话筒
  blockentity/                   频率、播放列表、话筒模式、红石
  menu/                          广播站 / 广播器 / 话筒菜单
  command/BroadcastCommands.java /? 指令
  world/BroadcastRegistryData.java 按频率检索设备
  server/MusicLibrary.java       UID 索引(8 位)
  server/UploadHttpServer.java   上传服务
  network/                       网络通道 + 数据包
  client/gui/                    主 GUI / 二级播放列表 GUI / 设备 GUI
  client/audio/                  OpenAL、ogg/wav/mp3 解码、距离衰减
src/main/resources/
  LICENSE                        MIT
  assets/broadcast/              模型/贴图/语言
  data/broadcast/                配方/战利品表
```

---

## 已知限制

- 播放列表的「每首时长」是服务端计时，不读取音频真实长度。
- 网络音乐必须是音频直链。
- 上传端口默认无鉴权，公网部署请自行加防火墙/反向代理。
- 内置 JLayer 为 LGPL-2.1，作为独立 jar 位于 `META-INF/jarjar/`。

## 许可

模组代码 MIT（见 [LICENSE](LICENSE)）；内置 JLayer 遵循 LGPL-2.1。
