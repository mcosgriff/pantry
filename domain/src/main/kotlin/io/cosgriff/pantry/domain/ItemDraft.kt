package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * An item being added or edited on the Item details screen (#7). Unlike [PantryItem], its name may be blank
 * while typing.
 *
 * While [expiresByHand] is false, the expiry date is the suggestion for the location and purchase date, and
 * follows them when they change. Setting a date (or clearing it) by hand stops that. A stored item's date
 * counts as set by hand, so moving it never changes its date.
 */
data class ItemDraft(
    val name: String,
    val location: Location,
    val quantity: Amount,
    val purchased: LocalDate,
    val expires: LocalDate?,
    val expiresByHand: Boolean,
    val barcode: String? = null,
    val opened: Boolean = false,
    val lowStockAt: BigDecimal? = null,
    val notes: String = "",
    val density: Density? = null,
    val id: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    fun withLocation(location: Location): ItemDraft =
        copy(location = location, expires = if (expiresByHand) expires else suggestedExpiry(purchased, location))

    fun withPurchased(date: LocalDate): ItemDraft =
        copy(purchased = date, expires = if (expiresByHand) expires else suggestedExpiry(date, location))

    fun withExpires(date: LocalDate?): ItemDraft = copy(expires = date, expiresByHand = true)

    /** The item to save, or `null` while the name is blank. Name and notes are trimmed. */
    fun toItem(): PantryItem? {
        if (name.isBlank()) return null
        return PantryItem(
            id = id,
            name = name.trim(),
            barcode = barcode,
            location = location,
            quantity = quantity,
            purchased = purchased,
            expires = expires,
            opened = opened,
            lowStockAt = lowStockAt,
            notes = notes.trim(),
            density = density,
        )
    }

    companion object {
        fun new(today: LocalDate, location: Location = Location.Dry, barcode: String? = null) = ItemDraft(
            name = "",
            location = location,
            quantity = Measure(BigDecimal.ONE, MeasureUnit.Each),
            purchased = today,
            expires = suggestedExpiry(today, location),
            expiresByHand = false,
            barcode = barcode,
        )

        fun of(item: PantryItem) = ItemDraft(
            id = item.id,
            name = item.name,
            barcode = item.barcode,
            location = item.location,
            quantity = item.quantity,
            purchased = item.purchased,
            expires = item.expires,
            expiresByHand = true,
            opened = item.opened,
            lowStockAt = item.lowStockAt,
            notes = item.notes,
            density = item.density,
        )
    }
}

/** The expiry quick picks on Item details, counted from today. */
enum class QuickPick {
    ThreeDays,
    OneWeek,
    OneMonth,
    NoDate,
    ;

    fun from(today: LocalDate): LocalDate? = when (this) {
        ThreeDays -> today.plusDays(3)
        OneWeek -> today.plusWeeks(1)
        OneMonth -> today.plusMonths(1)
        NoDate -> null
    }
}

/** The quantity stepper: [delta] more or fewer, never below zero, in the same unit or packages. */
fun Amount.stepped(delta: Int): Amount = withNumber((number + BigDecimal(delta)).max(BigDecimal.ZERO))
