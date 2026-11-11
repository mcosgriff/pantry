# 1. Product lookup by barcode through Open Food Facts

- Status: accepted, 2026-11-11 (#9)

## Context

Pantry made no network calls. Naming every scanned product by hand is the slowest part of putting a shopping
run away, and the Scan board shows "Found in Open Food Facts". Open Food Facts is a free, open database of food
products run by volunteers. Its read API needs no account; writes need one.

## Decision

- **Look up a scanned barcode in Open Food Facts**, the app's first and only network call:
  `GET https://world.openfoodfacts.org/api/v2/product/<barcode>.json?fields=product_name,brands,quantity`.
- **Only the barcode is sent,** plus what every HTTP request carries: the phone's IP address and a
  `User-Agent` of `Pantry/<version> (https://github.com/mcosgriff/pantry)`, as Open Food Facts asks of apps.
  There is no account, cookie or identifier.
- **Ask first.** Lookup is off at install. The first time a scanned barcode isn't known, the sheet asks whether
  to look it up, saying only the barcode is sent; the answer is kept, and Pantry settings can change it.
- **Only a barcode the pantry doesn't know is looked up.** A barcode named before is filled in from the phone.
  Store-packed codes (UPC-A starting with 2, EAN-13 20–29) are never sent: they change with every package.
- **Text only:** product name, brand and size. No photos are downloaded; the photo placeholder stays.
- **Nothing is stored from a lookup by itself.** What the user saves is remembered as a known product, as for
  a product named by hand (#12).
- **At most 15 lookups a minute,** Open Food Facts' limit per IP; past it, the sheet asks for a name as if
  offline. A failed or slow lookup (5 s) never blocks naming by hand.
- **No contributing yet.** Adding products to Open Food Facts needs the user's own account (an app-wide
  account's password can't be kept secret in a public repo or a sideloaded APK), so it gets its own issue.
  Shared data would be ODbL and photos CC BY-SA.
- **No HTTP library:** `HttpURLConnection` and the platform's `org.json`, so the network path adds no
  dependency to check for trackers.

## Consequences

- The app asks for `android.permission.INTERNET` (listed in `.github/privacy/permissions.txt`). Android grants
  it at install without a prompt, so the app explains the lookup where it is offered and in the README.
- Open Food Facts sees which barcodes are scanned from the phone's IP address, only while lookup is on.
- Any other network call needs its own decision (an ADR), as this one did.
