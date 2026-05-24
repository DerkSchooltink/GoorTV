# Trademark check — "GoorTV" (A1.1)

Pre-submission clearance for the brand name we're about to plaster across
the Play Store listing. Goal: catch a conflict *before* the store rejects
us or a rights-holder serves a takedown.

This is a self-clearance — it does **not** replace formal advice from a
trademark attorney. If the listing succeeds and the app gets traction,
consider a paid clearance from a Benelux / EU IP firm before any rebrand
or paid acquisition spend.

---

## Preliminary findings (open-web, 2026-05-24)

Done via search engines and direct API probing. Trademark register
search engines (TMview, eSearch+, USPTO TM Search, BOIP Merkenregister)
are JavaScript SPAs with anti-bot tokens — they can't be queried from a
script. Manual searches below are still required.

### Identical / near-identical marks

- **"Goor TV"** — YouTube channel, entertainment/celebrity content.
  Channel name use alone does not establish a registered trademark, but
  if the channel is monetised + has commercial activity in the EU, the
  owner could have a common-law claim in some jurisdictions.
  Check: `youtube.com/c/GoorTV`
- **"goortv"** — Facebook handle, appears to be a personal account
  (Tatyana Guryanova). Personal handles do not establish trademark
  rights. Low concern.
- **No active `goor.tv` website** found at the apex domain as of this
  audit.

### Phonetically / visually similar (possible 8(1)(b) confusion analysis)

These don't block us, but a thorough register search will surface them
and a hearing officer might cite confusion risk:

- **"GO TV"** — Maltese broadcaster, listed on Google Play
  (`mt.com.go.iptv.android.devices`). Different spelling (no double-o,
  no "Goor") but the same descriptive "TV" suffix and short brand stem.
- **"GoTV"** — multiple unrelated services (Africa, US sports apps).
  Common name, weak distinctiveness.
- **"gorTV"** — Twitch streamer handle; personal, no commercial use.

### Geographic / descriptive observations

- **"Goor"** is a town in Overijssel, Netherlands. Geographic indicators
  receive **reduced distinctive character** under EUTMR Art. 7(1)(c) +
  Benelux Convention Art. 2.11(1)(c). A pure word mark "Goor" for
  AV/media services in the Benelux might be refused on absolute grounds
  unless we can show acquired distinctiveness.
- **"TV"** is descriptive for AV services and adds no distinctiveness
  on its own.
- **Combined "GoorTV"** as a single compound is more defensible than
  either word alone — combinations can clear distinctiveness even when
  parts can't. Comparable: "EasyJet", "MoneyGram".

### Risk summary

- **Conflict risk:** low-to-moderate. No identical registered mark found
  on open web. The YouTube "Goor TV" is the strongest signal worth a
  manual check.
- **Descriptiveness risk:** moderate. Geographic + descriptive parts
  combined. A figurative element (the launcher icon) in any future
  trademark filing would strengthen distinctiveness.
- **Play Store risk:** none of the surface conflicts would trigger an
  automated Play takedown. They'd surface only if a rights-holder
  reports us.

---

## Manual searches you still need to run

Each link below takes you straight into the public search UI of the
relevant register. The whole sweep is 5–10 min of clicking.

### 1. EUIPO eSearch+ (EU trademark register)

URL: <https://www.euipo.europa.eu/eSearch/>

- Search field: **`GoorTV`**, then **`Goor`**, then **`Goor TV`** (with space).
- Filter: **Trade marks → All statuses → Trade mark type: Word + Figurative**.
- Nice classes that matter for us:
  - **9** (downloadable software, mobile apps)
  - **38** (telecommunications, streaming, broadcasting)
  - **41** (entertainment, video-on-demand)
  - **42** (SaaS, cloud software)
- Flag any active EU mark in classes 9 / 38 / 41 / 42 — those are the
  classes a competing media-player or streaming brand would file in.

### 2. TMview (covers EU + 70+ national registers, including BX, US, GB, DE, FR…)

URL: <https://www.tmdn.org/tmview/>

- Search field: **`GoorTV`**, then **`Goor`**.
- Offices filter: at minimum **EM** (EUIPO), **BX** (Benelux), **WO**
  (WIPO international), **US**, **GB**.
- Same Nice classes as above.
- Sort by **most recent** to catch any 2024–2026 filings before they
  appear in eSearch+.

### 3. BOIP Merkenregister (Benelux — NL/BE/LU)

URL: <https://www.boip.int/en/search>

- Tab: **Trademarks**.
- Search: **`GoorTV`**, then **`Goor`**.
- Filter: **Nice classes 9, 38, 41, 42**.
- BOIP is the office to check most carefully because "Goor" is a Dutch
  geographic name — a local restaurant chain or media outlet might own
  it for an unrelated class but file an opposition if we publish in NL.

### 4. USPTO Trademark Search

URL: <https://tmsearch.uspto.gov/>

(TESS was retired; this is the replacement.)

- Search: **`GoorTV`**, then **`Goor TV`**, then **`Goor`**.
- Filter: live marks only, **classes 9 / 38 / 41 / 42**.
- Lower priority for us (we're targeting NL/EU first) but if we ever
  launch in the US the registration cost is the same whether we file
  in year 1 or year 5 — knowing now whether the lane is clear saves a
  rebrand later.

### 5. WIPO Global Brand Database (covers WIPO international filings, useful catch-all)

URL: <https://branddb.wipo.int/branddb/en/>

- Search: **`GoorTV`**, then **`Goor`**.
- Filter: status **Active**, classes **9, 38, 41, 42**.
- Designations: at minimum **EM, BX, US**.

---

## Decision criteria

- **All clear in all 5 registers for classes 9 / 38 / 41 / 42** → proceed
  with the Play Store listing as planned, document the date of the
  clearance in this file.
- **Identical mark found in any of those classes** → either change the
  name *or* engage a trademark attorney for a coexistence opinion before
  filing. Don't ship a clearly-conflicting name.
- **Similar mark found** (e.g. "Goor", "Gor TV", "GoorMedia") → judgement
  call. Note it here and either proceed (small risk) or escalate to
  counsel.
- **"Goor" alone is registered in our classes** → still likely fine for
  the compound "GoorTV", but document the prior mark and the rationale.

---

## Filing decision — should we register the mark ourselves?

Not part of A1.1, but worth flagging:

- **BOIP Benelux word + figurative filing:** ~€244 for 1 class, ~€271
  for 3 classes. 4–6 month process. Low cost, high upside.
- **EUTM (EUIPO) filing:** €850 for 1 class, +€50 for class 2, +€150 for
  class 3. Higher cost, covers all 27 EU member states.
- **Recommendation:** wait until the app has either (a) traction or
  (b) a clear competitor whose imitation we'd want to block. Filing
  pre-launch is a luxury, not a necessity, and a "GoorTV" compound mark
  is unlikely to be poached in the first 6 months post-launch.

---

## Audit log

| Date | Searcher | Registers checked | Conflicts found | Decision |
|---|---|---|---|---|
| 2026-05-24 | Claude (open web only) | Search engines, WIPO/TMview API probe | None identical; "Goor TV" YouTube channel noted as low-concern | Pending manual register sweep |
| | | | | |

After the manual sweep, add a row with date, searcher (you), the five
registers ticked off, anything flagged, and the go/no-go.
