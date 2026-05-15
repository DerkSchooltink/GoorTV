# GoorTV

A free, open source video streaming player for Android and Android TV. Supports M3U playlists and Xtream Codes compatible sources.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **M3U & Xtream Codes** — add any M3U playlist URL or Xtream Codes compatible source
- **Custom channels** — manually add, edit, or delete individual channels
- **Group filtering** — pick which channel groups to show per source
- **Favorites & watch history** — mark channels and resume where you left off
- **Google Cast** — cast to any Chromecast or Android TV device (Nvidia Shield, etc.)
- **Android TV & D-pad** — fully navigable with a remote control
- **Per-source stream limits** — cap concurrent streams per source
- **Custom HTTP headers** — pass auth headers for protected streams
- **Aspect ratio control** — Fit, Fill, Zoom, 16:9, 4:3

---

## Install

### Obtainium (recommended)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Add source: `https://github.com/DerkSchooltink/GoorTV`
3. Obtainium will notify you of new releases automatically

### Manual APK

Download the latest `app-release.apk` from the [Releases](https://github.com/DerkSchooltink/GoorTV/releases) page and sideload it.

---

## Getting started

### Add a source

1. Open the app and go to **Settings**
2. Tap **Add source** and choose a type:
   - **M3U** — paste a playlist URL
   - **Xtream Codes** — enter your provider URL, username, and password
3. The app syncs channels automatically on launch

> GoorTV is a player only — it does not provide, host, or endorse any streaming content. You are responsible for ensuring you have the rights to access any streams you add.

### Browse and play

- Channels are grouped by category
- Use the search bar to filter by name
- Press a channel to start playing
- Tap the screen while playing to show controls

### Cast to a TV

- While playing, tap the screen to reveal controls
- Tap the **Cast** button and select your device
- Requires a Chromecast or Android TV device on the same Wi-Fi network

---

## Development

### Requirements

- Android Studio Hedgehog or newer
- JDK 17
- [Task](https://taskfile.dev) (`brew install go-task`)

### Build & run

```bash
git clone https://github.com/DerkSchooltink/GoorTV.git
cd GoorTV

task build        # assembleDebug
task install      # build + install on connected device
task run          # install + launch MainActivity
```

### Other commands

```bash
task test:unit    # run unit tests
task test:ui      # run instrumented tests (needs device/emulator)
task lint
task logcat
task clean
```

### Architecture

Single-module Kotlin + Jetpack Compose app targeting Android TV (minSdk 26).

- **Data** — Room database, DataStore preferences, Ktor HTTP client
- **DI** — Koin
- **Player** — ExoPlayer (Media3)
- **Casting** — Google Cast SDK with the default media receiver
- **Navigation** — Compose Navigation with three screens: Home, Player, Settings

---

## Releasing

Releases are built automatically by GitHub Actions when a version tag is pushed.

### One-time setup (keystore + GitHub secrets)

```bash
# Generate a release keystore (keep this file safe, never commit it)
keytool -genkey -v -keystore release.jks -alias goortv \
        -keyalg RSA -keysize 2048 -validity 10000

# Base64-encode it for the GitHub secret
base64 -i release.jks | pbcopy   # macOS — paste into KEYSTORE_BASE64
```

Add four secrets to your GitHub repo (`Settings → Secrets → Actions`):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | base64-encoded `release.jks` |
| `STORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `goortv` (or whatever you chose) |
| `KEY_PASSWORD` | key password |

### Publish a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions builds the signed APK and attaches it to the release automatically. Obtainium users get notified.

---

> **Note on cleartext traffic**: the app allows plain HTTP streams (`usesCleartextTraffic="true"`) because many streaming sources do not use HTTPS. No user credentials are transmitted over HTTP — provider passwords are used only for Xtream Codes API calls to the user's own source.

---

## Contributing

Bug reports and pull requests are welcome. For major changes please open an issue first to discuss what you'd like to change.

---

## License

[MIT](LICENSE) © Derk Schooltink
