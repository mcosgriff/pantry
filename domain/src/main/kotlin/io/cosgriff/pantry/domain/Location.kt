package io.cosgriff.pantry.domain

import java.time.Period

/**
 * Where an item is kept. Dry, Fridge and Freezer are built in; users can add their own (#8).
 *
 * The built-in ones carry no name: the app shows its own translated wording for each (#3).
 */
sealed interface Location {
    /** Typical shelf life of something put here, used to suggest an expiry date. `null`: no suggestion. */
    val shelfLife: Period?

    data object Dry : Location {
        override val shelfLife: Period? = null
    }

    data object Fridge : Location {
        override val shelfLife: Period = Period.ofDays(7)
    }

    data object Freezer : Location {
        override val shelfLife: Period = Period.ofMonths(3)
    }

    /** A location the user added. [name] is their own text, shown as typed. */
    data class Custom(val name: String, override val shelfLife: Period?) : Location {
        init {
            require(name.isNotBlank()) { "Location name must not be blank" }
        }
    }

    companion object {
        val defaults: List<Location> = listOf(Dry, Fridge, Freezer)
    }
}
