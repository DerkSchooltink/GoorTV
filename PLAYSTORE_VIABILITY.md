# Play Store viability — verification plan

Living document. Last updated 2026-05-23. Phase 0 + 1 + 2 + 3 audits done;
4 + 5 + 6 still process-only (no engineering changes yet).

## Status snapshot

| Phase | Status | Verdict |
|---|---|---|
| 0 — Policy audit | ✅ done | **YELLOW** — listable today, hostile environment |
| 1 — Positioning | ✅ audited | Listing copy drafted, assets needed |
| 2 — Technical compliance | ✅ audited | 2 blockers (AAB upload, Crashlytics); 5 medium items |
| 3 — Privacy + Data Safety | ✅ audited | Inventory complete; UGC consent + privacy policy still needed |
| 4 — Pre-launch testing | ⏳ pending | Blocked on developer account + closed-testing recruit |
| 5 — Submission | ⏳ pending | Blocked on Phases 1–4 |
| 6 — Post-launch | ⏳ pending | Need Crashlytics + `noCast` flavor |

**Total engineering effort to ship-ready:** ~3.5 person-days, see
[Consolidated action items](#consolidated-action-items-prioritized) at the
bottom.

**Bottom line:** ship is feasible. The most important non-obvious calls
out of the audit:

1. **AAB not APK** — current `release.yml` produces an APK; Play requires
   AAB. Easy fix.
2. **No crash reporting** — Crashlytics or Sentry must be wired before
   closed-testing.
3. **No first-run UGC consent or privacy policy** — both are Play
   submission requirements for player apps that accept user URLs.
4. **Don't brand "IPTV" in the listing** — survivors avoid it; TiviMate's
   absence from Play correlates with explicit IPTV branding.

---

## TL;DR — the elephant in the room

GoorTV is a "bring your own content" IPTV player that accepts arbitrary M3U /
Xtream URLs. Google Play has tightened enforcement against IPTV apps over the
last ~2 years; many have been removed even when the streams themselves are
user-supplied. Comparable BYOC players that survive on Play (OTT Navigator,
some forks of IPTV Smarters) do so by leaning hard on "player only, no
bundled content" positioning. The strict ones (TiviMate) abandoned Play
entirely and went sideload-only.

---

## Phase 0 — Hard-blocker policy audit  (DONE 2026-05-23)

### Verdict: **YELLOW** — listable today, hostile environment

A BYOC IPTV / M3U player is **currently listable** on Google Play in NL/EU/US.
Multiple direct comparables — Smarters Pro variants, GSE Smart IPTV Pro,
Televizo, Perfect Player — are live as of May 2026. But the environment is
hostile and worsening; long-term presence isn't guaranteed.

### Evidence (as of 2026-05-23, re-verify before submission)

| App | Status | Notes |
|---|---|---|
| Smarters Pro / IPTV Smarters PRO | **Listed** (multiple package IDs, updated Mar–May 2026) | Original pulled; clones thrive under new dev accounts |
| GSE Smart IPTV (free) | **Delisted** 2024–25 | "GSE SMART IPTV PRO" (`com.gsetech.smartiptv2`) still live |
| OTT Navigator | **Not on Play**; sideload only | Deliberate Play avoidance |
| TiviMate | **Not on Play**; sideload only | Long-standing |
| Televizo | Listed but **reportedly caught in JioStar 2026 sweep** | Status volatile — re-verify |
| Perfect Player | Removed 2019 (bogus DMCA), now relisted | Demonstrates DMCA fragility |

### Key policy clauses (verified from `support.google.com`)

- **IP infringement** (`/answer/9888072`): no apps that infringe IP rights of
  others.
- **Deceptive Behavior** (`/answer/9888077`): listing copy must match runtime
  behavior; can't imply bundled channels.
- **UGC policy**: apps accepting user-supplied URLs need a first-run ToS, an
  in-app report/remove flow, and a definition of objectionable content.

### Top 3 risk drivers (from the survey)

1. **Rightsholder DMCA / direct takedown — dominant pattern.** JioStar pulled
   36 IPTV apps during the T20 World Cup 2026. DAZN Italy 2025. Perfect
   Player 2019. These bypass policy review entirely.
2. **Manual review on update submission.** Triggers: brand-named providers in
   description, screenshots with recognizable broadcaster logos, "watch live
   TV / sports / movies" framing.
3. **Pre-loaded content / referral to pirate providers** — instant ban.
   Correlated with GSE's original removal.

### Jurisdictional notes

- Italy's "Pezzotto" enforcement targets **end users and stream distributors,
  not player-app developers** to date. No EU member-state action against pure
  player apps.
- US DMCA §1201 not implicated — no DRM circumvention; ExoPlayer plays open
  HTTP streams.
- Cast SDK has no IPTV-specific flag.

### Listing-copy pattern from survivors

Smarters family disclaimer (verbatim from listing snippet):
> "Smarters IPTV Player is not affiliated with any third-party providers and
> does not condone unauthorized streaming of copyrighted content. The app does
> not provide or include any media content, and users must supply their own
> playlists or streaming links."

### Recommended mitigations (carry forward into Phases 1 + 2)

**Listing copy:**
- Title: prefer "GoorTV — M3U Media Player" over IPTV-prefixed branding
  (TiviMate's deliberate Play avoidance correlates with explicit "IPTV"
  framing).
- First paragraph: explicit BYOC disclaimer, no content/playlists/channels
  included.
- Screenshots: zero recognizable broadcaster logos. Use a public-domain HLS
  sample (e.g. Mux / Bitmovin test streams).
- Avoid "Xtream Codes" by brand name — call it "Xtream-compatible API".
  "Xtream Codes" itself was a piracy-ecosystem brand prosecuted in 2019.

**App code (new follow-ups for the IMPROVEMENTS backlog):**
- First-run ToS gate before any source can be added; checkbox + persisted
  acceptance.
- In-app "report this source" + one-tap remove on every channel / source row.
- No default playlists, no auto-suggest, no discovery features.
- Visible legal-use disclaimer in Settings.

### Confidence calibration

- Policy text: high confidence (fetched May 2026 directly).
- App-status table: **medium — Play listings churn weekly**, particularly
  post-JioStar sweep. Re-verify the day of submission.
- Pezzotto / DMCA scope: high.
- Staleness window: **~8–12 weeks**. Rightsholder takedown campaigns are
  event-driven (major sports tournaments); expect another wave around the
  2026 FIFA World Cup.

### Decision

**Proceed to Phase 1 with eyes open.** First listing is feasible. Long-term
presence depends on whether a rightsholder decides to sweep BYOC players —
that risk is real and recurring. Have the F-Droid + GitHub Releases fallback
ready *before* submission, not after.

---

## Phase 1 — Positioning & metadata

How the listing reads matters more than the code for surviving policy review.
Phase 0 made this concrete: avoid "IPTV" and "Xtream Codes" by brand name,
mirror the disclaimer language that listed survivors use.

### App identity

- **Title**: `GoorTV — M3U Media Player` (recommended over `GoorTV` alone
  because it telegraphs the BYOC posture in the title itself, which
  reviewers see first).
- **Developer name**: must match the verified individual / org account.
- **Package name**: `dev.goor.tv` ✓ (already set).
- **Trademark check** — Action: search USPTO TESS, EUIPO TMview, and BOIP
  (Benelux) for "GoorTV". Likely clean (`Goor` is a Dutch place name + a
  common Dutch surname); confirm before listing.

### Descriptions (draft)

**Short description (80 char):**
> Open M3U / Xtream-compatible media player for Android TV and phones.

**Full description (≤ 4000 char) — first draft:**
> GoorTV is a free, open-source media player for Android TV and Android
> phones. It plays user-supplied M3U playlists and Xtream-compatible
> streams that you bring yourself.
>
> GoorTV does not include, host, or curate any media content. The app
> ships with no playlists, no channels, and no preset URLs. To use it,
> you point it at a media source you legally own or are authorized to
> access — for example, a local server, a personal IPTV subscription,
> or a public-domain stream catalogue.
>
> Features:
> • Multiple playlists / sources at once
> • EPG (electronic programme guide) via XMLTV
> • Channel grid with now/next, favorites, search
> • Android TV remote / D-pad first-class
> • Cast to Chromecast and DLNA renderers (Cast SDK)
> • HLS, MPEG-TS, MP4, and most ExoPlayer-supported formats
>
> GoorTV is a player application. It is your responsibility to ensure
> the content you stream through it is licensed for your use.
>
> Source code: https://github.com/DerkSchooltink/GoorTV

(Tune wording before submission — the structure is sound; the words
matter to reviewers.)

### Listing metadata

| Field | Value |
|---|---|
| Category | Video Players & Editors |
| Tags | M3U Player, Media Player, XMLTV, Android TV, HLS, Cast |
| Content rating | Everyone (no content shipped; user-supplied) |
| Pricing | Free, no ads, no IAP |
| In-app purchases | None — confirms ToS exemption |
| Designed for TV | Yes (banner + LEANBACK_LAUNCHER both set) |

### Screenshots & graphics

- **App icon**: ✓ ships at `res/mipmap-*/ic_launcher.png`. Verify it follows
  Google's adaptive-icon spec (foreground + background layers).
- **Feature graphic** (1024×500): not yet produced. Action.
- **TV banner** (1280×720): ✓ at `res/drawable/banner.png` (640×360 — Play
  requires 1280×720 for *TV banner asset upload*; the in-app banner is a
  separate requirement and is fine at 640×360 per current Android TV docs).
- **Phone screenshots** (3–8): not yet produced. Must avoid recognizable
  broadcaster logos. Easiest path: capture against a tiny fixture M3U
  bundled in the test infra (also unblocks §8.M Maestro real-M3U sub-item).
- **Tablet screenshots**: optional but listing-quality.
- **TV screenshots** (3–8, 16:9): mandatory for Android TV listing.

### Action items

- [ ] **A1.1** Trademark check for "GoorTV" (USPTO TESS, EUIPO TMview, BOIP).
- [ ] **A1.2** Produce a 1024×500 feature graphic.
- [ ] **A1.3** Produce a 1280×720 TV banner asset (separate from the in-app
  banner).
- [ ] **A1.4** Build a tiny fixture M3U for screenshot capture — bundle a
  `screenshots.m3u` with placeholder names ("Demo News", "Demo Sports",
  "Public Domain Channel") and public-domain stream URLs (e.g. Big Buck
  Bunny HLS, Tears of Steel). Same fixture can host the Maestro real-M3U
  flow.
- [ ] **A1.5** Capture 5+ phone screenshots and 5+ TV screenshots against
  the fixture.
- [ ] **A1.6** Draft and lock the final 80-char short and ≤4000-char full
  descriptions.
- [ ] **A1.7** Decide on rename: `GoorTV` vs `GoorTV — M3U Media Player`.

---

## Phase 2 — Technical & manifest compliance

Findings from a code-side audit on 2026-05-23 (branch `main` @ `b58477e`).

### Status table

| Topic | Status | Detail |
|---|---|---|
| Target SDK | ✅ | `targetSdk = 37` (`libs.versions.toml`). Play requires 35+ for submissions since Aug 2025; we exceed it. |
| compileSdk / minSdk | ✅ | `compileSdk = 37`, `minSdk = 26` — Android 8.0+ supported. |
| Release signing | ✅ pipeline / ⚠ Play App Signing | `release.yml` decodes a base64 keystore from `secrets.KEYSTORE_BASE64` and signs via env-var passwords. The current secrets are an upload key, NOT the Play App Signing key. Need to enroll in Play App Signing and confirm the same upload key is what's used. |
| Build artifact | ❌ APK | `release.yml:31` runs `./gradlew :app:assembleRelease` and uploads `app-release.apk`. Play **requires AAB** for new apps. Must switch to `bundleRelease` + upload `.aab`. |
| ProGuard / R8 | ✅ | `isMinifyEnabled = true`, `isShrinkResources = true` in release. Cast SDK / Mediarouter / Koin / Room entities are explicitly kept in `app/proguard-rules.pro`. |
| Permissions | ✅ minimal | Manifest declares only `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`. No surprising transitive permissions from Cast SDK in the merged manifest (verify with `./gradlew :app:processReleaseManifest && cat app/build/intermediates/merged_manifests/release/AndroidManifest.xml` before submission). |
| `usesCleartextTraffic` | ⚠ | Set to `true` — required because many M3U/Xtream providers serve over plain HTTP. Must be **explicitly disclosed** in Play listing or rationalized in the Data Safety form (it does NOT block submission but reviewers do flag it). |
| Network Security Config | ❌ missing | No `network_security_config.xml`. Recommended to scope cleartext to user-supplied hosts (impossible without enumeration) or simply document the global allow. Status quo is acceptable but suboptimal. |
| TV manifest | ✅ | `<uses-feature android:name="android.software.leanback" required="false"/>` ✓. `LEANBACK_LAUNCHER` intent-filter present. `android:banner` references `@drawable/banner` ✓. |
| `android:allowBackup` | ✅ | `false` — sensible for an app with Xtream credentials in plaintext Room. |
| 64-bit native libs | ⏳ verify | Media3 ExoPlayer + Coil + Ktor bring native libs. Verify all ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) ship in the AAB. Action: `./gradlew :app:bundleRelease`, then `bundletool dump manifest --bundle app/build/outputs/bundle/release/app-release.aab` and `unzip -l ... \| grep lib/`. |
| Crash reporting | ❌ none | No Crashlytics, Sentry, or any crash-aggregator wired. **Action required** — without this, you can't tell when a release regresses on real-world devices. Adding Crashlytics is straightforward (Firebase plugin + `crashlytics:25.x`). Sentry is an alternative if you prefer OSS-friendly attribution. |
| Install size budget | ⏳ measure | Not yet measured. Action: `./gradlew :app:bundleRelease`, then `bundletool get-size total --apks=...`. Aim for <30 MB AAB on `xxxhdpi`. Cast SDK is a significant contributor; budget accordingly. |
| Baseline profile | ❌ none | A `baseline-prof.txt` would speed cold-start materially on TV (where launch perf is judged). Exists as an open follow-up in `IMPROVEMENTS.md §9 baseline profile generator`. |

### Action items

- [x] **A2.1** Switch `release.yml` from `assembleRelease` → `bundleRelease`, upload `.aab` not `.apk`. *(Done — workflow now builds both; APK kept for GitHub-Releases sideload, AAB added for Play.)*
- [ ] **A2.2** Enroll in Play App Signing, generate a new upload key, rotate `KEYSTORE_BASE64` secret if needed, document the procedure for re-issuing the key if compromised.
- [ ] **A2.3** Add Firebase Crashlytics (or Sentry — pick one). This is the single biggest gap.
- [ ] **A2.4** Verify all 4 ABIs ship in the AAB (`bundletool` after a release build).
- [ ] **A2.5** Measure AAB install size; act if >30 MB.
- [x] **A2.6** Add a `network_security_config.xml` even if it only documents the cleartext allow. Helps with the Data Safety form rationale. *(Done — `app/src/main/res/xml/network_security_config.xml`, referenced from manifest.)*
- [ ] **A2.7** Generate a baseline profile via Macrobenchmark module (already in repo). Improves cold-start metrics that Play's pre-launch report grades.

---

## Phase 3 — Privacy & data-safety form

Mandatory Data Safety form is part of submission. Lying = removal.

### Data inventory (from code audit, 2026-05-23)

| Data | Origin | Leaves device? | Where to | Disclose as |
|---|---|---|---|---|
| User's IP address | Network stack | ✅ Yes | User-supplied M3U / EPG / stream hosts; image-logo hosts; Google Cast receiver during cast | Inherent to network access; document but no special disclosure beyond "uses network" |
| M3U / EPG playlist URLs | User input (Settings → Add Source) | ✅ Egress in HTTP requests | The user-chosen server | Disclose as data they typed in; their choice |
| Xtream Codes **username** | User input | ⚠ Egress | The Xtream provider (sent as URL parameter) | "Account info / User IDs" — must declare |
| Xtream Codes **password** | User input | ⚠ Egress | The Xtream provider (sent as URL parameter) | "Personal info / Passwords" — must declare |
| Custom HTTP headers (`Source.headers`) | User input | ✅ Egress | Whatever target host requires them (typically auth bearer tokens) | "App activity / Other" or "Personal info / Passwords" depending on content |
| Stream media URL during cast | Channel URL | ✅ Egress | Google's default Cast Media Receiver app `CC1AD845` (Google-operated) | "Account info / User IDs" if URL contains creds; "App info & performance" otherwise |
| Channel logo URLs | M3U `tvg-logo` attribute | ✅ Egress | Whatever CDN the user's M3U references | "App info — referer/IP exposed" |
| Search history (5 queries max) | User typing in search box | ❌ on-device only | `SearchHistoryRepository` → SharedPreferences | Not collected per Google definition (no upload) |
| Recently-watched timestamps | DAO writes on play | ❌ on-device only | Room | Not collected |
| Favorites | DAO writes on toggle | ❌ on-device only | Room | Not collected |
| User preferences (sort order, included groups) | DataStore | ❌ on-device only | DataStore | Not collected |
| Crash data | (none today) | ❌ | (none) | Will need disclosure if Crashlytics added (A2.3) |

### Critical risk: Xtream credentials at rest

`Source.username` and `Source.password` are stored as plain TEXT columns in
the Room database (`Source.kt:21-22`). On a rooted device or via ADB backup
(disabled — `allowBackup="false"` ✓) they're readable. Two paths:

- **Path A**: declare plaintext on-device storage and rely on `allowBackup=false`
  + Android sandboxing. Acceptable per Play policy if disclosed.
- **Path B**: implement Android Keystore-wrapped encryption for the
  username/password columns. Already an open low-priority item in
  `IMPROVEMENTS.md §2`. Slightly safer but adds key-management complexity
  (key destroyed if user clears app data = stored creds become unreadable).

**Recommendation:** ship Path A for v1, declare honestly, plan Path B as a
v1.x improvement. Path A is what nearly every Xtream-compatible player does
today; users expect creds to be cleared with the app.

### Privacy policy

Required. Even a static GitHub Pages markdown page suffices.

### Action items

- [ ] **A3.1** Decide on Path A vs Path B for Xtream credential storage.
- [ ] **A3.2** Write the privacy policy. Skeleton:
  - What data the app collects (essentially: nothing leaves device, except
    network metadata to user-supplied URLs and Google's Cast receiver).
  - What rights users have (the data IS on their device; uninstall = deleted).
  - Contact email.
  - Effective date.
  - Host at `https://<user>.github.io/goortv-privacy/` (separate repo or
    `docs/` in this one).
- [ ] **A3.3** Fill the Data Safety form per the table above. Mark "Data
  encrypted in transit" honestly — HTTPS yes, cleartext HTTP no, depends on
  user-supplied URL.
- [x] **A3.4** Add a one-time UGC consent dialog (per Play UGC policy
  requirement surfaced in Phase 0): user must accept ToS before adding their
  first source. Persist acceptance to DataStore. *(Done — `UgcConsentGate` mounted at the navigation root, backed by `UserPreferencesRepository.tosAccepted`.)*
- [ ] **A3.5** Add a per-source / per-channel "report" or "remove" action that
  the UGC policy expects. Channels already have a delete path via Edit dialog
  for custom channels; for synced channels there's no per-row removal — the
  whole source removes. Document this explicitly or add per-channel hide.

---

## Phase 4 — Pre-launch testing

Process-heavy; wall-clock dominated by Google-imposed 14-day closed-testing
requirement for new individual accounts.

### Steps

1. **Internal testing track** (1 hour active work, 1 day wall)
   - Up to 100 testers; no policy gating. Used to shake out the upload
     pipeline and verify Play App Signing works end-to-end before involving
     anyone else.
   - Action: invite yourself + 1 trusted tester. Push a build.
2. **Closed testing track** (≤ 20 testers minimum, **14 days wall**)
   - Google policy effective Nov 2023: new individual accounts must run a
     closed test with ≥ 20 testers for ≥ 14 days before being eligible to
     promote to production. **You cannot skip this.**
   - Action: recruit 20+ testers in advance via the GitHub repo, IPTV
     subreddits, NL Android communities. Plan for the recruit cost.
3. **Pre-launch report** (automatic, 1 hour to review)
   - Play Console runs the AAB on Firebase Test Lab on a handful of real
     devices automatically. Returns crash reports, accessibility issues,
     content warnings, performance metrics.
   - Common failures: `usesCleartextTraffic` flagged as a security
     warning (informational only), missing landscape support, low-FPS on
     low-end devices.
4. **On-device manual testing** (2-4 hours)
   - Chromecast with Google TV (~$50) is the cheapest TV target.
   - Nvidia Shield TV if available — most demanding users live here.
   - Mid-range phone (Pixel 6a class).
   - Manual happy-path: add source → sync → play → cast → background/foreground
     → background sync triggers on resume.
5. **Crash-free rate gate** (passive)
   - Requires Crashlytics (A2.3). Target: >99% across 24h of closed-testing.
     Investigate every crash before promoting.

### Action items

- [ ] **A4.1** Create Google Play developer account ($25 one-time). Individual
  vs organization decision needed.
- [ ] **A4.2** Recruit ≥ 20 closed testers. Set up a testing-channel signup
  (Google Group or "join the beta" GitHub readme link).
- [ ] **A4.3** Run Pre-launch Report after first upload; triage findings.
- [ ] **A4.4** Acquire a Chromecast with Google TV (or other Android TV
  device) for manual testing. Currently testing on a Pixel 9a — that's
  phone-only. The existing Macrobenchmark module gives baseline
  numbers once a TV device is available; re-run after each release candidate.

---

## Phase 5 — Submission & review

Wall-clock-dominated by Google's review queue (1–3 weeks, longer for IPTV
apps in busy periods).

### Steps

1. **Account verification** (1–3 days)
   - Phone + ID verification for individual accounts since 2023.
   - Tax info for paid distribution (skip if free-only).
2. **Listing finalisation** (1 hour)
   - Upload all Phase 1 assets (description, screenshots, banner, icon).
   - Fill Data Safety form per Phase 3.
   - Content rating questionnaire.
   - Target audience: 13+ (Teen) — avoids "Designed for Families" review path
     which is stricter and irrelevant for an IPTV player.
   - Designated countries: NL, EU, US to start. Avoid Italy (Pezzotto-driven
     scrutiny) and CN/RU regions where IPTV is heavily regulated.
3. **AAB upload** (5 minutes, assumes A2.1 done)
4. **Review submission** (1 hour active; 1–3 weeks waiting)
   - **Expect rejection on first attempt.** Common rejection reasons for
     IPTV-adjacent apps: "App description misrepresents functionality"
     (usually a tone-of-voice issue), "Facilitates IP infringement" (catch-all
     — needs response with the survivor disclaimer language).
5. **Rejection response template** (have ready):
   > GoorTV is a media player application. It is functionally a wrapper
   > around Media3 ExoPlayer that accepts user-supplied M3U / XMLTV /
   > Xtream-compatible URLs as input. The app does not bundle, host,
   > curate, or distribute any media content. Users supply their own
   > sources — local files, personal IPTV subscriptions, public-domain
   > catalogues — and are responsible for ensuring those sources are
   > licensed for their use. This is equivalent in policy posture to
   > generic media players (e.g. VLC for Android), differing only in
   > supported input format. The listing description, screenshots, and
   > Data Safety disclosures accurately reflect this user-content-only
   > model.

### Action items

- [ ] **A5.1** Complete account verification BEFORE Phase 4 closed-testing
  begins so you don't block on it.
- [ ] **A5.2** Decide target countries. Start narrow (NL/EU/US).
- [ ] **A5.3** Save the rejection-response template (above) somewhere
  retrievable; you will use it.

---

## Phase 6 — Post-launch

### Ongoing operations

- **Crash monitoring** via Crashlytics → triage weekly. Threshold: >0.5% crash
  rate per release = investigation; >1% = hotfix.
- **1-star review monitoring** for "doesn't have channels" / "doesn't work"
  complaints — deflect with canned BYOC explanation. Don't engage with bait
  reviews.
- **Policy-update email monitoring**. Google occasionally tightens IPTV
  policy with 30-day update windows. Subscribe to Play Console emails.
- **Quarterly re-verification** of the Phase 0 comparable-app table. If
  comparable apps start dropping off Play in a wave, that's a signal to
  preemptively warn users via the listing description that fallback channels
  exist.

### Fallback distribution (have this ready BEFORE Phase 5)

| Channel | Status | Notes |
|---|---|---|
| GitHub Releases | ✅ ready | `auto-tag.yml` already produces signed APKs. No action needed. |
| F-Droid | ⚠ needs a flavor | Cast SDK is non-FOSS Google Play Services; F-Droid requires a `noCast` build flavor. Action: introduce `cast` / `noCast` product flavors in `app/build.gradle.kts`, conditional-compile Cast UI behind a `BuildConfig` flag. ~half-day work. |
| Amazon Appstore | ⏳ untested | More permissive on IPTV historically. Fire TV is Amazon-only. ~half-day to set up an account and submit. |
| Aptoide / Uptodown | ⏳ low effort | Sideload-grade trust; useful for capturing users who can't or won't use Play. |

### Action items

- [ ] **A6.1** Set up Crashlytics alerting (email/Slack) for crash-rate spikes
  (depends on A2.3).
- [ ] **A6.2** Pre-emptively add a `noCast` build flavor for F-Droid
  publishing. Builds on the existing `PlayerEngine` seam — Cast removal is
  cleaner now post-#40 than before.
- [ ] **A6.3** Subscribe the maintainer's Play Console email to policy
  updates.
- [ ] **A6.4** Schedule a quarterly /loop reminder to re-verify Phase 0
  comparable-app table.

---

## Effort estimate (excluding policy waits)

| Phase | Engineering time | Wall time |
|---|---|---|
| 0 — Policy audit | 1–2 h | 1 d |
| 1 — Positioning | 3–5 h | 1 d |
| 2 — Technical compliance | ~1 d | 2 d |
| 3 — Data safety form | 2 h | 0.5 d |
| 4 — Pre-launch testing | 4–8 h | **2 weeks (closed-testing requirement)** |
| 5 — Submission | 1 h | **1–3 weeks (review wait)** |
| 6 — Post-launch | ongoing | ongoing |

**Total to first listing: ~5 weeks elapsed, ~3 person-days engineering.**
Most of the wall time is Google-imposed waiting, not work.

---

## Consolidated action items (prioritized)

Everything that needs to happen, sourced from Phases 1–6. Grouped by
prerequisite chain so you can pick up where this leaves off.

### Blocker — must do before any submission attempt

- [x] **A2.1** Switch `release.yml` from `assembleRelease` → `bundleRelease`,
  upload `.aab`. *(Done — both artifacts published per release tag.)*
- [ ] **A2.3** Wire Crashlytics (or Sentry). Without this, you cannot
  monitor the closed-test phase meaningfully.
- [ ] **A3.2** Write + host the privacy policy.
- [ ] **A3.3** Fill the Data Safety form per the inventory table.
- [x] **A3.4** UGC consent dialog on first launch (Play UGC policy).
- [ ] **A4.1** Create Play developer account, complete identity verification.
- [ ] **A5.1** Account verification done before Phase 4 closed-testing.

### High value — visibly improves listing-acceptance odds

- [ ] **A1.6** Lock the description copy (avoid "IPTV" and "Xtream Codes"
  by brand name).
- [ ] **A1.7** Decide on title: `GoorTV` vs `GoorTV — M3U Media Player`.
- [ ] **A1.4** Bundle a fixture M3U for screenshots + Maestro.
- [ ] **A1.5** Capture phone + TV screenshots against the fixture.
- [ ] **A1.2 / A1.3** Produce feature graphic (1024×500) and TV banner asset
  (1280×720).
- [ ] **A3.1** Decide Xtream credential storage path (plaintext vs Keystore).
- [ ] **A3.5** Per-channel hide / per-source remove for UGC moderation.
- [ ] **A4.2** Recruit ≥ 20 closed testers, set up signup flow.

### Medium — improves quality but not gating

- [ ] **A1.1** Trademark check for "GoorTV".
- [ ] **A2.2** Enroll Play App Signing; rotate keys if needed.
- [ ] **A2.4** Verify all 4 ABIs in the AAB.
- [ ] **A2.5** Measure AAB install size.
- [x] **A2.6** Add `network_security_config.xml`.
- [ ] **A4.3** Run pre-launch report, triage.
- [ ] **A4.4** Acquire a Chromecast with Google TV for manual TV testing.
- [ ] **A5.2** Decide target country list.
- [ ] **A5.3** Save rejection-response template somewhere retrievable.

### Lower priority — post-launch / robustness

- [ ] **A2.7** Generate a baseline profile via Macrobenchmark.
- [ ] **A6.1** Crashlytics alerting.
- [ ] **A6.2** `noCast` build flavor for F-Droid.
- [ ] **A6.3** Subscribe Play Console policy emails.
- [ ] **A6.4** Quarterly Phase 0 re-verification.

### Estimates

| Bucket | Items | Effort |
|---|---|---|
| Blocker | 7 | ~2 days engineering |
| High value | 9 | ~1 day engineering + ~half-day design (graphics) |
| Medium | 9 | ~half-day engineering + hardware purchase |
| Lower priority | 5 | ~1 day, can spread over weeks post-launch |

**~3.5 person-days of engineering work to ship-ready state**, not counting
review waits.

### Decisions still required from the maintainer

1. **Personal project or commercial intent?** Affects account type and tax
   posture.
2. **Title rename to `GoorTV — M3U Media Player`?** Recommended.
3. **Xtream credential storage — Path A (plaintext + disclosure) or Path B
   (Keystore)?** Recommended A for v1.
4. **`noCast` flavor for F-Droid — yes or no?** Affects scope of A6.2.
5. **Crashlytics vs Sentry?** Personal preference; Crashlytics is more
   integrated with Play Console.
6. **Monetization?** Free / Free+ads / Paid / Donations. Affects review path
   and listing copy.
7. **Target countries?** NL/EU/US recommended start; explicitly exclude IT.
