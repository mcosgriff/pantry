package io.cosgriff.pantry.domain

import java.time.LocalTime

/** The Pantry settings screen's values (#8). Defaults are what a new install starts with. */
data class PantrySettings(
    /** Whether the daily digest counts items expiring within [warningDays] (#27). */
    val notifyExpiringSoon: Boolean = false,
    /** Whether the daily digest counts items already past their date (#27). */
    val notifyExpired: Boolean = false,
    /** Days before expiry an item counts as "use soon". */
    val warningDays: Int = DEFAULT_WARNING_DAYS,
    /** When the daily digest goes out, while either alert is on. */
    val dailyDigestAt: LocalTime = LocalTime.of(8, 0),
    val batchScanByDefault: Boolean = false,
    val defaultLocation: DefaultLocation = DefaultLocation.LastUsed,
    val unitSystem: UnitSystem = UnitSystem.Us,
    /** Whether a scanned barcode the pantry doesn't know is looked up in Open Food Facts (#9). */
    val productLookup: LookupChoice = LookupChoice.Ask,
) {
    init {
        require(warningDays >= 0) { "Warning window must not be negative: $warningDays" }
    }

    /** Whether there is a daily digest at all: one of the two alerts is on. */
    val digestOn: Boolean
        get() = notifyExpiringSoon || notifyExpired
}

/** Where the Scan screen puts a new item unless told otherwise. */
sealed interface DefaultLocation {
    data object LastUsed : DefaultLocation

    data class Fixed(val location: Location) : DefaultLocation
}

enum class UnitSystem { Us, Metric }

/** Product lookup's setting (#9, `docs/adr/0001`): off until the user says yes on the scan sheet or in settings. */
enum class LookupChoice { Ask, On, Off }
