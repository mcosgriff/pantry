# Design

A local copy of the Pantry design canvas, so screens are built from the exact mockups.

- Canvas: https://claude.ai/artifact/5kaAAyobGePsdG3TFfPeYh
- Design system (tokens, rules, Compose mapping): https://claude.ai/artifact/Xu1pTgesVxAXhGgB4w8P7e

The canvas is the source of truth. A change made there reaches the repo only when these files are copied
again; change a mockup on the canvas, not here.

## Boards

| Screen | Source | Issue |
| --- | --- | --- |
| Pantry list | `source/Main.dc.html` | #6 |
| Scan | `source/Scan.dc.html` | #10 |
| Item details | `source/ItemEdit.dc.html` | #7 |
| Pantry settings | `source/Settings.dc.html` | #8 |

Each board is an HTML page drawn at 390×844 with the design system's variables from
`source/ds/pantry/tokens.css`, the same values as `app/src/main/java/io/cosgriff/pantry/ui/theme/`. Each board
has a light and dark theme (its `theme` prop). Sample items on the boards are made up.

## Reading a board

- Colors, radii and sizes are `var(--token)`; use the matching `PantryTheme` / `MaterialTheme` value, never
  the hex.
- Text in the markup is the intended wording; it goes into `res/values/strings.xml`.
- Touch targets on the boards are 44px; the app uses 48dp (`PantryDimens.tapMin`).
