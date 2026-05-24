# Play Store listing copy (locked)

This is the canonical copy for the Google Play Store listing. Treat it as
the source of truth — paste verbatim into Play Console when submitting,
and PR any future changes through here first.

**Positioning rules** (from Phase 0 audit):

- Don't brand "IPTV" anywhere in the listing. Use "M3U", "media player",
  "live stream", "playlist", "channel guide" instead.
- Don't reference "Xtream Codes" by brand name — say "Xtream-compatible"
  or "Xtream API" generically when needed.
- Never imply the app distributes, hosts, or curates any content. The
  word "your" or "user-supplied" should appear in every paragraph that
  could be read as a content claim.
- Never name third-party providers, channels, or networks. No
  broadcaster names. No sport leagues. No movie titles.

---

## App title — A1.7

```
GoorTV — M3U Media Player
```

25 / 30 characters. Em-dash separator (Unicode U+2014, not a hyphen).
In-app branding (launcher icon label, top-bar title) stays just
`GoorTV` — the longer title is for store search ranking only.

---

## Short description — A1.6

≤ 80 characters. Appears in search results and the listing header.

```
Open-source media player for the M3U playlists, EPGs, and streams you supply.
```

77 / 80 chars. Front-loads "Open-source" (positive policy signal +
attracts the OSS audience), avoids "IPTV", makes the BYOC stance
unmistakable.

---

## Full description — A1.6

≤ 4000 characters. Appears on the listing page once expanded.

```
GoorTV is a clean, open-source media player for Android TV and Android phones.
It plays the M3U playlists, XMLTV electronic programme guides, and
Xtream-compatible streams that **you** supply.

The app ships with **no content**, no playlists, no preset URLs, and no
account system. You provide the source URLs; GoorTV renders them.

WHAT IT DOES

• Adds M3U and Xtream-API sources from URLs you paste into Settings
• Auto-syncs sources hourly and EPGs every six hours, on-demand refresh available
• Plays HLS, DASH, MP4, and MKV streams via Media3 ExoPlayer
• Surfaces a now-and-next mini-guide from your XMLTV data
• Lets you favorite channels, sort them, and hide ones you do not want to see
• Saves your last-watched timestamps so the Recently Watched rail stays useful
• Casts to any Google Cast receiver in your home network (where available)
• Works fully offline once a source is synced — no second network round-trip per channel switch

WHAT IT DOES NOT DO

• It does not contain advertising or tracking SDKs
• It does not contact any analytics service
• It does not host, curate, or distribute any media content
• It does not bundle any playlists, even as examples — you must add your own
• It does not phone home for content lookups — every request goes to a server you configured

PRIVACY

GoorTV does not collect personal information about you. The only data that
leaves your device is (a) network requests to the URLs you typed into
Settings, and (b) anonymous crash reports used to fix bugs. Crash reports
are scoped to non-personal technical data only (stack trace, device model,
app version) and are hosted on Sentry's EU data centres. IP address
capture, screenshots, view hierarchies, and session replay are all
explicitly disabled.

The Android permissions GoorTV requests are INTERNET,
ACCESS_NETWORK_STATE, and ACCESS_WIFI_STATE only.

Full privacy policy: https://derkschooltink.github.io/GoorTV/privacy/

OPEN SOURCE

GoorTV is published under an open-source licence and the data inventory
above is auditable in the source code:
https://github.com/DerkSchooltink/GoorTV

YOUR RESPONSIBILITY

The legality of the streams you play through GoorTV is your responsibility.
You confirm this on first launch. GoorTV is a media player, not a content
service — it has no way to verify the licensing status of arbitrary URLs
you configure, and it is your obligation to ensure you have the right to
access the sources you add.

REQUIREMENTS

• Android 8.0 (API 26) or later
• Phone, tablet, or Android TV
• Internet connection (Wi-Fi or Ethernet)
• Optional: a Google Cast receiver on the same network
```

Character count: ~3050 / 4000. Leaves headroom for future additions
without hitting the cap.

---

## Category & content rating — for reference

- **Category:** Entertainment → Video Players & Editors
- **Content rating:** IARC self-assessment: "Reference, News, or
  Educational" → rated 3+ (the app has no built-in content, so the
  IARC questionnaire has no boxes to check beyond "user-generated
  content moderated by the user themselves")
- **Tags / keywords:** m3u, media player, xmltv, epg, hls, dash, exoplayer,
  cast, android tv, open source

---

## Copy that we deliberately did NOT use

These phrases were considered and rejected for the reasons noted —
documented here so future revisions don't accidentally re-introduce them.

| Rejected phrase | Why |
|---|---|
| "IPTV" / "Internet TV" | Direct policy signal; correlates with delistings. |
| "Xtream Codes" | Branded name of a third-party panel. Say "Xtream-compatible" or "Xtream API" instead. |
| "Stream live TV channels" | Implies content provisioning. Use "play live streams you supply" instead. |
| "Watch your favorite shows" | Implies content curation. Reword as "play the channels you've added". |
| "1000s of channels" / channel-count claims | Risks looking like a content offer. The app ships with zero channels. |
| Names of any provider, panel, or service | Trademark + association risk. |
| "Premium", "Pro", "+" variants | We have one tier. Don't imply a paywall that doesn't exist. |
