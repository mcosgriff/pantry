# Pantry

[![CI](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/pantry/actions/workflows/ci.yml)
[![Licence: GPL-3.0](https://img.shields.io/github/license/mcosgriff/pantry)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#install)

Offline-first pantry tracker for Android. Scan groceries after a shopping run, keep track of what's in the
dry pantry, fridge and freezer, set quantities and expiration dates, and plan meals around what expires
first. Everything stays on the phone.

## Status

Early development. The Pantry list is the first screen: items in the dry pantry, fridge and freezer,
soonest expiry first, with search, a tab per location and a banner for what expires this week. Its View opens Use soon: what has expired, what expires this
week and next, each with one tap to throw it out, mark it used up, or freeze it (moved to the Freezer with
a new date), and Undo. Items are
added by hand and edited on the item details screen: location, quantity and unit, expiry date (suggested
from the location's shelf life, with quick picks), opened, purchase date, a low-stock alert and notes. Pantry
settings add storage locations of your own (with a default shelf life), set how many days before expiry an
item counts as "use soon", and where new items start. A new install shows an empty pantry. The plan,
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
| Naming unknown products, store-packed labels, reviewing a haul | [#12](https://github.com/mcosgriff/pantry/issues/12), [#13](https://github.com/mcosgriff/pantry/issues/13), [#14](https://github.com/mcosgriff/pantry/issues/14) |
| First run, Use soon, search, sort and filter | [#15](https://github.com/mcosgriff/pantry/issues/15), [#16](https://github.com/mcosgriff/pantry/issues/16), [#17](https://github.com/mcosgriff/pantry/issues/17) |
| Removing items with a reason (to be decided) | [#18](https://github.com/mcosgriff/pantry/issues/18) |
| Meal prep (to be decided) | [#19](https://github.com/mcosgriff/pantry/issues/19) |
| Shopping list and trips | [#20](https://github.com/mcosgriff/pantry/issues/20) |
| Product lookup by barcode, and adding to Open Food Facts (to be decided) | [#9](https://github.com/mcosgriff/pantry/issues/9) |

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
