# GoorTV Improvement Backlog

Findings from architecture/structure review. Pick items one by one; check off when done.

Legend: **H** = high leverage · **M** = medium · **L** = low/cleanup

---

## 1. Sync & Lifecycle Ownership

- [x] **H** Move `epgSyncService.syncAll()` and `SourceSyncService.sync()` out of `HomeViewModel.init`. Own them in `Application.onCreate` (process-scoped `CoroutineScope` via Koin) or — better — `WorkManager` (one-time + periodic, `NetworkType.CONNECTED` constraint). Today, opening directly to Guide/Settings skips sync. (`HomeViewModel.kt:102-111`) — *Done: `AppSyncCoordinator` owns sync, triggered from `App.onCreate`. WorkManager deemed overkill for a TV app that runs foreground.*
- [x] **H** Add retry/backoff for failed initial sync. Currently `sync()` runs only if `channelDao.count() == 0`; first failure leaves an empty UI with no auto-recovery. — *Done: both `SourceSyncService.syncAll` and `EpgSyncService.syncAll` retry per source with exponential backoff (30s → 1m → 2m → 5m cap, 3 attempts).*
- [x] **M** Remove `Dispatchers.IO` from `HomeViewModel.kt:110`. `EpgSyncService` / `SourceSyncService` should `withContext(Dispatchers.IO)` internally around blocking work. — *Done: `EpgSyncService.processXmltv` wraps blocking XML parse in `withContext(IO)`. HomeViewModel no longer references Dispatchers.IO.*
- [x] **L** Promote duplicated `minuteTicker()` (in `HomeViewModel:69`, `PlayerViewModel:42`, `GuideViewModel:60`) into a single Koin-injected `TimeProvider` exposing a shared `StateFlow<Long>`. — *Done: `TimeProvider` (open class) injected into all three VMs; `FakeTimeProvider` in TestFixtures for deterministic tests. `SharingStarted.WhileSubscribed(5s)` pauses the timer when no screen subscribes.*

## 2. Data Layer Safety

- [x] **H** Add Room `MigrationTestHelper` androidTest for every step 1→9 plus full-chain test. Flip `exportSchema = true` in `AppDatabase.kt:14`. Currently zero coverage on 8 migrations. — *Done: `MigrationTest.kt` hand-rolls v1, walks all migrations, Room validates entity fingerprint on open. `schemas/9.json`+ now committed.*
- [x] **H** Wrap `SourceSyncService.sync` read+merge+replace (`SourceSyncService.kt:48-58`) in a single `withTransaction`. Serialize concurrent syncs per `sourceId` with a `Mutex`. Eliminates race and partial-failure window. — *Done: merge moved into `ChannelDao.replaceForSourcePreservingUserData(@Transaction)`. Per-source `Mutex` via `ConcurrentHashMap` in `SourceSyncService`.*
- [x] **H** Fix silent user-data loss when upstream channel URL changes. Today preservation is keyed only on URL match. Add fallback match: `tvgChannelId`, then `(sourceId, name, group)`. — *Done: cascade match URL → tvgChannelId → (name, group), single-consume to avoid double-apply.*
- [x] **M** Drop redundant `Index("tvgChannelId")` in `Channel.kt:19` — covered by composite `(sourceId, tvgChannelId)`. Saves write cost on large playlists. — *Done: schema v9→v10, `MIGRATION_9_10` drops the index.*
- [x] **M** Audit `ChannelDao.getAll()` / `getAllVisible()` callers. Unbounded `Flow<List<Channel>>` over 38k rows will jank/OOM. Paging variants exist — migrate consumers. — *Done: `getAll()` had no production callers; `getAllVisible()` replaced with narrower `getVisibleWithTvgId()` for the guide.*
- [x] **M** Add unique constraint or app-level guard on `(Source.type, Source.url)` — nothing prevents duplicate source rows today. — *Done: unique index on `(type, url)` (schema v10→v11, `MIGRATION_10_11` dedupes then indexes) + pre-check + snackbar in `SettingsViewModel`.*
- [ ] **L** Migrate `SearchHistoryRepository` from `SharedPreferences` (with constructor-time synchronous `load()`) to DataStore. Aligns with the rest of prefs.
- [ ] **L** Consider Keystore-wrapped column encryption (or SQLCipher) for Xtream `username`/`password` in `Source.kt:14-15` — only if threat model expands to rooted devices / forensic recovery.

**Follow-ups discovered during this work** (not blockers, log here so they don't get lost):
- [ ] **L** `HomeScreenTest` + `SettingsScreenTest` had stale VM constructor signatures — fixed minimally to compile, but assertion semantics may need a deeper pass (some mocks are now dead code).
- [ ] **L** Migration test execution requires a device/emulator (`task test:ui`); not yet wired into CI.

## 3. UI / Compose — Structure & Performance

- [ ] **H** Split `HomeScreen.kt` (536 lines, 12 `collectAsStateWithLifecycle` in root) into stateless sub-composables: channel list, top bar, FAB stack. Today every `nowMs` minute-tick recomposes the entire tree.
- [ ] **H** Fix unstable lambdas in `ChannelItem` (`HomeScreen.kt:340-426`). Hoist callbacks to take `channelId: Long`, wrap with `remember(channel.id)`. Currently 4 lambdas re-allocate per item per recomp, defeating Compose skipping.
- [ ] **M** Fix per-recomp `Pair` allocation in `HomeScreen.kt:283-285` (`item.channel.sourceId to it`). Either `remember(nowByChannel, item.channel)` or precompute in ViewModel.
- [ ] **M** Drop `nowMs: Long = System.currentTimeMillis()` default param at `HomeScreen.kt:347` — defeats Compose skipping.
- [ ] **M** Split `PlayerScreen.kt` (332-line composable body) and `SettingsScreen.kt` similarly.
- [ ] **L** Extract retry-prepare logic from `PlayerScreen.kt:288-305` (duplicates `:111-141`) into a local `fun preparePlayer()`.

## 4. UI / Compose — TV-Specific UX

- [x] **H** ~~Adopt `androidx.tv.material3` / `tv-foundation`~~ — extracted `tvFocusable()` modifier helpers (`rememberTvFocus` + `trackTvFocus` + `focusBorder`) in `dev/goor/tv/ui/util/TvFocus.kt`. Applied to RecentChannelCard, ProgrammeBlock, PlayerScreen back/aspect/cast buttons. Full `androidx.tv.material3` adoption deferred — separate project decision.
- [ ] **M** Add `FocusRequester` for first item of `LazyColumn`/`LazyRow` for D-pad entry. Add focus restoration after dialog dismiss (`HomeScreen.kt:297-322`, `SettingsScreen.kt:87-123`). — *Deferred: needs on-device testing to verify the UX doesn't hijack focus unexpectedly.*
- [x] **M** Make `MediaRouteButton` D-pad focusable (`PlayerScreen.kt:333-338`). — *Done: wrapped raw `AndroidView` in a focusable Box that owns the focus ring.*
- [x] **M** Add `BackHandler { }` to `PlayerScreen`. — *Done: explicit `BackHandler { onBack() }`. Cast session is owned at a higher level so it persists across screens — standard Android cast UX.*
- [x] **M** Use `rememberSaveable` for `aspectRatioMode`. — *Done with a custom `Saver<AspectRatioMode, Int>` storing ordinal.*
- [x] **M** Request focus on text fields when `AddEditChannelDialog`/`AddSourceDialog` opens. — *Done: `FocusRequester` on the Name field + `LaunchedEffect(Unit) { requestFocus() }` in both dialogs.*
- [ ] **L** Add `tvOverscanInsets` / 5% safe-area padding; content currently runs edge-to-edge.
- [x] **L** Replace `backFocusRequester.requestFocus()` try/catch in `PlayerScreen.kt:100-102` with cleaner pattern. — *Done: try/catch removed; `LaunchedEffect(Unit)` runs after the focus tree is built so the `IllegalStateException` race is no longer possible.*
- [ ] **L** `GuideScreen.kt:83-89` uses hardcoded `200` px offset to center "now"; use `BoxWithConstraints` for true viewport width.

## 5. UI / Compose — Dialogs & Validation

- [ ] **M** `AddEditChannelDialog.kt:30-43`: render delete-confirm as a second overlay, not a replacement. Today in-flight edits are lost if user cancels confirm.
- [ ] **M** `AddSourceDialog` (`SettingsScreen.kt:305-369`): add URL validation and required-field gating on Add button. Same regex as `AddEditChannelDialog.kt:21-24`.
- [ ] **M** `GroupsDialog` (`SettingsScreen.kt:208-303`): `selected` state goes stale if `source.includedGroups` updates externally. Use `rememberSaveable` or key the outer `remember`.
- [ ] **L** `EditSourceDialog` (`SettingsScreen.kt:382-453`): consider exposing manual EPG URL override for Xtream sources too, not just M3U.

## 6. Navigation

- [ ] **M** Migrate `AppNavigation.kt` to Compose Navigation 2.8 type-safe routes (`@Serializable` data classes). Eliminates `"player/$channelId"` interpolation, `navArgument` boilerplate, and the defensive `-1L` + `popBackStack` dance at `AppNavigation.kt:39-47`.

## 7. ViewModel Decomposition

- [x] **H** Extract custom-channel CRUD (`getOrCreateManualSourceId`, `HomeViewModel.kt:113-125`) into a `ManualSourceManager` or move into `SourceSyncService`. `HomeViewModel` currently has 7 deps mixing screen state, CRUD, and sync orchestration. — *Done: new `ManualSourceManager` owns the singleton MANUAL source lifecycle + add/update/delete; HomeViewModel now delegates. Mutex serializes `getOrCreate` so concurrent `addChannel` calls can't create duplicate MANUAL rows (previously caught only at the DB unique-index layer with a constraint error).*

## 8. Testing

- [ ] **H** Add `SourceSyncServiceTest` — URL-keyed favorite/`lastWatchedAt` preservation logic is high-risk and untested.
- [ ] **H** Room migration tests (see also section 2).
- [ ] **M** Add `PlayerScreen` androidTest — Cast button overlay, controls auto-hide after 4s, system bar restore on back are untested.
- [ ] **M** Extend Maestro coverage:
  - Player flow (channel tap → PlayerScreen → back restores system bars).
  - Favorite toggle.
  - Have `03_add_source_dialog.yaml` exercise a real public M3U so sync→list chain is verified end-to-end (today it taps Cancel).

## 9. Build, CI, Tooling

- [x] **H** Enforce lint + detekt in `ci.yml`. Add `./gradlew :app:lintDebug detekt` step. Configure detekt with a baseline so the gate is meaningful from day one. — *Done: detekt 1.23.8 wired via `allprojects {}`, config at `config/detekt/detekt.yml`, baseline at `config/detekt/baseline.xml` (62 grandfathered issues). Lint + detekt both gate CI. Also fixed real `UnsafeOptInUsageError` on Media3 unstable APIs in PlayerScreen.*
- [x] **M** Align `compileSdk` — app is 36 (`app/build.gradle.kts`), benchmark is 37 (`benchmark/build.gradle.kts:7`). Pick one in `libs.versions.toml`. — *Done: `compileSdk = 37` (and `minSdk = 26`, `targetSdk = 37`) centralized in version catalog; both modules read from it.*
- [x] **M** ~~Update `Taskfile.yaml` to actually include `lint`, `clean`, `logcat` tasks (currently documented in `CLAUDE.md` but missing).~~ — *No-op: Taskfile.yml already has all three. IMPROVEMENTS audit was stale.*
- [ ] **L** Consolidate `release.yml` and `auto-tag.yml` — both produce signed APKs via different triggers. Two paths to the same artifact is maintenance debt.
- [ ] **L** Set up baseline profile generator alongside `benchmark/` module.

## 10. Polish & Hygiene

- [ ] **M** Refresh `CLAUDE.md` — schema is v9 (not v7), 8 migrations (not 6), `task lint`/`clean`/`logcat` documented but don't exist, mentions Turbine but codebase uses `.value` + `runCurrent` patterns.
- [ ] **M** Externalize hardcoded strings to `strings.xml` and use `stringResource`. Today zero usage — blocks localization and string-ID-based testing.
- [ ] **L** Add `contentDescription` to `AsyncImage` channel logos (`HomeScreen.kt:478`, `PlayerScreen.kt:380`, `GuideScreen.kt:228`) — currently all `null`. Pass `channel.name` for TalkBack.
- [ ] **L** Extract magic dp/sp values to a `Dimensions.kt` (or theme tokens). `GuideScreen.kt:51-56` already does this — good template.
- [ ] **L** Replace `SimpleDateFormat` singletons (`PlayerScreen.kt:404-406`, `GuideScreen.kt:58-60`) with `java.time.format.DateTimeFormatter` (thread-safe).

---

## What's already good (don't touch)

- `GuideViewModel` — clean `combine` of flows into sealed `GuideState`.
- `Source.headersMap()` RFC 7230 + CR/LF guard at model layer.
- `replaceForSource` `@Transaction`; batched `applyTvgChannelIdAssignments`.
- `StreamConcurrencyTracker` as single source of truth for forced-stop.
- CI `auto-tag.yml` — path-filtered, tag-existence guarded.
- Signing via env vars only; no committed keystore.
- Version catalog is current.
- Unit tests are behavior-focused with shared `TestFixtures`.
- Single-module layout is appropriate for current size — don't split prematurely.
- No repository layer over DAOs — keep it that way at this scale.
