# Pantry — notes for Claude

Offline-first Android app for tracking what's in the dry pantry, fridge and freezer. Scan groceries after a
shopping run, set quantities and expiration dates, and plan meals around what expires first. Single user, no
account, no server. Distributed by sideloading via Obtainium from GitHub Releases, not the Play Store.
Public repo, GPL-3.0 (`LICENSE`).

Set up from the Android App Playbook (https://claude.ai/artifact/QZmyJUpsjv8rC1z1pzhM7s): toolchain, CI,
releases and issues follow it. When this repo changes a shared practice, update the playbook's section and
add a row to its Revision log.

- Design canvas (mockups): https://claude.ai/artifact/5kaAAyobGePsdG3TFfPeYh
- Design system (tokens, README, Compose mapping): https://claude.ai/artifact/Xu1pTgesVxAXhGgB4w8P7e

**Local copy of the design: `design/`** (`design/source/<Board>.dc.html`, exact colors, spacing and copy).
Start from `design/README.md`. Before building a screen, read its board.

This file holds the rules and the decisions behind them. How a feature works is in its code and tests; the
issue numbers lead to the discussion.

## Status

Scaffold only: the theme and a placeholder home screen. `./gradlew build` is green (Gradle 9.9.1, AGP 9.5.1,
lint included). Keep it green; lint fails the build on a warning as on an error (`warningsAsErrors`), and
there is no baseline: fix a warning, or suppress that one case in `app/lint.xml` with a comment saying why.

## Commands

- `./gradlew :domain:test` — fast pure-Kotlin tests. Run often.
- `./gradlew build` — everything: unit tests, screenshot and accessibility tests, and lint.
- `./gradlew :app:recordRoborazziDebug` — re-record the reference screenshots (`app/src/test/screenshots/`)
  after a change meant to alter a screen; look at every image it changes.
- `./gradlew koverHtmlReportCoverage` — coverage of `:domain` and `:app`; CI sends the XML to Codecov.
- `./gradlew :app:installDebug` — install on a connected phone (debug app id `io.cosgriff.pantry.debug`).
- Release: merge the PR `release-please` keeps open (version and changelog come from Conventional Commits);
  it tags `vX.Y.Z` and starts `release.yml`, which builds the signed APK after approval in the `release`
  environment.

## Modules

- `:domain` — pure Kotlin, **no Android imports**. All business rules (expiry, quantities, units) live here
  and get unit tests.
- `:data` — Room 3 database (`PantryDatabase`, schemas exported to `data/schemas`, committed) and DataStore
  settings, behind `PantryRepository` and `SettingsRepository`; Hilt's `DataModule`. Tests run on the JVM
  with Robolectric, whose Android jar Gradle resolves (root `build.gradle.kts`). A schema change needs a new
  version, its committed JSON and a migration; `MigrationTest` opens every exported version (#4).
- `:app` — Compose UI, ViewModels, theme. Hilt (`PantryApp`).

Add `:baselineprofile` once the screens settle.

## Decisions — don't change without asking

- **applicationId `io.cosgriff.pantry` must never change. The release signing key must never change** once
  the first release is out.
- **No trackers, analytics, crash reporting or ads.** CI and `release.yml` scan the release APK with Exodus
  Privacy (`.github/scripts/exodus-scan.sh`): a tracker fails, and so does a permission not in
  `.github/privacy/permissions.txt` (or one listed that the APK no longer asks for). A new permission means
  updating that list and explaining it in the app.
- **Network: one call only,** product lookup by barcode in Open Food Facts (`docs/adr/0001`, #9). It sends
  only the barcode, is off until the user says yes, and never sends store-packed codes. Any other network
  call needs its own ADR first.
- **Barcodes are read with zxing-cpp, not ML Kit.** ML Kit uploads usage logs to Google. Check any Google or
  Firebase library's dependency tree before adding it.
- **No Android auto-backup** (`allowBackup=false`, `fullBackupContent=false`, data extraction rules exclude
  all) until the app has its own export.
- **Dynamic color is off.** Every color comes from the Pantry tokens (`ui/theme/Color.kt`).
- minSdk 29, targetSdk 37, compileSdk 37, JDK 17, Kotlin + Compose + Material 3.
- **IDE is Android Studio.** Keep AGP current (Studio's Upgrade Assistant).

## Design tokens

Values live in the design system and in `ui/theme/`; don't type a color, radius or size into a screen.

- Colors: `MaterialTheme.colorScheme` for the Material roles, `PantryTheme.colors` for the rest (Dry,
  Fridge and Freezer pairs, warning, scanner). Every Material role is set in `Color.kt`, the `…Fixed` and
  `surfaceContainer…` ones included; one left out falls back to Material's purple.
- Location colors always come with the location's letter or name; color is never the only cue.
- Type: Inter for text, Space Grotesk for screen titles, the expiration date and location letters
  (`MaterialTheme.typography`, `PantryTheme.text`). Both are bundled in `res/font`, OFL texts in
  `assets/licences/`.
- Corners from `MaterialTheme.shapes`; spacing and sizes from `PantryDimens`. Touch targets ≥ 48dp.
- Flat by default: borders, not shadows. The only shadow is the Scan button's.

## Conventions

- **Never commit on `main`.** All work goes on a branch named `<type>/<topic>` (Conventional Commit types:
  `feat/scan-sheet`, `fix/expiry-sort`) and reaches `main` through a squash-merged pull request. If the
  checkout is on `main`, create the branch before the first edit. `.githooks/pre-commit` refuses a commit on
  `main`; turn it on once per clone with `git config core.hooksPath .githooks`.
- **Keep `README.md` current in the same PR.** A change people would notice (a new screen or feature, a
  permission, a network call, install steps, a requirement) updates the README's "What it does", "Status"
  or "Install" in that PR, not later.
- Work is tracked as GitHub issues; reference the issue number in the PR (`Closes #6`) and in code comments
  where a decision lands.
- Business logic goes in `:domain` with a test; ViewModels stay thin.
- A composable used by more than one screen lives in `ui/components/`.
- A screen is a `…Content` composable (plain state and callbacks) wrapped by a `…Screen` that reads the
  ViewModel, so it can be previewed and screenshot-tested. A `…Content` that needs today takes it as a
  parameter, never `LocalDate.now()`. Only ViewModels and the expiry digest (`notify/ExpiryDigest.kt`, #27,
  #52) read the clock, through the injected `java.time.Clock`.
- **Every screen gets a screenshot test** in `ScreensScreenshotTest` (`compose.shoot(...)`, or `shootScreen`
  for a sheet or dialog), light and dark. It also runs the Accessibility Test Framework, where a warning
  fails; suppress a deliberate finding for that one kind of element, with a comment saying why.
- Fixed-pattern rules from this file are checked by `SourceRulesTest` and `HardcodedTextTest`; a new rule
  that is a pattern in the source goes there.
- **All wording lives in `res/values/strings.xml`**, named `<screen>_<what>`. Composables use
  `stringResource` / `pluralStringResource`. Quantities go in carrying their unit.
- Never format a figure or date with `Locale.US` or `String.format` for display.
- Never show placeholder data as if real; mark sample values clearly.
- Any Python helper scripts: use `uv run` with inline script dependencies.
- Public repo: never commit real personal data, keystores, `local.properties` or `.idea/`. Test fixtures
  use made-up values.

## Agent skills

### Issue tracker

GitHub Issues on `mcosgriff/pantry`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five default triage roles. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root, created lazily. See `docs/agents/domain.md`.
