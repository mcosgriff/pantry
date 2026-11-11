# 2. One amount model for the pantry and recipes, with unit conversions

- Status: accepted, 2026-11-11

## Context

Pantry items and meal ingredients each store an amount as a number and a free `unit` string, from one flat
enum, `QuantityUnit { Each, Pound, Ounce, Bag, Pack, Tub }`. That enum mixes three kinds of thing: counts
(each), weights (lb, oz) and packages (bag, pack, tub). It has no volume units, so recipes can't say "2 cups"
or "1 tbsp". Nothing converts, so an ingredient only matches a pantry item in exactly the same unit, and
cooking "1 lb" can't come out of "2 bags".

## Decision

### Units

Every unit has a **dimension** (count, volume or mass) and a fixed factor to that dimension's base: one, the
millilitre or the gram. Volume and mass units belong to a **measurement system**, US customary or metric.

| Dimension | US customary | Metric | Base |
|---|---|---|---|
| Count | each, dozen | | each |
| Volume | tsp, tbsp, fl oz, cup, pint, quart, gallon | ml, l | ml |
| Mass | oz, lb | g, kg | g |

US units use their exact legal definitions:
- 1 gallon = 3.785411784 l, and the cup, pint, quart, fluid ounce, tablespoon and teaspoon are fixed fractions
  of it.
- 1 lb = 453.59237 g, and 1 oz = 1/16 lb.

The cup is the customary 236.5882365 ml, not the 240 ml "legal cup" used on nutrition labels. "Ounce" always
means weight; fluid ounce is its own unit.

### Amounts

An **amount** is one of two shapes, used by pantry items and recipe ingredients alike:

- **Measure:** a number and a unit, e.g. `12 each`, `1.24 lb`, `2 cup`.
- **Packaged:** a number of **packages** (bag, pack, tub, box, can, jar, bottle, carton), with optional
  **contents** per package, a measure. For example `2 bag × 5 lb`, or `1 tub` with no contents given.

Numbers are exact (`BigDecimal`) and never negative. Rounding happens only for display, or where a count must
be whole: a recipe's 5.33 eggs becomes 6 (#19).

### Conversions

- **Within a dimension,** conversion is exact: 1 cup is 16 tbsp, 1 kg is 2.2046… lb.
- **Between volume and mass,** conversion needs a **density** in grams per millilitre. Pantry ships a table of
  typical densities for common ingredients: flours, sugars, butter, milk, water, oils, honey, rice, oats, salt,
  cocoa. It matches an ingredient or item by name, the longest matching phrase winning, so "brown sugar" beats
  "sugar". The values are approximate kitchen figures and say so. An item can set its own density, which wins
  over the table.
- **Count converts only to count.** Each and dozen convert to each other, never to volume or mass.
- **A packaged amount** converts through its contents. `2 bag × 5 lb` is 10 lb; taking 1 lb leaves 1.8 bags.
  Without contents, a package converts only to packages of the same kind.
- **When a conversion isn't possible,** the result says so; Pantry never guesses. A recipe's "2 cups flour"
  against a "1 bag" of flour with no contents is shown as not comparable, not as a match.

### Showing amounts

A setting picks the measurement system to show: US customary by default, or metric. Amounts are stored in the
unit they were entered in. Pantry converts only to compare, to subtract, or when the user asks to see another
system.

### Storage

Pantry items and meal ingredients store an amount the same way: amount, unit, contents amount, contents unit,
and density (items only, as an override). The unit column holds either a unit or a package name. The existing
stored names keep their meaning, so old rows read unchanged:
- `Each`, `Pound` and `Ounce` (by weight) are units;
- `Bag`, `Pack` and `Tub` become packages with no contents.

## Consequences

- Recipes can be written in the units cookbooks use. Ingredients match pantry items across units when the
  conversion is defined, and cooking subtracts in the item's own unit.
- One amount model and one storage shape mean one set of rules to test, and a single place for future units.
- The density table is approximate. A flour's weight per cup varies with how it's scooped, so cross-dimension
  results are estimates. The UI marks them "≈". An item's own density removes the guess.
- A schema change (new nullable columns, an auto-migration) and changes to the unit pickers follow. These are
  tracked as their own issues.
