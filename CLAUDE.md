# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
task build                    # assembleDebug
task build:release            # assembleRelease

# Deploy & run
task install                  # installDebug
task run                      # installDebug + launch MainActivity

# Tests
task test:unit                # ./gradlew :app:testDebugUnitTest
task test:ui                  # ./gradlew :app:connectedDebugAndroidTest  (needs device/emulator)
./gradlew :app:testDebugUnitTest --tests "dev.goor.tv.ui.screens.home.HomeViewModelTest"

# Maestro E2E (needs device)
task maestro                  # all flows
task maestro:flow -- 01_empty_state.yaml

# Perf
task benchmark                # macrobench scroll (needs benchmark build type)
task profile                  # 8s gfxinfo framestats capture

# Misc
task lint
task logcat
task clean
```

## Architecture

Single-module Android app (`app/`, sources under `src/main/kotlin/`), Kotlin + Jetpack Compose, targeting Android TV (minSdk 26).

**Data flow**: Room DAOs + DataStore → `StateFlow` in ViewModels → Compose UI. There is no generic repository layer — DAOs are injected directly. The few repository-named classes (`SearchHistoryRepository`, `UserPreferencesRepository`) wrap DataStore-backed prefs, not Room. `ManualSourceManager` owns the singleton "Custom Channels" MANUAL source and its CRUD.

**DI**: Koin (`di/AppModule.kt`) — single module wiring `AppDatabase`, DAOs, `HttpClient` (`defaultHttpClient()` factory using Ktor Android engine), `XtreamApi`, `SourceSyncService`, `EpgSyncService`, `AppSyncCoordinator`, `TimeProvider`, `StreamConcurrencyTracker`, `ManualSourceManager`, `SearchHistoryRepository`, `UserPreferencesRepository` (over a `DataStore<Preferences>` at `user_prefs`), and four ViewModels. `PlayerViewModel` takes a `channelId: Long` as a parametrized injection (`params.get()`).

**Navigation**: `ui/navigation/AppNavigation.kt` uses Compose Navigation 2.8 type-safe routes — `@Serializable object Home / Settings / Guide` and `@Serializable data class Player(channelId: Long)`. Four screens, all under `ui/screens/{home,player,settings,guide}/`.

**Source sync**: `AppSyncCoordinator` (process-scoped, kicked off once from `App.onCreate`) runs `SourceSyncService.syncAll()` then `EpgSyncService.syncAll()` — opening the app directly to any screen still triggers a sync. Both services throttle by `lastSyncedAt` / `lastEpgSyncedAt` (1 h sources, 6 h EPG) and retry each failed source with exponential backoff (30 s → 1 m → 2 m capped at 5 m, 3 attempts). Manual refresh from Settings passes `throttleMs = 0L` to bypass the throttle. Two source types — `M3U` (Ktor + `M3uParser`) and `XTREAM` (Xtream Codes API via `XtreamApi`). The atomic merge that preserves user data (`replaceForSourcePreservingUserData` on `ChannelDao`) cascades through URL → tvg-id → (name, group) so favorites and lastWatchedAt survive upstream URL churn. Per-source `Mutex` in `SourceSyncService` serializes concurrent syncs of the same source.

**Time**: A shared `TimeProvider` (Koin singleton) exposes a single minute-cadence `StateFlow<Long>` that Home/Player/Guide ViewModels collect — all screens tick on the same edge and the timer pauses 5 s after the last subscriber.

**Casting**: Google Cast via `play-services-cast-framework` using the default media receiver (`CC1AD845` — no Play Store registration required). `CastOptionsProvider` in `cast/` is wired via manifest `meta-data`. The player wraps `MediaRouteButton` in a focusable `Box` and dispatches D-pad Enter via `onPreviewKeyEvent → performClick()` since `AndroidView` doesn't bubble key events to `Modifier.clickable`. `MainActivity` extends `AppCompatActivity` (not `ComponentActivity`) so `MediaRouteButton` can show its `FragmentDialog`.

**Player UI**: `PlayerView` runs with `useController = false`. Controls (aspect-ratio button, Cast button) are a Compose overlay that appears on tap and auto-hides after 4 s. System bars are hidden on player entry and restored on back. `aspectRatioMode` is `rememberSaveable` so the user's choice survives config changes. `BackHandler { onBack() }` makes hardware back explicit.

**TV focus**: `ui/util/TvFocus.kt` provides `rememberTvFocus()` + `Modifier.trackTvFocus()` + `Modifier.focusBorder()` so screens get a consistent D-pad focus indicator without re-implementing the `var isFocused by remember + onFocusChanged + inline border` boilerplate. Full `androidx.tv.material3` adoption is tracked as a follow-up.

**Database**: Room, DB name `goortv.db`. `Channel` has a cascade-delete FK to `Source`. Current schema version is **11**; migrations `MIGRATION_1_2` through `MIGRATION_10_11` are defined in `AppDatabase`. `exportSchema = true` writes `app/schemas/<dbClass>/<version>.json` on every build — those files are committed and consumed by `MigrationTestHelper` in `app/src/androidTest/`. v9 added a composite `(sourceId, tvgChannelId)` index, v10 dropped the now-redundant standalone tvg-id index, v11 added a unique `(type, url)` index on `sources`.

**Assets**: App launcher icon lives in `res/mipmap-*/ic_launcher.png` (mdpi → xxxhdpi). Android TV home screen banner is `res/drawable/banner.png` (640×360 px), referenced via `android:banner` in the manifest.

## Testing conventions

- Unit tests use `MainDispatcherRule` (in `app/src/test/.../util/`) to swap `Dispatchers.Main` with `UnconfinedTestDispatcher`.
- Test data builders `testChannel()` / `testSource()` / `testProgramme()` live in `TestFixtures.kt`; `FakeTimeProvider` in the same file gives deterministic clock control. Use these rather than constructing models inline.
- MockK for mocking. Assertions on `StateFlow` typically read `.value` after `runCurrent()` / `advanceUntilIdle()` — Turbine is available but rarely needed.
- Avoid `mockk<HttpClient>()` — it tends to OOM the test JVM under load. Use `defaultHttpClient(MockEngine { … })` instead (see `SourceSyncServiceTest`).
- Compose UI tests (`androidTest/`) use `createComposeRule()` with MockK for ViewModel fakes.
- Maestro flows live in `.maestro/` and cover the four main user flows.

## Static analysis

- `detekt` runs in CI; config at `config/detekt/detekt.yml`, baseline at `config/detekt/baseline.xml` (grandfathered pre-existing violations so new issues fail from day one). Regenerate the baseline with `./gradlew :app:detektBaseline` after fixing issues.
- Android Lint runs in CI as `:app:lintDebug`.
