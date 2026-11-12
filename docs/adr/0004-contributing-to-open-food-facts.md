# 4. Adding new products to Open Food Facts

- Status: accepted, 2026-11-11 (#42)

## Context

Product lookup (`docs/adr/0001`, #9) reads Open Food Facts and sends only a barcode. A product it doesn't have is
named by hand on the scan sheet, so the user already holds what Open Food Facts lacks: the barcode and a name
read off the package. Offering to send them back helps the next person who scans it, in any app. That sends
more than a barcode and needs an account, so it gets its own decision. The boards are `OffShare.dc.html`,
`OffDetails.dc.html` and the Contributing part of `OffSettings.dc.html`.

Open Food Facts' write API (`/cgi/product_jqm2.pl`) needs authentication. Its docs offer an app-wide account,
but that account's password would sit in a public repository and in every sideloaded APK, where anyone could
read it and write as the app.

## Decision

- **Writes go only to Open Food Facts,** `https://world.openfoodfacts.org`, the host lookup already uses:
  `POST /cgi/session.pl` to sign in and `POST /cgi/product_jqm2.pl` to add a product. No other host.
- **Only with the user's own Open Food Facts account.** No app-wide account. The account is created on
  openfoodfacts.org, in the browser; the app only links to the page.
- **Sign-in:** the first time the user shares, the form asks for the Open Food Facts user name and password.
  They are posted once to `/cgi/session.pl` (with `jqm=1`, which answers in JSON). The password is never
  stored; the app keeps the `session` cookie from the answer and the user name it returns. A wrong password
  says so and sends nothing else. Pantry settings show the account and sign out, which forgets the session on
  the phone (Open Food Facts ends a user's older sessions itself once there are ten).
- **The session stays on the phone,** in the app's DataStore beside the other settings, sealed with AES-GCM
  under a key in the Android Keystore (platform APIs, no library: `androidx.security` and Tink were not
  needed). It is sent only to Open Food Facts, in the `Cookie` header of a write. App data is excluded from
  backup and device transfer (`allowBackup=false`, data extraction rules exclude all). A session Open Food
  Facts no longer accepts ("no user credentials", 401 or 403) is forgotten and the user signs in again.
- **The least Open Food Facts takes for a new product:** `code` and `product_name`. `add_brands` and
  `quantity` go only when the user typed them on the form, from the package in hand (`add_brands`, as Open
  Food Facts asks apps, so a brand is added rather than replacing one). Also `app_name=Pantry` and
  `app_version`, which Open Food Facts asks every app to send, and the lookup's `User-Agent`.
- **Nothing personal, nothing guessed:** not the pantry, quantities, dates, location, store, prices, language,
  country (`cc`/`lc`) or anything about the phone. No `app_uuid` and no `comment`: the user's own account
  already says who added it. Categories are left out: they are a taxonomy the user would have to guess at.
  Open Food Facts records the user name as the contributor and sees the phone's IP address, as with any
  request; the prompt says the user name is shared.
- **No photos in this change.** The boards' photo row (front, ingredients, nutrition) is left out: taking,
  storing and uploading pictures is a camera flow and an upload API of its own, and a photo can show more
  than the package. A later change can add it with its own decision.
- **Licences are shown in the prompt:** shared data is public; the database is under the Open Database
  Licence (ODbL), each product's data under the Database Contents Licence (DbCL), and photos (not sent) would
  be CC BY-SA.
- **Ask me / Always / Never,** as on `OffSettings`: "Ask me" at install shows the prompt; "Don't ask again,
  always share" on it, or Always in settings, goes straight to the form; Never stops the offer. Always never
  sends without the form: it skips the question, not the user's tap on Share.
- **Off until the user says yes.** It is offered only after lookup is on and found nothing, only for a
  product the user named on the scan sheet (one item, not batch scan for now), and nothing is sent until the
  user is signed in and taps "Share product".
- **Never a store-packed code** (UPC-A starting with 2, EAN-13 20–29): they change with every package, as in
  `docs/adr/0001`. The barcode is checked again before anything is sent.
- **Looked up again just before sending,** with the existing lookup: a product someone added meanwhile is
  left alone (`product_name` and `quantity` would replace theirs), and if Open Food Facts can't be reached,
  nothing is sent.
- **No HTTP library,** as for lookup: `HttpURLConnection`, no cookie jar (the one cookie is passed by hand), no
  redirects followed.
- **Tests never reach Open Food Facts.** The writer is tested with a fake `HttpPost`, the HTTP code against a
  JDK server on the loopback address. Open Food Facts' staging server (`world.openfoodfacts.net`) has its own
  accounts; nothing in the app points at it, and trying it means changing `baseUrl` by hand in a debug build.

## Consequences

- No new permission: `android.permission.INTERNET` is already asked for lookup
  (`.github/privacy/permissions.txt`). No new dependency, so nothing new to check for trackers.
- Open Food Facts learns, under the user's own account, the barcodes and names the user chooses to share, and
  the IP address they come from. Nothing else leaves the phone.
- A name is stored as `product_name` without a language: Pantry doesn't know the package's language, so Open
  Food Facts files it under its default and its volunteers can correct it.
- Open Food Facts' docs say sessions are tied to an IP address and that username/password sign-in will move to
  OpenID Connect (Keycloak). A phone that changes network may need to sign in again; a move to Keycloak needs
  a new decision.
- Any other network call still needs its own ADR.
