package io.cosgriff.pantry.domain

import java.math.BigDecimal

/**
 * [own], an item's own density, or a typical one for [name] from the built-in table; `null` when neither
 * applies. The table matches whole words, the longest phrase first, so "brown sugar" wins over "sugar" and
 * "buttermilk" isn't butter (docs/adr/0002, #54).
 */
fun densityOf(name: String, own: Density? = null): Density? {
    if (own != null) return own
    val words = " " + name.lowercase().replace(Regex("[^a-z]+"), " ").trim() + " "
    return TYPICAL_DENSITIES.entries
        .filter { (phrase, _) -> " $phrase " in words }
        .maxByOrNull { (phrase, _) -> phrase.length }
        ?.value
}

/**
 * Typical kitchen densities in grams per millilitre. Approximate: a cup of flour weighs more or less with how
 * it's filled, so anything converted through these is shown as an estimate.
 */
private val TYPICAL_DENSITIES: Map<String, Density> = mapOf(
    "water" to "1.00",
    "milk" to "1.03",
    "buttermilk" to "1.03",
    "cream" to "1.00",
    "vegetable oil" to "0.92",
    "olive oil" to "0.91",
    "oil" to "0.92",
    "honey" to "1.42",
    "maple syrup" to "1.32",
    "butter" to "0.96",
    "peanut butter" to "1.09",
    "flour" to "0.51",
    "all purpose flour" to "0.51",
    "bread flour" to "0.51",
    "whole wheat flour" to "0.48",
    "cake flour" to "0.48",
    "sugar" to "0.84",
    "granulated sugar" to "0.84",
    "brown sugar" to "0.90",
    "powdered sugar" to "0.48",
    "confectioners sugar" to "0.48",
    "salt" to "1.22",
    "table salt" to "1.22",
    "rice" to "0.85",
    "oats" to "0.38",
    "rolled oats" to "0.38",
    "cocoa" to "0.36",
    "cocoa powder" to "0.36",
).mapValues { (_, grams) -> Density(BigDecimal(grams)) }
