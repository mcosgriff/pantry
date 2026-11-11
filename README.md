# Pantry

[![CI](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml)
[![Licence: GPL-3.0](https://img.shields.io/github/license/mcosgriff/pantry)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#install)

Offline-first pantry tracker for Android. Scan groceries after a shopping run, keep track of what's in the
dry pantry, fridge and freezer, set quantities and expiration dates, and plan meals around what expires
first. Everything stays on the phone.

Early development: the app is a scaffold with its theme in place; no screens are built yet.

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
