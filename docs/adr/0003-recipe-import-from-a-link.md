# 3. Importing a recipe from a link

- Status: accepted, 2026-11-11 (#43)

## Context

Typing in a meal's ingredients one by one is slow, and most recipes are found in a browser. Most recipe sites
embed the recipe for search engines as schema.org `Recipe` data in JSON-LD (`<script type="application/ld+json">`):
its name, servings and one line of text per ingredient. Reading that is a second network call, which
`docs/adr/0001` says needs its own decision.

## Decision

- **Fetch only the page the user chose, once.** The link comes from Android's Share menu (Share → Pantry in the
  browser) or is pasted into Meal prep's "Import a recipe". Pantry shows the link and fetches it only when the
  user taps "Read recipe": one `GET` of that URL, following the site's own redirects. No other page, image,
  script or stylesheet is fetched, and nothing is fetched in the background or again later.
- **Only `https` links.** Anything else (an `http` link, a `file:` or `content:` URI, text with no link) is
  refused before any request, and the screen says so.
- **What is sent:** the URL, plus what every HTTP request carries: the phone's IP address and a `User-Agent` of
  `Pantry/<version> (https://github.com/mcosgriff/pantry)`, the same as for Open Food Facts. No cookie, account,
  referrer or identifier; the user's name and contacts are never sent.
- **What is read:** only the page's `Recipe` JSON-LD, also inside `@graph` or an array, and of that only its
  `name`, `recipeYield` (a number, or text such as "4 servings") and `recipeIngredient` lines. The page is read
  up to 4 MB, with a 10 s timeout, and dropped as soon as it is read. Images are never downloaded.
- **No `Recipe` data: say so, never guess.** A page without it (or one whose `Recipe` has no ingredients) shows
  "No recipe found on this page", with the link to try another. Pantry doesn't scrape the page's visible text.
- **Nothing is stored but the meal the user saves.** Ingredient lines are parsed on the phone into amount, unit
  and name (`:domain`), and the user reviews each one: its amount, unit and name, and which pantry item it is,
  or "not tracked". The page, its URL and its other contents are not kept.
- **Decisions are remembered on the phone.** When the user says a line's name is a pantry item, marks it not
  tracked, or says what an unknown unit word means ("clove" is each), Pantry keeps that rule in its settings
  store and applies it to later imports. Rules never leave the phone.
- **One recipe at a time, chosen by the user.** No bulk import: reading a site's recipes in bulk would break its
  terms.
- **No HTTP or HTML library:** the same `HttpURLConnection` path as Open Food Facts, and the platform's `org.json`
  for the JSON-LD, so nothing new to check for trackers.

## Consequences

- No new permission: `android.permission.INTERNET` is already asked for (`docs/adr/0001`). The README says when
  this second call happens and what it sends.
- The site sees the phone's IP address and that Pantry read that one page, as it would see a browser that opened
  it.
- Sites without JSON-LD can't be imported; the user types the meal in instead.
- Ingredient amounts are stored the way meals store them today. Until meals store the amount model of
  `docs/adr/0002`, a line whose unit a meal can't hold keeps its amount in its name ("2 cups flour"), and a
  pantry match keeps the amount only when it converts exactly into the item's unit.
