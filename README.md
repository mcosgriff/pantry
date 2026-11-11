# Pantry

[![CI](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml)
[![Licence: GPL-3.0](https://img.shields.io/github/license/mcosgriff/pantry)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#install)

Offline-first pantry tracker for Android. Scan groceries after a shopping run, keep track of what's in the
dry pantry, fridge and freezer, set quantities and expiration dates, and plan meals around what expires
first. Everything stays on the phone.

## Status

Early development. The app builds and has its theme (light and dark); no screens are built yet. The plan,
in order:

| Step | Issue |
| --- | --- |
| Pantry rules: items, locations, quantities, expiry | [#3](https://github.com/mcosgriff/pantry/issues/3) |
| Saving the pantry and settings on the phone | [#4](https://github.com/mcosgriff/pantry/issues/4) |
| Screenshot and accessibility tests | [#5](https://github.com/mcosgriff/pantry/issues/5) |
| Pantry list | [#6](https://github.com/mcosgriff/pantry/issues/6) |
| Add, edit and remove items | [#7](https://github.com/mcosgriff/pantry/issues/7) |
| Pantry settings | [#8](https://github.com/mcosgriff/pantry/issues/8) |
| Scanning barcodes | [#10](https://github.com/mcosgriff/pantry/issues/10) |
| Product lookup by barcode (to be decided) | [#9](https://github.com/mcosgriff/pantry/issues/9) |

The screens are designed already: see [`design/`](design/README.md).

## Privacy

No account, no server, no trackers, no ads. The app makes no network calls today. Product lookup by barcode
would be the first, and is waiting on a decision about what it sends ([#9](https://github.com/mcosgriff/pantry/issues/9)).

## Install

Pantry isn't on the Play Store. Add `https://github.com/mcosgriff/pantry` in
[Obtainium](https://github.com/ImranR98/Obtainium), which installs and updates it from GitHub Releases, or
download the APK from the [latest release](https://github.com/mcosgriff/pantry/releases/latest).
Android 10 or later.

Each release APK has a SHA-256 checksum and a build-provenance attestation:

```
gh attestation verify pantry-vX.Y.Z.apk --repo mcosgriff/pantry
```

## Building

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

GPL-3.0. The bundled Inter and Space Grotesk fonts are under the SIL Open Font License
(`app/src/main/assets/licences/`).
