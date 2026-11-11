# Contributing

Pantry is maintained by one person; issues and pull requests are welcome. This page is for people working
on the code. The rules a change is held to live in [`CLAUDE.md`](CLAUDE.md) (Decisions, Conventions);
[`CODING_STANDARDS.md`](CODING_STANDARDS.md) says which apply to a diff.

## Setup

Open the folder in Android Studio and let Gradle sync. JDK 17. The project tracks the latest Android
Gradle Plugin; upgrade it with Tools → AGP Upgrade Assistant.

Turn on the repo's git hooks once per clone; the pre-commit hook refuses commits on `main`:

```
git config core.hooksPath .githooks
```

## Tests

| Command | What it runs |
|---|---|
| `./gradlew :domain:test` | The business rules: pure Kotlin, fast. Run it often. |
| `./gradlew build` | Everything: unit tests, screenshot and accessibility tests, lint. Lint fails on a warning. |
| `./gradlew :app:recordRoborazziDebug` | Re-records the reference screenshots in `app/src/test/screenshots/`. Look at every image it changes before committing. |
| `./gradlew koverHtmlReportCoverage` | Test coverage of `:domain` and `:app`, in `build/reports/kover/htmlCoverage/`. |

Every screen's `…Content` composable is drawn under Robolectric in light and dark (`shoot` / `shootScreen` in
`app/src/test/java/io/cosgriff/pantry/ui/screens/Screenshots.kt`), checked with Google's Accessibility Test
Framework (a warning fails), and compared pixel for pixel with its reference. On a mismatch, CI uploads the
new image and the difference as the `screenshot-differences` artifact.

## Changing a dependency

A library must carry no trackers; CI scans the APK with Exodus Privacy and fails on one, or on a
permission that isn't in `.github/privacy/permissions.txt`. Check the dependency tree of any Google or
Firebase library before adding it.

## Wording and translations

All text the app shows is in `app/src/main/res/values/strings.xml`. A new language is a
`res/values-xx/strings.xml`.

## Pull requests

- Work on a branch named `<type>/<topic>` (`feat/scan-sheet`, `fix/expiry-sort`), never on `main`.
- Pull requests are squash-merged, so the title becomes the commit on `main`. It must be a
  [Conventional Commit](https://www.conventionalcommits.org/) (`fix:`, `feat:`, `docs:`, …);
  `release-please` builds the version and changelog from them, and `pr-title.yml` checks it.
- `./gradlew build` must be green.
- A change people would notice updates `README.md` in the same pull request.
- Test data is made up: no real personal data in the repo.
- Security problems go through a private report, not an issue: see [`SECURITY.md`](SECURITY.md).
