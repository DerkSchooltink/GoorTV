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

# Misc
task lint
task logcat
task clean
```

## Architecture

Single-module Android app (`app/`), Kotlin + Jetpack Compose, targeting Android TV (minSdk 26).

**Data flow**: Room DAOs → `StateFlow` in ViewModels → Compose UI (no repository layer; DAOs are injected directly).

**DI**: Koin (`di/AppModule.kt`) — single module wiring `AppDatabase`, DAOs, `SourceSyncService`, and three ViewModels. `PlayerViewModel` takes a `channelId: Long` as a parametrized injection (`params.get()`).

**Navigation**: `AppNavigation.kt` uses `sealed class Screen` with string routes. Three screens: `Home` → `Player/{channelId}` and `Settings`. `PlayerScreen` receives `channelId` from nav args and loads the channel itself.

**Source sync**: `SourceSyncService` fetches channels on app start (triggered from `HomeViewModel.init`). Two source types — `M3U` (fetched via Ktor, parsed by `M3uParser`) and `XTREAM` (Xtream Codes API via `XtreamApi`). On each sync, user data (favorites, `lastWatchedAt`) is preserved by matching on channel URL before deleting and reinserting.

**Database**: Room, DB name `goortv.db`. `Channel` has a cascade-delete FK to `Source`. Current schema version is 2 (`MIGRATION_1_2` defined in `AppDatabase`).

## Testing conventions

- Unit tests use `MainDispatcherRule` (in `app/src/test/.../util/`) to swap `Dispatchers.Main` with `UnconfinedTestDispatcher`.
- Test data builders `testChannel()` / `testSource()` live in `TestFixtures.kt` — use these rather than constructing models inline.
- MockK for mocking, Turbine for `Flow` assertions.
- Compose UI tests (`androidTest/`) use `createComposeRule()` with MockK for ViewModel fakes.
- Maestro flows live in `.maestro/` and cover the four main user flows.
