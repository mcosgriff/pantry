# Glossary

Words the code, issues and screens use for one thing each. Decisions behind them are in `docs/adr/`.

- **Item:** one thing in the pantry, in a location, with an amount and maybe an expiry date.
- **Location:** where an item is kept: the built-in Dry, Fridge and Freezer, or one the user adds.
- **Location key:** a location's identity outside its row: `DRY`, `FRIDGE`, `FREEZER`, or `custom:<id>` for one
  the user added. Settings store it; the database keeps the same names for the built-in ones (#51).
- **Deleted location:** a location the user removed (only an empty one can be). What refers to it falls back,
  per use: a default location to "Last used", a new item to the last used location then Dry, a known product
  to no location, the pantry list's tab to All. `DeletedLocation` in :domain holds the whole policy (#51).
- **Meal:** a recipe the household cooks: a name, servings and ingredients. Kept after it's cooked.
- **Ingredient:** one line of a meal, from the pantry (it matches an item) or typed in (not tracked).
- **Meal plan:** a meal on a day, for a number of servings, which scales the ingredients.
- **Consumption:** what using up, throwing out, freezing or cooking does to the pantry and the shopping list
  (#48).
- **Amount:** how much of an item or ingredient: a measure or a packaged amount (`docs/adr/0002`).
- **Measure:** a number and a unit: `12 each`, `1.24 lb`, `2 cup`.
- **Unit:** each, dozen; tsp, tbsp, fl oz, cup, pint, quart, gallon, ml, l; oz, lb, g, kg. "Ounce" is always
  weight.
- **Dimension:** what a unit measures: count, volume or mass. Units convert exactly within a dimension.
- **Measurement system:** US customary or metric. A setting picks which one to show.
- **Package:** bag, pack, tub, box, can, jar, bottle, carton. A packaged amount is a number of packages, with
  optional contents.
- **Contents:** the measure in one package: the 5 lb of a 5 lb bag.
- **Density:** grams per millilitre, which converts volume to mass. From the built-in table by name, or the
  item's own.
