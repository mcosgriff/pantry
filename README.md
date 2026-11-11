# Pantry

[![CI](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/mcosgriff/pantry?sort=semver)](https://github.com/mcosgriff/pantry/releases/latest)
[![Licence: GPL-3.0](https://img.shields.io/github/license/mcosgriff/pantry)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#install)

Offline-first pantry tracker for Android. Scan groceries after a shopping run, keep track of what's in the
dry pantry, fridge and freezer, set quantities and expiration dates, and plan meals around what expires
first. Everything stays on the phone.

## Status

Early development. The Pantry list is the first screen: items in the dry pantry, fridge and freezer,
soonest expiry first (or by name, newest or lowest stock), with search across names, notes and barcodes,
filters (opened, low stock, no date, added this week), a tab per location and a banner for what expires this week. Its View opens Use soon: what has expired, what expires this
week and next, each with one tap to throw it out, mark it used up, or freeze it (moved to the Freezer with
a new date), and Undo. Items are
scanned by barcode (UPC-A and EAN-13, read on the phone with zxing-cpp; batch scan collects a whole haul to review, name and add in one go,
and a barcode you have named once fills in its name, location and unit on the next scan; store-packed meat and deli labels show their price and are named and weighed instead) or added by hand, and edited on the item details screen: location, quantity and unit, expiry date (suggested
from the location's shelf life, with quick picks), opened, purchase date, a low-stock alert and notes. A barcode
the pantry doesn't know can be looked up in Open Food Facts, if you say yes, to fill in its name. Pantry
settings add storage locations of your own (with a default shelf life), set how many days before expiry an
item counts as "use soon", and where new items start. The Shopping tab lists what is running low (at or under its
low-stock alert), what this week's meals still need, what was used up, and what you add by hand, each name
once; a shopping trip checks things off and ends by opening the scanner to restock. The Meal prep tab plans the week's meals
from what's in the pantry, use-soon first: pick the ingredients from the pantry (or type in ones it doesn't
track), and each meal shows what it has, what has expired and what is missing. "Add missing to shopping list"
and "Cooked it", which takes the amounts out of the pantry, finish the loop; a cooked meal is kept to plan
again in one tap. A new install shows an empty pantry. Every
screen is screenshot-tested in light and dark and checked for accessibility (touch targets, contrast,
labels) on each build. The plan, in order:

| Step | Issue |
| --- | --- |
| Pantry rules: items, locations, quantities, expiry | [#3](https://github.com/mcosgriff/pantry/issues/3) |
| Saving the pantry and settings on the phone | [#4](https://github.com/mcosgriff/pantry/issues/4) |
| Screenshot and accessibility tests | [#5](https://github.com/mcosgriff/pantry/issues/5) |
| Pantry list | [#6](https://github.com/mcosgriff/pantry/issues/6) |
| Add, edit and remove items | [#7](https://github.com/mcosgriff/pantry/issues/7) |
| Pantry settings | [#8](https://github.com/mcosgriff/pantry/issues/8) |
| Scanning barcodes | [#10](https://github.com/mcosgriff/pantry/issues/10) |
| Naming unknown products, store-packed labels, reviewing a haul | [#12](https://github.com/mcosgriff/pantry/issues/12), [#13](https://github.com/mcosgriff/pantry/issues/13), [#14](https://github.com/mcosgriff/pantry/issues/14) |
| First run, Use soon, search, sort and filter | [#15](https://github.com/mcosgriff/pantry/issues/15), [#16](https://github.com/mcosgriff/pantry/issues/16), [#17](https://github.com/mcosgriff/pantry/issues/17) |
| Removing items with a reason (to be decided) | [#18](https://github.com/mcosgriff/pantry/issues/18) |
| Meal prep | [#19](https://github.com/mcosgriff/pantry/issues/19) |
| Shopping list and trips | [#20](https://github.com/mcosgriff/pantry/issues/20) |
| Product lookup by barcode, and adding to Open Food Facts (to be decided) | [#9](https://github.com/mcosgriff/pantry/issues/9) |

The screens are designed already: see [`design/`](design/README.md).

## Privacy

No account, no server, no trackers, no ads. It asks for one permission, the camera, only when you open
Scan; it reads barcodes from the picture on the phone and saves no pictures.

The app makes one kind of network call, and only after you say yes: looking up a scanned barcode the pantry
doesn't know in [Open Food Facts](https://world.openfoodfacts.org), a free database of food products run by
volunteers ([#9](https://github.com/mcosgriff/pantry/issues/9), [`docs/adr/0001`](docs/adr/0001-open-food-facts-lookup.md)).
The first time a barcode isn't known, the scan sheet asks; Pantry settings turn it on or off. It sends only
the barcode (and, as any request does, your IP address and the app's name and version), never store-packed
meat and deli labels, and keeps only the product's name, brand and size. That needs Android's internet
permission, which Android grants without asking.

## Install

Pantry isn't on the Play Store. Add `https://github.com/mcosgriff/pantry` in
[Obtainium](https://github.com/ImranR98/Obtainium), which installs and updates it from GitHub Releases, or
download the APK from the [latest release](https://github.com/mcosgriff/pantry/releases/latest).
Android 10 or later.

## Verify your download

Every release APK is signed with the same key. Its SHA-256 certificate fingerprint is:

```
10:6B:57:A2:C4:D6:DC:3F:EC:F9:C3:78:E8:2D:9C:16:05:CB:C1:9A:B9:0C:D2:52:A4:29:3A:E6:C6:37:7F:BA
```

Check an APK before installing it with `apksigner verify --print-certs pantry-vX.Y.Z.apk` (the "SHA-256
digest" line, without colons), or the installed app with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
Android also refuses an update signed with a different key.

Each release also carries:

- `pantry-vX.Y.Z.apk.sha256`, the APK's SHA-256 checksum: `sha256sum -c pantry-vX.Y.Z.apk.sha256`.
- A build-provenance attestation, which shows the APK was built by this repo's release workflow from its
  tag:
  ```
  gh attestation verify pantry-vX.Y.Z.apk --repo mcosgriff/pantry
  ```
  The same signed provenance is attached as `pantry-vX.Y.Z.apk.intoto.jsonl`, which `gh attestation verify`
  takes with `--bundle`.
- `pantry-vX.Y.Z.exodus.json`, the [Exodus Privacy](https://reports.exodus-privacy.eu.org/) scan of that APK:
  no trackers, and only the permissions listed in
  [`.github/privacy/permissions.txt`](.github/privacy/permissions.txt).

## Building

| Module | What lives there |
|---|---|
| `:domain` | Pure Kotlin: items, locations, quantities and units, expiry, shopping and meal rules. Unit-tested, no Android. |
| `:data` | The Room database (schemas in `data/schemas`), DataStore settings, the Hilt module. |
| `:app` | Jetpack Compose UI, Navigation 3, theme, barcode scanning (zxing-cpp). |

1. Open this folder in Android Studio and let Gradle sync. JDK 17.
2. Turn on the repo's git hooks once per clone (the pre-commit hook refuses commits on `main`):
   `git config core.hooksPath .githooks`
3. Run on a device or emulator with the `app` run configuration, or `./gradlew :app:installDebug` (debug app
   id `io.cosgriff.pantry.debug`, which installs beside a release build).
4. `./gradlew :domain:test` runs the business rules' tests in seconds; `./gradlew build` runs everything:
   unit tests, screenshot and accessibility tests, and lint. Lint fails the build on a warning.
5. Upgrade the Android Gradle Plugin with Tools → AGP Upgrade Assistant.

Commit messages and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/). More on
tests, screenshots and pull requests is in [`CONTRIBUTING.md`](CONTRIBUTING.md). The rules a change is held
to are in [`CLAUDE.md`](CLAUDE.md); the reasons behind the big decisions are in [`docs/adr/`](docs/adr/).

## Releasing (maintainers)

1. Create a release keystore once and **back it up** somewhere other than GitHub. Every update must be
   signed with the same key, or Android and Obtainium refuse it:
   ```
   keytool -genkeypair -v -keystore pantry-release.jks -storetype PKCS12 -alias pantry -keyalg RSA -keysize 4096 -validity 36500
   ```
2. Add the signing secrets to the `release` environment (Settings → Environments → release → Environment
   secrets), not as repository secrets. The environment needs a maintainer's approval and accepts only `v*`
   tags.
   - `KEYSTORE_B64`: `base64 -w0 pantry-release.jks | gh secret set KEYSTORE_B64 --env release`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS` (the `-alias` above), `KEY_PASSWORD` (the same as the keystore's, for
     PKCS12)
3. Release: merge the open **release PR** (`chore(main): release X.Y.Z`). The `release-please` workflow keeps
   it up to date from the Conventional Commits on `main`: `fix:` bumps the patch version, `feat:` the minor,
   a breaking change the major. It updates `CHANGELOG.md` and `version.txt`. Merging tags `vX.Y.Z`, creates
   the GitHub Release and starts the signed build once CI has passed on that commit; approve the waiting
   `release` run in the Actions tab. The build attaches the APK, its checksum, the provenance and the Exodus
   report to the release.
4. Check the release APK's signing certificate against the fingerprint in
   [Verify your download](#verify-your-download): `apksigner verify --print-certs pantry-vX.Y.Z.apk`.

`applicationId` (`io.cosgriff.pantry`) and the signing key must never change.

## How Pantry is made

Pantry is written with an AI coding assistant, and this says how, so nobody has to guess.

- **I decide what it does and how it works.** What the app should do, what it must never do (track you,
  send anything but a barcode you agreed to look up, use Google's ML Kit), and the architecture that keeps
  those promises are my calls. They're written down, with their reasons, in [`CLAUDE.md`](CLAUDE.md) and
  [`docs/adr/`](docs/adr/).
- **Claude writes most of the code.** It works from those decisions. Every commit it had a hand in says so
  (`Co-Authored-By: Claude`), so the history shows which work is which.
- **Nothing reaches `main` without me.** Every change is a pull request that I review and merge myself.
- **It's tested.** The business rules live in a pure Kotlin module with unit tests. Every screen is
  screenshot-tested in light and dark and checked for accessibility, and CI runs those, lint and an Exodus
  Privacy scan of the APK on every pull request.

If you find something that looks careless, open an issue: it's mine to fix, whoever typed it.

## Licence

GPL-3.0 ([`LICENSE`](LICENSE)). The bundled Inter and Space Grotesk fonts are under the SIL Open Font License
(`app/src/main/assets/licences/`).
