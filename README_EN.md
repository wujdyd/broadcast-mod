# Broadcast Mod (Minecraft 1.20.1 + Forge)

School / railway-station style broadcast system. Version: **0.0.3br**

Release jar: `build/libs/broadcast-0.0.3br-all.jar` (bundled MP3/FLAC decoder, reobfuscated).

## 0.0.3br Highlights

- Debugger durability is synced like a vanilla tool; no extra item tooltip lines.
- ESC pause suspends OpenAL sources; adding a same-frequency speaker no longer restarts playback.
- Real playlist progression via client track-end; secondary GUI pins "Now playing" and shows 5 "Up next" per page with unlimited pages.
- FLAC support (jFLAC, 8/16-bit).
- Video links (Bilibili, Douyin, Kuaishou, YouTube, TikTok, etc.) can be resolved to audio by server-side `yt-dlp` + `ffmpeg`.
- Audio subtitle HUD; connected indicator light (brightness 5, particles only while linking).
- Microphone fallback capture + chime before capture; 300-block portable mic range; failure messages near speakers.
- Frequency permission bands and 100-block OP3/OP2 interference rules (`/? band`).

## Blocks & Items

| Name | Description |
| --- | --- |
| Broadcast Station | Core block: multiple frequencies, music library, online music, playlist |
| Wall / Ceiling / Floor / Column Speaker | Four distinct speaker models, linked by frequency |
| Microphone | Redstone-controlled; GUI for frequency, redstone mode, pause music, start chime |
| Portable Microphone | Handheld: right-click to start/stop talking, Shift+right-click for GUI; no durability |
| Broadcast Debugger | The only tool that opens GUIs; 450 durability; craftable |
| Broadcast Indicator | Place anywhere; lights while a mic/station broadcasts in the dimension |

## Crafting

| Item | Recipe (3×3) |
| --- | --- |
| Station | `iron iron iron` / `redstone redstone redstone` / `copper - -` |
| Ceiling Speaker | `iron iron iron` / `- copper -` / `- - -` |
| Wall Speaker | `iron - -` / `iron iron iron` / `iron - -` |
| Floor Speaker | `iron iron iron` / `iron iron iron` / `- copper -` |
| Column Speaker | `- iron -` / `iron copper iron` / `- iron -` |
| Microphone | `- nugget -` / `nugget iron nugget` / `- redstone -` |
| Debugger | `- iron -` / `iron copper iron` / `- redstone -` |
| Portable Microphone | `- iron -` / `iron redstone iron` / `- copper -` |
| Indicator | `- glass -` / `glass redstone glass` / `- glass -` |

Recipes are unlocked in the recipe book on login.

## Debugger

Right-click a station/speaker/microphone to open its GUI. 450 durability; per saved configuration:

- Microphone: **3**
- Speaker: **4**
- Station (frequencies / playlist / loop): **5**

Opening GUIs, play/stop, volume and redstone toggle are free. The old `/? give TSQ` command has been removed.

## Frequency

Station can hold up to 8 frequencies (`CH1` by default). Speakers/microphones use one frequency each. The microphone reaches same-frequency speakers within **10 blocks**. Microphone GUI also has redstone mode, pause-music and start-chime toggles.

## Portable Microphone

Right-click toggles talking; Shift+right-click opens its own GUI (frequency + pause music). Captures the player's client microphone; no durability. Stops automatically on logout/dimension change/item drop.

## Indicator Light

Place anywhere (no support needed). Lights up (level 15) while any microphone/station in the dimension is broadcasting.

## Station GUI

Music Library (search / UID / two-step delete / playlist entry), Online Music, Frequency, Devices.
Secondary playlist GUI: loop, forward/reverse, gap, per-track duration; scroll values like Create.

## Commands (`/?`)

- `/? list command`
- `/? list music`
- `/? list recipe`
- `/? band <max|cr|su> <start:end> <yes|no>` (OP3)
- `/? delete musichub sure <UID>` (OP)
- `/? restart`, `/? stopweb`, `/? startweb` (OP)

## MP3

Native JLayer decoding, bundled. ID3v2 tags are skipped before decoding; Header is validated; the old `getFrequency()` bug is fixed.

## Video Audio (companion mod)

Video links are handled by the separate **Broadcast Media Support** addon; the main jar does not bundle yt-dlp/ffmpeg.

Install both:

- `broadcast-0.0.3br-all.jar`
- `broadcast_media_support-0.0.3br.jar`

Without the addon the main mod reports exactly: `未安装broadcast media support，无法播放`.

The addon downloads yt-dlp (~17 MB) and ffmpeg (~80 MB) on first use into `config/broadcast_media_support/tools/` (Windows/Linux/macOS). For offline servers, set absolute paths in `config/broadcast_media_support-common.toml`.

## Multiplayer "Invalid session"

That is Mojang session authentication, not this mod. Use `online-mode=false` for private offline servers, or a genuine account able to reach `sessionserver.mojang.com`. Both sides must use the same jar. Network protocol: `0.0.3br2`.

## License

MIT (see [LICENSE](LICENSE)); bundled JLayer is LGPL-2.1.
