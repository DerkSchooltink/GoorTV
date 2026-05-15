# GoorTV

A free, open source IPTV player for Android and Android TV.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **M3U & Xtream Codes** — add any M3U playlist URL or Xtream Codes provider
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

## Contributing

Bug reports and pull requests are welcome. For major changes please open an issue first to discuss what you'd like to change.

---

## License

[MIT](LICENSE) © Derk Schooltink
