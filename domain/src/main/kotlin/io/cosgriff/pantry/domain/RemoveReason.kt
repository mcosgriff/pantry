package io.cosgriff.pantry.domain

/**
 * What happened to an item removed from item details (#18), in the order the dialog asks. Used it up is the first
 * and the one chosen to start with: it's what usually happened.
 */
enum class RemoveReason {
    /** It's gone because it was eaten: it goes on the shopping list, as Use soon's "Used up" does (#48). */
    UsedUp,

    /** It spoiled or was thrown away. Nothing records waste yet, so for now it only leaves the pantry. */
    ThrewOut,

    /** It was never there: removing it changes nothing else. */
    AddedByMistake,
    ;

    /**
     * Whether removing for this reason puts the item's name on the shopping list. Only using it up does: something
     * thrown out may not be wanted again, and a mistake was never bought. #18 asked whether only things bought
     * often should go on; nothing counts purchases yet, so every used-up item does, once per name, as Use soon's
     * quick action and "Cooked it" already do.
     */
    val addsToShoppingList: Boolean get() = this == UsedUp
}
