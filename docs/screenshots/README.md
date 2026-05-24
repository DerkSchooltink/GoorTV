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

## TV (3840×2160)

Captured against the same fixture on an AOSP TV on x86 emulator (Android 16,
debug build from `fix/cast-non-gms-crash`). Play Store accepts either
1920×1080 or 3840×2160 for TV screenshots; the 4K natives are kept as-is.

| File | Surface | What it shows |
|---|---|---|
| `tv/01-home.png` | Home | Top bar, Recently Watched, category-grouped channel list |
| `tv/02-player.png` | Player | Big Buck Bunny fullscreen via Media3 ExoPlayer (back arrow is intentionally always visible) |
| `tv/03-settings.png` | Settings | Sources list with M3U source + Privacy policy entry |
| `tv/04-search.png` | Search | Search bar focused, channel list below |
| `tv/05-hide-menu.png` | Home (MENU key) | DropdownMenu with "Hide channel" — UGC moderation per A3.5 |

**Capture note:** AOSP TV on x86 ships GMS basic (Play Store is present) but
lacks the Cast dynamite module. Pre-`fix/cast-non-gms-crash` builds would
crash the process on every channel tap. The fix probes
`CastContext.getSharedInstance` once and skips Cast UI when unavailable.
