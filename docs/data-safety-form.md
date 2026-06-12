# Play Console Data Safety form — answers (A3.3)

Canonical answers for **Play Console → App content → Data safety**. Derived
from the Phase 3 inventory in `PLAYSTORE_VIABILITY.md`, updated for Sentry
(A2.3) which the original inventory predates. Keep this file in sync with
what is actually submitted.

## Google's definition, applied

"Collected" means transmitted off the device **to the developer or parties
acting on the developer's behalf**. GoorTV sends user-typed URLs, Xtream
credentials, and HTTP headers only to the servers **the user themself
configures** — those parties act on the user's behalf, not ours, so they are
not "collection" under the form's definition. What we *do* operate is Sentry
(EU region), which receives crash reports. That is collection and is
declared below.

If a reviewer pushes back on the user-supplied-server reasoning, the
fallback is to additionally declare "Account info → User IDs" and "Personal
info → Other" as collected-optional with purpose "App functionality" — but
do not volunteer this; it misrepresents where the data goes.

## Section answers

**Does your app collect or share any of the required user data types?**
→ **Yes** (because of Sentry crash reports).

**Is all of the user data collected by your app encrypted in transit?**
→ **Yes** for everything we collect (Sentry is HTTPS-only). User-supplied
stream traffic may be cleartext HTTP, but that data is not "collected" by
us; cleartext support is additionally documented in
`network_security_config.xml` and the privacy policy.

**Do you provide a way for users to request that their data is deleted?**
→ **Yes** — Sentry events carry no account identity (PII off,
`sendDefaultPii = false`); deletion requests via goortv@proton.me are
honored by deleting matching events in Sentry. Sentry's retention
auto-expires events after 90 days regardless.

## Data types declared

### App info and performance → Crash logs
- Collected: **Yes** · Shared: **No**
- Processed ephemerally: No
- Required or optional: **Optional** — Sentry only initialises when the
  build has a DSN baked in, and users can avoid it by installing the
  GitHub/Obtainium build… **but** the Play build always has it →
  answer **Required** (no in-app opt-out today; revisit if a Settings
  toggle is added).
- Purpose: **App functionality** (crash diagnosis)

### App info and performance → Diagnostics
- Collected: **Yes** · Shared: **No**
- Required: same reasoning as crash logs → **Required**
- Purpose: **App functionality**

### Device or other IDs
- **Not collected.** Sentry PII is disabled; no advertising ID, no
  `ANDROID_ID`. Verify on upgrade of the Sentry SDK that
  `sendDefaultPii` remains false and no device identifiers appear in
  event payloads.

### Everything else → Not collected
Playlists/EPG URLs, Xtream credentials (Keystore-encrypted at rest, sent
only to the user's own provider), custom headers, favorites, watch
history, search history (on-device, max 5), preferences — none of it
reaches infrastructure we control.

## Cross-checks before submitting

- [x] Privacy policy (https://derkschooltink.github.io/GoorTV/privacy/)
      mentions Sentry crash reporting and the EU region — required since we
      declare crash-log collection. *Verified 2026-06-12 (`docs/privacy.md`
      "Anonymous crash reports" section).*
- [x] `isSendDefaultPii = false` in `App.kt`. *Verified 2026-06-12.*
- [ ] Merged release manifest still requests only INTERNET /
      ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE (re-check on the build that
      actually gets uploaded).
- [ ] Re-verify Sentry defaults whenever the SDK is upgraded.
