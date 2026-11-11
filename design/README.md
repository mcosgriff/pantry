# Design

A local copy of the Pantry design canvas, so screens are built from the exact mockups.

- Canvas: https://claude.ai/artifact/5kaAAyobGePsdG3TFfPeYh
- Design system (tokens, rules, Compose mapping): https://claude.ai/artifact/Xu1pTgesVxAXhGgB4w8P7e

The canvas is the source of truth. A change made there reaches the repo only when these files are copied
again; change a mockup on the canvas, not here.

## Boards

The canvas groups boards into pages; this table follows them.

| Page | Screen | Source | Issue |
| --- | --- | --- | --- |
| Core screens | Pantry list | `source/Main.dc.html` | #6 |
| Core screens | Scan | `source/Scan.dc.html` | #10 |
| Core screens | Item details | `source/ItemEdit.dc.html` | #7 |
| Core screens | Pantry settings | `source/Settings.dc.html` | #8 |
| Scan flow | Camera permission | `source/ScanPermission.dc.html` | #10 |
| Scan flow | Product not found | `source/ScanNotFound.dc.html` | #12 |
| Scan flow | Store-packed label | `source/ScanWeighed.dc.html` | #13 |
| Scan flow | Review haul | `source/ScanReview.dc.html` | #14 |
| Empty & edge states | Empty pantry | `source/PantryEmpty.dc.html` | #15 |
| Empty & edge states | Use soon | `source/Expiring.dc.html` | #16 |
| Empty & edge states | Search: no results | `source/SearchEmpty.dc.html` | #17 |
| Empty & edge states | Sort and filter | `source/SortFilter.dc.html` | #17 |
| Empty & edge states | Remove item | `source/RemoveConfirm.dc.html` | #18 |
| Meal prep | Meal prep week | `source/MealPrep.dc.html` | #19 |
| Meal prep | Meal details | `source/MealDetail.dc.html` | #19 |
| Shopping | Shopping list | `source/Shopping.dc.html` | #20 |
| Shopping | Shopping trip | `source/ShoppingTrip.dc.html` | #20 |
| Open Food Facts (exploration) | Share prompt | `source/OffShare.dc.html` | #9 |
| Open Food Facts (exploration) | Product details and photos | `source/OffDetails.dc.html` | #9 |
| Open Food Facts (exploration) | Product lookup settings | `source/OffSettings.dc.html` | #9 |

The Open Food Facts boards show look and feel only; bracketed text on them waits on the research in #9.

Each board is an HTML page drawn at 390×844 with the design system's variables from
`source/ds/pantry/tokens.css`, the same values as `app/src/main/java/io/cosgriff/pantry/ui/theme/`. Each board
has a light and dark theme (its `theme` prop). Sample items on the boards are made up.

## Reading a board

- Colors, radii and sizes are `var(--token)`; use the matching `PantryTheme` / `MaterialTheme` value, never
  the hex.
- Text in the markup is the intended wording; it goes into `res/values/strings.xml`.
- Touch targets on the boards are 44px; the app uses 48dp (`PantryDimens.tapMin`).
