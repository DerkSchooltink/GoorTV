---
title: GoorTV Privacy Policy
permalink: /privacy/
---

# GoorTV Privacy Policy

**Effective date:** 2026-06-11
**Contact:** goortv@proton.me

GoorTV is a media player for Android TV and Android phones. It plays
M3U playlists, XMLTV electronic programme guides, and Xtream-compatible
streams that **you** supply. The app ships with no content, no
playlists, no preset URLs, and no account system.

This document explains what data the app handles, where it goes, and
what choices you have.

---

## TL;DR

- The app **does not collect personal information about you**.
- The only data that leaves your device is (a) network requests to the
  URLs **you** typed into Settings, and (b) anonymous crash reports
  used to fix bugs.
- Uninstalling the app deletes all data it stored about your sources,
  favorites, and watch history.

---

## Data the app handles

### Stored on your device only — never uploaded

| Data | Where it lives | How to delete |
|---|---|---|
| Sources you added (M3U / XMLTV / Xtream URLs, optional Xtream username + password, optional HTTP headers) | Local Room database (`goortv.db`) | Settings → delete the source, or uninstall the app |
| Channel list synced from your sources | Local Room database | Auto-cleared when the source is deleted |
| Favorites, recently-watched timestamps, hidden channels | Local Room database | Settings → Hidden channels (to unhide) or uninstall |
| User preferences (sort order, included groups, ToS acceptance) | Local DataStore preferences | Uninstall |
| Search history (max 5 most-recent queries) | Local SharedPreferences | Uninstall |

Xtream credentials are encrypted at rest with a key held in the
Android Keystore (AES-GCM); the key never leaves your device. Android
sandboxing keeps the database inaccessible to other apps on a
non-rooted device, and `android:allowBackup="false"` keeps it out of
system backups. Because the encryption key cannot be copied off the
device, credentials cannot be recovered on a different device — you
re-enter them if you ever move to new hardware.

### Sent over the network as a direct consequence of using the app

| Data | Destination | Why |
|---|---|---|
| Your IP address + the playlist / EPG / stream URL you configured | Whichever server hosts the URL you typed | Required to fetch the content you asked for |
| Xtream username + password | The Xtream provider you configured | Sent in URL parameters as the Xtream API requires |
| Custom HTTP headers (if you set any) | Whatever target host requires them | Same — used only on requests you initiated |
| Channel logo URL | Whatever CDN your M3U references | Required to display channel artwork |
| Stream media URL | Google's default Cast Media Receiver (when you cast) | Required to start playback on the Cast device |

Two consequences of the Xtream Codes protocol are worth spelling out:
the protocol embeds your username and password directly in API and
stream URLs. That means (a) if your provider only serves plain HTTP,
your credentials travel over the network unencrypted, and (b) when you
cast an Xtream stream, the URL — credentials included — is sent to the
Cast device so it can fetch the stream. Prefer providers that support
HTTPS, and only cast to devices you trust.

These are **your** destinations — you chose them by adding the source.
The app does not contact any other servers on its own except for
crash reporting (next section).

### Anonymous crash reports — sent automatically when the app crashes

When the app crashes unexpectedly, an automatic crash report is sent
to **Sentry** (sentry.io) to help us fix the bug. Crash reports are
hosted on Sentry's **EU data centres** (Frankfurt, Germany) and are
deliberately scoped to **non-personal technical data only**:

- Stack trace of the crash (file names, line numbers, exception
  message — does **not** include URLs you typed or stream contents)
- Device model and Android OS version
- App version and build number

The following are **explicitly disabled**:

- IP address capture
- Screenshots
- View hierarchy / UI snapshots
- Performance traces
- Session replay
- Default Sentry "PII" collection

Crash reports cannot be linked back to you personally; no account
identifier is sent. They are retained according to Sentry's standard
retention policy (90 days for free-tier projects) and used only for
debugging.

If you do not want to send crash reports, the only way to opt out is
to uninstall the app — there is currently no in-app toggle. We may
add one in a future release.

---

## What the app does *not* do

- It does not contain advertising or tracking SDKs.
- It does not contact any analytics service (Firebase, Mixpanel,
  Amplitude, Google Analytics, Facebook SDK — none of them).
- It does not read your contacts, location, microphone, or camera.
  The Android permissions it requests are `INTERNET`,
  `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE` only.
- It does not host, curate, or distribute any media content. The
  legality of the streams you play through it is your responsibility,
  as you acknowledged when you first launched the app.

---

## Children

The app is intended for users aged 13 and older (the same minimum age
required by Google Play to operate an Android device account). The
app does not knowingly collect personal information from children.

---

## Changes to this policy

If the data the app handles changes materially — for example, if we
add a new third-party service — this document will be updated and the
effective date at the top bumped. Older versions remain available in
the [GitHub history](https://github.com/DerkSchooltink/GoorTV/commits/main/docs/privacy.md)
of this file.

---

## Contact

Questions about this policy or about how the app handles your data:
**goortv@proton.me**

The app is open source — the data inventory above is auditable in the
source code at
[github.com/DerkSchooltink/GoorTV](https://github.com/DerkSchooltink/GoorTV).
