package io.cosgriff.pantry.domain

/**
 * A location's identity, the one key scheme for locations (#51): a built-in location by its name (`DRY`,
 * `FRIDGE`, `FREEZER`), a location the user added by its id (`custom:<id>`).
 *
 * Settings store [text]. The database keeps the same text for a built-in location in `locations.builtIn` and
 * the row id for one the user added, so the two are one scheme, not two.
 */
sealed interface LocationKey {
    /** The key as stored. */
    val text: String

    /** Dry, Fridge or Freezer. */
    data class BuiltIn(val location: Location) : LocationKey {
        init {
            require(location !is Location.Custom) { "Not a built-in location: $location" }
        }

        override val text: String
            get() = when (location) {
                Location.Dry -> "DRY"
                Location.Fridge -> "FRIDGE"
                Location.Freezer -> "FREEZER"
                is Location.Custom -> error("unreachable")
            }
    }

    /** A location the user added, by its saved id. */
    data class Custom(val id: Long) : LocationKey {
        init {
            require(id != 0L) { "A location has no key until it is saved" }
        }

        override val text: String get() = "$CUSTOM_PREFIX$id"
    }

    companion object {
        private const val CUSTOM_PREFIX = "custom:"

        /** [location]'s key. A location the user added must be saved first (its id is not 0). */
        fun of(location: Location): LocationKey = when (location) {
            is Location.Custom -> Custom(location.id)
            else -> BuiltIn(location)
        }

        /** The key [text] stands for, or `null` for text the app doesn't understand. */
        fun parse(text: String): LocationKey? = when (text) {
            "DRY" -> BuiltIn(Location.Dry)
            "FRIDGE" -> BuiltIn(Location.Fridge)
            "FREEZER" -> BuiltIn(Location.Freezer)
            else -> text.removePrefix(CUSTOM_PREFIX).takeIf { it != text }?.toLongOrNull()?.takeIf { it != 0L }?.let(::Custom)
        }
    }
}

/** [location]'s key; `null` for a location the user added that isn't saved yet. */
val Location.key: LocationKey?
    get() = if (this is Location.Custom && id == 0L) null else LocationKey.of(this)

/** The location in this list with [key], as it is now (renamed, say); `null` if none has it: it was deleted. */
fun List<Location>.withKey(key: LocationKey?): Location? = key?.let { k -> firstOrNull { it.key == k } }

/**
 * What each use of a location does when the location it refers to was deleted (#51). One place for the whole
 * policy; each function takes the reference already resolved, `null` meaning the location is gone.
 *
 * | Use                              | Falls back to                                            |
 * |----------------------------------|----------------------------------------------------------|
 * | An item                          | Never happens: a location holding items can't be deleted |
 * | Settings' default location       | "Last used"                                              |
 * | A new item's starting location   | The default, else the last used one, else Dry            |
 * | A known product (#12)            | No location (the database sets it to null, `SET_NULL`);  |
 * |                                  | the scan sheet then uses the new-item location           |
 * | The pantry list's chosen tab     | All                                                      |
 */
object DeletedLocation {
    /** Settings' default location: a deleted one reads as "Last used", which is what the settings screen shows. */
    fun defaultLocation(resolved: Location?): DefaultLocation = resolved?.let(DefaultLocation::Fixed) ?: DefaultLocation.LastUsed

    /**
     * Where a new item starts: the default location, else the last used one, else Dry (also before any item
     * was added). A deleted default is skipped as if "Last used" were set, matching [defaultLocation].
     */
    fun newItem(default: Location?, lastUsed: Location?): Location = default ?: lastUsed ?: Location.Dry

    /** The pantry list's chosen tab, as it is now among [locations]; `null` (All) once deleted. */
    fun chosenTab(chosen: Location?, locations: List<Location>): Location? = locations.withKey(chosen?.key)
}
