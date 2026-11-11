package io.cosgriff.pantry.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Default number of days before expiry that an item counts as "use soon" (Pantry settings board). */
const val DEFAULT_WARNING_DAYS = 3

/** How an expiry date stands against today. Day counts are typed so the app can word them. */
sealed interface ExpiryStatus {
    data object NoDate : ExpiryStatus

    /** Past its date. [daysAgo] ≥ 1. */
    data class Expired(val daysAgo: Long) : ExpiryStatus

    /** Expires within the warning window. [daysLeft] is 0 when it expires today. */
    data class ExpiresSoon(val daysLeft: Long) : ExpiryStatus

    /** Expires after the warning window. */
    data class Fine(val daysLeft: Long) : ExpiryStatus
}

/**
 * Status of [expires] on [today]. An item expiring today is still usable today, so it is [ExpiryStatus.ExpiresSoon]
 * with 0 days left; the last day of the window is inside it.
 */
fun expiryStatus(expires: LocalDate?, today: LocalDate, warningDays: Int = DEFAULT_WARNING_DAYS): ExpiryStatus {
    require(warningDays >= 0) { "Warning window must not be negative: $warningDays" }
    if (expires == null) return ExpiryStatus.NoDate
    val daysLeft = ChronoUnit.DAYS.between(today, expires)
    return when {
        daysLeft < 0 -> ExpiryStatus.Expired(daysAgo = -daysLeft)
        daysLeft <= warningDays -> ExpiryStatus.ExpiresSoon(daysLeft)
        else -> ExpiryStatus.Fine(daysLeft)
    }
}

/** Expiry date to suggest for a new item: [purchased] plus [location]'s shelf life, or `null` if it has none. */
fun suggestedExpiry(purchased: LocalDate, location: Location): LocalDate? =
    location.shelfLife?.let(purchased::plus)
