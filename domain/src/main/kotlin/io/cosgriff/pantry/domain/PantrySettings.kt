package io.cosgriff.pantry.domain

import java.time.LocalTime

/** The Pantry settings screen's values (#8). Defaults are what a new install starts with. */
data class PantrySettings(
    val notifications: Boolean = false,
    /** Days before expiry an item counts as "use soon". */
    val warningDays: Int = DEFAULT_WARNING_DAYS,
    val dailyDigestAt: LocalTime = LocalTime.of(8, 0),
    val batchScanByDefault: Boolean = false,
    val defaultLocation: DefaultLocation = DefaultLocation.LastUsed,
    val unitSystem: UnitSystem = UnitSystem.Us,
) {
    init {
        require(warningDays >= 0) { "Warning window must not be negative: $warningDays" }
    }
}

/** Where the Scan screen puts a new item unless told otherwise. */
sealed interface DefaultLocation {
    data object LastUsed : DefaultLocation

    data class Fixed(val location: Location) : DefaultLocation
}

enum class UnitSystem { Us, Metric }
