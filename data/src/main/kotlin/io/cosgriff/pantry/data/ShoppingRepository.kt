package io.cosgriff.pantry.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.cosgriff.pantry.data.db.ShoppingDao
import io.cosgriff.pantry.data.db.ShoppingEntryEntity
import io.cosgriff.pantry.domain.ShoppingEntry
import io.cosgriff.pantry.domain.ShoppingLine
import io.cosgriff.pantry.domain.ShoppingReason
import io.cosgriff.pantry.domain.shoppingKey
import io.cosgriff.pantry.domain.shoppingList
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The shopping list (#20): running-low lines worked out from the pantry, stored used-up and by-hand lines, and the
 * trip's cart (the keys of lines checked off), which lives in DataStore beside the settings.
 */
@Singleton
class ShoppingRepository @Inject constructor(
    private val dao: ShoppingDao,
    private val pantry: PantryRepository,
    private val store: DataStore<Preferences>,
) {
    fun lines(): Flow<List<ShoppingLine>> =
        combine(pantry.items(), dao.observeAll()) { items, rows -> shoppingList(items, rows.map { it.toDomain() }) }

    fun cart(): Flow<Set<String>> = store.data.map { it[CART].orEmpty() }

    /** Puts [name] on the list by hand; returns its id, or `null` when it's blank or already on the list. */
    suspend fun addByHand(name: String, today: LocalDate): Long? = insertNew(name, ShoppingReason.ByHand, today)

    /**
     * Puts [name] on the list as used up; returns its id, or `null` when it's blank or already on the list. Only
     * [Consumption] calls this: using something up also changes the pantry (#48).
     */
    internal suspend fun addUsedUp(name: String, today: LocalDate): Long? = insertNew(name, ShoppingReason.UsedUp, today)

    /** Puts [names] on the list for [meal], planned on [day] (#19). A name already on the list isn't added again. */
    suspend fun addForMeal(names: List<String>, meal: String, day: LocalDate, today: LocalDate) {
        val onList = namesOnList().toMutableSet()
        names.map { it.trim() }.filter { it.isNotEmpty() && onList.add(shoppingKey(it)) }.forEach {
            dao.insert(ShoppingEntryEntity(name = it, reason = ShoppingReason.ForMeal.stored(), addedOn = today.toEpochDay(), meal = meal, mealDay = day.toEpochDay()))
        }
    }

    /**
     * A name is on the list once (#48). The list shows one line per name, so a second stored entry with the same
     * name would be hidden, and a trip couldn't take it off.
     */
    private suspend fun insertNew(name: String, reason: ShoppingReason, today: LocalDate): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || shoppingKey(trimmed) in namesOnList()) return null
        return dao.insert(ShoppingEntryEntity(name = trimmed, reason = reason.stored(), addedOn = today.toEpochDay()))
    }

    private suspend fun namesOnList(): Set<String> = lines().first().mapTo(mutableSetOf()) { shoppingKey(it.name) }

    suspend fun remove(entry: ShoppingEntry) = dao.delete(listOf(entry.id))

    internal suspend fun remove(id: Long) = dao.delete(listOf(id))

    suspend fun setInCart(key: String, inCart: Boolean) = store.edit {
        val now = it[CART].orEmpty()
        it[CART] = if (inCart) now + key else now - key
    }

    /**
     * Ends the trip: stored lines that were in the cart come off the list, and the cart empties. Running-low lines
     * leave by themselves once their items are restocked; what wasn't got stays on the list. Every stored entry
     * with a bought line's name goes, so none hidden behind that line is left to reappear (#48); that includes a
     * for-meal entry for another day, since a name is on the list once.
     */
    suspend fun finishTrip() {
        val cart = cart().first()
        val bought = lines().first().filter { it.key in cart }.mapTo(mutableSetOf()) { shoppingKey(it.name) }
        val done = dao.observeAll().first().filter { shoppingKey(it.name) in bought }.map { it.id }
        if (done.isNotEmpty()) dao.delete(done)
        store.edit { it.remove(CART) }
    }

    private companion object {
        val CART = stringSetPreferencesKey("shopping_cart")
    }
}

private fun ShoppingReason.stored() = when (this) {
    ShoppingReason.UsedUp -> "USED_UP"
    ShoppingReason.ByHand -> "BY_HAND"
    ShoppingReason.ForMeal -> "FOR_MEAL"
    ShoppingReason.RunningLow -> error("not stored")
}

private fun ShoppingEntryEntity.toDomain() = ShoppingEntry(
    id = id,
    name = name,
    reason = when (reason) {
        "USED_UP" -> ShoppingReason.UsedUp
        "FOR_MEAL" -> ShoppingReason.ForMeal
        else -> ShoppingReason.ByHand
    },
    addedOn = LocalDate.ofEpochDay(addedOn),
    meal = meal,
    mealDay = mealDay?.let(LocalDate::ofEpochDay),
)
