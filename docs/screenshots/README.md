# Play Store screenshot assets

Four device sets (phone, 7" tablet, 10" tablet, Android TV), six surfaces
each, captured against the `screenshots.m3u` fixture + `screenshots-epg.xml`
EPG (public-domain HLS streams, generic programme titles, no broadcaster
references) on the amber redesign, release build.

| Set | Resolution | Play Console slot |
|---|---|---|
| `phone/` | 1080×2424 | Phone screenshots |
| `tablet-7/` | 1080×1920 | 7-inch tablet |
| `tablet-10/` | 1440×2560 | 10-inch tablet |
| `tv/` | 1920×1080 | Android TV (TV banner is separate, see store-assets) |

## Surfaces (identical across sets)

| File | Surface | What it shows |
|---|---|---|
| `01-home.png` | Home | Category-grouped channels with EPG now-playing + progress per row |
| `02-player.png` | Player | Big Buck Bunny fullscreen via Media3 ExoPlayer (Demo News channel) |
| `03-settings.png` | Settings | Configured source with sync/groups/edit/delete, Privacy policy link |
| `04-search.png` | Search | Search bar focused over the grouped channel list |
| `05-hide-menu.png` | Home (long-press) | "Hide channel" menu — UGC moderation per A3.5 |
| `06-guide.png` | Guide | Populated XMLTV grid with now-line |

## Re-capture procedure (scripted)

XMLTV timestamps are absolute, so first regenerate the EPG fixture and let
GitHub Pages publish it (the M3U's `url-tvg` points at the Pages URL; the
app auto-discovers it on first sync):

```bash
python3 docs/screenshots/generate-epg.py docs/screenshots-epg.xml
# commit + merge to main, wait for Pages deploy (~1 min), verify:
curl -s https://derkschooltink.github.io/GoorTV/screenshots-epg.xml | head -2
```

Per device profile (AVDs: `shot_phone` 1080×2424@420, `shot_tab7`
1080×1920@280, `shot_tab10` 1440×2560@320 on `google_apis`;
`shot_tv` 1920×1080@320 on `android-tv` — all android-36/arm64):

```bash
emulator -avd shot_phone -no-snapshot -no-boot-anim -no-audio -wipe-data &
adb wait-for-device
adb install -r app/build/outputs/apk/release/app-release.apk   # release build!
# deterministic captures:
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global $s 0; done
# clean status bar (phone/tablet only):
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# capture all six surfaces:
cd docs/screenshots/phone
maestro test -e M3U_URL=https://derkschooltink.github.io/GoorTV/screenshots.m3u ../capture-flow.yaml
```

Gotchas baked into `capture-flow.yaml` (don't relearn them):

- `M3U_URL` must come via `-e` **before** the flow path; an in-flow `env:`
  default silently overrides the CLI value.
- Type only `https://` URLs — the emulator IME upgrades a typed `http://`
  to `https://`, which broke `10.0.2.2` local-server fixtures.
- Text input races the IME on cold emulators; the add-source dialog section
  asserts each field and retries.
- Wait/tap targets use the first list row ("Open Documentary") or scroll
  first — TV viewports fit only ~4 rows.

**TV capture note:** AOSP TV images ship GMS basic without the Cast dynamite
module; `CastAvailability` gates all Cast SDK entry points, otherwise every
channel tap crashes (see CLAUDE.md, Casting).
