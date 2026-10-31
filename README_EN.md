# Broadcast Mod (Minecraft 1.20.1 + Forge)

School / railway-station style broadcast system. Version: **0.0.1br**

Release jar: `build/libs/broadcast-0.0.1br-all.jar` (bundled MP3 decoder, reobfuscated).

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
- `/? delete musichub sure <UID>` (OP)
- `/? restart`, `/? stopweb`, `/? startweb` (OP)

## MP3

Native JLayer decoding, bundled. ID3v2 tags are skipped before decoding; Header is validated; the old `getFrequency()` bug is fixed.

## Multiplayer "Invalid session"

That is Mojang session authentication, not this mod. Use `online-mode=false` for private offline servers, or a genuine account able to reach `sessionserver.mojang.com`. Both sides must use the same jar. Network protocol: `0.0.1br2`.

## License

MIT (see [LICENSE](LICENSE)); bundled JLayer is LGPL-2.1.
