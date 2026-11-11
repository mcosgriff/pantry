package io.cosgriff.pantry.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * What the daily digest says (#27): how many items expire within the warning window ("Warn me", today
 * included) and how many are already past their date. A count whose alert is off is 0.
 */
data class ExpiryDigest(val expiringSoon: Int, val expired: Int) {
    /** Nothing to tell: no notification goes out that day. */
    val isEmpty: Boolean
        get() = expiringSoon == 0 && expired == 0
}

/** The digest for [items] on [today], counting only what [settings] has turned on. Undated items never count. */
fun expiryDigest(items: List<PantryItem>, today: LocalDate, settings: PantrySettings): ExpiryDigest {
    val statuses = items.map { it.expiryStatus(today, settings.warningDays) }
    return ExpiryDigest(
        expiringSoon = if (settings.notifyExpiringSoon) statuses.count { it is ExpiryStatus.ExpiresSoon } else 0,
        expired = if (settings.notifyExpired) statuses.count { it is ExpiryStatus.Expired } else 0,
    )
}

/**
 * When the next digest goes out: today at [at] if that is still ahead of [now], otherwise tomorrow at [at]. On a
 * day the clocks skip [at] (daylight saving), it goes out that much later in wall time.
 */
fun nextDigest(now: ZonedDateTime, at: LocalTime): ZonedDateTime {
    val today = ZonedDateTime.of(now.toLocalDate(), at, now.zone)
    return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), at, now.zone)
}
