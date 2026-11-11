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

    /** Puts [name] on the list; returns its id, or `null` for a blank name. */
    suspend fun add(name: String, reason: ShoppingReason, today: LocalDate): Long? {
        require(reason != ShoppingReason.RunningLow) { "Running-low lines come from the pantry" }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return dao.insert(ShoppingEntryEntity(name = trimmed, reason = reason.stored(), addedOn = today.toEpochDay()))
    }

    suspend fun remove(entry: ShoppingEntry) = dao.delete(listOf(entry.id))

    suspend fun remove(id: Long) = dao.delete(listOf(id))

    suspend fun setInCart(key: String, inCart: Boolean) = store.edit {
        val now = it[CART].orEmpty()
        it[CART] = if (inCart) now + key else now - key
    }

    /**
     * Ends the trip: stored lines that were in the cart come off the list, and the cart empties. Running-low lines
     * leave by themselves once their items are restocked; what wasn't got stays on the list.
     */
    suspend fun finishTrip() {
        val cart = cart().first()
        val done = lines().first().filter { it.key in cart }.mapNotNull { it.entry?.id }
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
    ShoppingReason.RunningLow -> error("not stored")
}

private fun ShoppingEntryEntity.toDomain() = ShoppingEntry(
    id = id,
    name = name,
    reason = if (reason == "USED_UP") ShoppingReason.UsedUp else ShoppingReason.ByHand,
    addedOn = LocalDate.ofEpochDay(addedOn),
)
