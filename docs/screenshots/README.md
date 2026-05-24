# Play Store screenshot assets

Captured against the `screenshots.m3u` fixture (public-domain HLS streams,
no broadcaster references) on a Pixel 9a emulator (1080×2424, Android 17,
release APK v1.4.2). Re-capture by:

1. Install the release APK.
2. Accept the consent dialog.
3. Settings → + → M3U → URL: `https://derkschooltink.github.io/GoorTV/screenshots.m3u`.
4. Open the source's Groups dialog, tap **All**, **Save** (new sources default
   to "no groups selected" — this is intentional).
5. Drive the flows captured below.

## Phone (1080×2424)

| File | Surface | What it shows |
|---|---|---|
| `phone/01-home.png` | Home | 9 channels grouped by category, plus the top-bar (favorites / sort / search / guide / settings) |
| `phone/02-player.png` | Player | Big Buck Bunny rendering full-screen via Media3 ExoPlayer |
| `phone/03-settings.png` | Settings | Demo source listed with sync/groups/edit/delete actions, plus the in-app Privacy policy link |
| `phone/04-search.png` | Search | Search bar focused, Recently Watched rail, grouped channel list |
| `phone/05-hide-menu.png` | Home (long-press) | DropdownMenu with "Hide channel" — demonstrates UGC moderation per A3.5 |

TV screenshots (1920×1080) pending Android TV emulator capture.
