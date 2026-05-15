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

**Data flow**: Room DAOs + DataStore → `StateFlow` in ViewModels → Compose UI. There is no generic repository layer — DAOs are injected directly. The few repository-named classes (`SearchHistoryRepository`, `UserPreferencesRepository`) wrap DataStore-backed prefs, not Room.

**DI**: Koin (`di/AppModule.kt`) — single module wiring `AppDatabase`, DAOs, `SourceSyncService`, `StreamConcurrencyTracker`, `SearchHistoryRepository`, `UserPreferencesRepository` (over a `DataStore<Preferences>` at `user_prefs`), and three ViewModels. `PlayerViewModel` takes a `channelId: Long` as a parametrized injection (`params.get()`).

**Navigation**: `ui/navigation/AppNavigation.kt` uses `sealed class Screen` with string routes. Three screens: `Home` → `Player/{channelId}` and `Settings`. `PlayerScreen` receives `channelId` from nav args and loads the channel itself. Screens live in `ui/screens/{home,player,settings}/`.

**Source sync**: `SourceSyncService` fetches channels on app start (triggered from `HomeViewModel.init`). Two source types — `M3U` (fetched via Ktor, parsed by `M3uParser`) and `XTREAM` (Xtream Codes API via `XtreamApi`). On each sync, user data (favorites, `lastWatchedAt`) is preserved by matching on channel URL before deleting and reinserting. Custom user-added channels (sourceId = null path) are managed via `AddEditChannelDialog` on Home.

**Casting**: Google Cast via `play-services-cast-framework` using the default media receiver (`CC1AD845` — no Play Store registration required). `CastOptionsProvider` in `cast/` is wired via manifest `meta-data`. The player uses a `MediaRouteButton` (wrapped in `AndroidView`) for the Cast button. `MainActivity` extends `AppCompatActivity` (not `ComponentActivity`) so `MediaRouteButton` can show its `FragmentDialog`.

**Player UI**: `PlayerView` runs with `useController = false`. Controls (aspect ratio button, Cast button) are a Compose overlay that appears on tap and auto-hides after 4 s. System bars are hidden on player entry and restored on back. Channel name overlay is tied to the same show/hide state as the controls.

**Database**: Room, DB name `goortv.db`. `Channel` has a cascade-delete FK to `Source`. Current schema version is **7**; migrations `MIGRATION_1_2` through `MIGRATION_6_7` are defined in `AppDatabase` (added: favorites/lastWatchedAt, group index, `includedGroups`, `lastSyncedAt`, per-source `headers`, per-source `maxConcurrentStreams`).

**Assets**: App launcher icon lives in `res/mipmap-*/ic_launcher.png` (mdpi → xxxhdpi). Android TV home screen banner is `res/drawable/banner.png` (640×360 px), referenced via `android:banner` in the manifest.

## Testing conventions

- Unit tests use `MainDispatcherRule` (in `app/src/test/.../util/`) to swap `Dispatchers.Main` with `UnconfinedTestDispatcher`.
- Test data builders `testChannel()` / `testSource()` live in `TestFixtures.kt` — use these rather than constructing models inline.
- MockK for mocking, Turbine for `Flow` assertions.
- Compose UI tests (`androidTest/`) use `createComposeRule()` with MockK for ViewModel fakes.
- Maestro flows live in `.maestro/` and cover the four main user flows.
