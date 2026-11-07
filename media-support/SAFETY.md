# Download Consent

Broadcast Media Support does **not** download anything automatically.

No file is downloaded while a video is played, while a GUI is open, or while
the server starts.

Downloads only begin after an explicit user action:

1. The client opens the download request page with `F9` or `/broadcastmedia`,
   reads the listed files/sizes, and presses **Request download**.
2. A server administrator explicitly runs `/broadcastmediasupport download`.

Only then are `yt-dlp` and `ffmpeg` downloaded from their official release
locations into `config/broadcast_media_support/tools/`.

The mod does not download project updates. The only optional network accesses
are the user-requested tool downloads and the user-requested video URL that
`yt-dlp` resolves.
