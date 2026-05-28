# Contributing to GoorTV

Thanks for your interest in improving GoorTV! Contributions of all kinds are
welcome — bug reports, fixes, features, docs, and tests.

By participating you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Getting started

GoorTV is a single-module Android app (Kotlin + Jetpack Compose) targeting
Android TV (minSdk 26). You'll need:

- A recent **Android Studio** (or the command-line Android SDK).
- **JDK 17+**.
- Optionally [Task](https://taskfile.dev) for the shortcut commands below — every
  one maps to a plain `./gradlew` invocation if you'd rather not install it.

```bash
task build          # assembleDebug
task test:unit      # JVM unit tests  (./gradlew :app:testDebugUnitTest)
task test:ui        # instrumented Compose tests — needs a device/emulator
task lint           # Android Lint
```

The full command list lives in `Taskfile.yml`, and architecture notes for the
codebase are in `CLAUDE.md`.

## Before you open a pull request

Please make sure the following pass locally:

```bash
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # Android Lint
./gradlew :app:detekt               # static analysis
```

- **detekt**: new violations fail the build. If you fix existing baselined
  issues, regenerate the baseline with `./gradlew :app:detektBaseline`. Don't add
  new entries to the baseline to dodge a fix.
- **Database changes**: bump the schema version, add a `Migration`, and commit the
  generated `app/schemas/**.json`. The migration test in `app/src/androidTest/`
  walks the full chain and will fail on drift.
- **UI / TV behaviour**: type checks and tests verify correctness, not feel.
  If you change a screen, verify it on a device or emulator with a D-pad and say
  so in the PR.
- Add or update tests for the behaviour you change.

## Commit & PR conventions

- Use short, imperative, prefixed commit subjects: `fix:`, `feat:`, `docs:`,
  `chore:`, `refactor:`, `test:`.
- Keep a PR focused on one concern. Separate schema migrations and
  emulator-dependent UI work from pure logic changes where practical.
- Fill in the PR template, including a test plan. Note anything that needs
  on-device verification.
- CI (build, unit tests, lint, detekt, instrumented tests, CodeQL) must be green.

## Reporting bugs & requesting features

Use the [issue templates](https://github.com/DerkSchooltink/GoorTV/issues/new/choose).
Include your device/Android version and reproduction steps. For **security**
issues, do **not** open a public issue — follow [SECURITY.md](SECURITY.md).

## Legal

GoorTV is a player only; it ships no channels or content. Please don't add
sample sources, pre-loaded playlists, or anything that facilitates accessing
content without authorization. Contributions are accepted under the project's
[MIT License](LICENSE).
