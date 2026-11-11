package io.cosgriff.pantry.data

import androidx.room3.withWriteTransaction
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.QuickAction
import io.cosgriff.pantry.domain.checkPlan
import io.cosgriff.pantry.domain.cook
import io.cosgriff.pantry.domain.freeze
import io.cosgriff.pantry.domain.quickAction as chooseQuickAction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * A quick action taken on [before], the item as it was, so it can be undone; [shoppingId] is the "Used up" line it
 * added, if it added one.
 */
data class Consumed(val action: QuickAction, val before: PantryItem, val shoppingId: Long? = null)

/**
 * What using up, throwing out, freezing or cooking does to the pantry and the shopping list (#48). Each one is a
 * single transaction. Something used up leaves the pantry and goes on the list, unless its name is there already.
 *
 * Removing an item from item details isn't consumption: it's a correction, and adds no line until #18 decides
 * what a reason for removing means.
 */
@Singleton
class Consumption @Inject constructor(
    private val db: PantryDatabase,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
    private val meals: MealRepository,
) {
    /** Takes Use soon's quick action on [item] (`quickAction` decides which); returns what undoes it. */
    suspend fun quickAction(item: PantryItem, today: LocalDate): Consumed {
        val action = chooseQuickAction(item, today)
        return db.withWriteTransaction {
            val shoppingId = when (action) {
                QuickAction.ThrowOut -> {
                    pantry.delete(item.id)
                    null
                }
                QuickAction.UsedUp -> usedUp(item, today)
                QuickAction.Freeze -> {
                    pantry.save(freeze(item, today))
                    null
                }
            }
            Consumed(action, item, shoppingId)
        }
    }

    /** Puts the item back as it was before [consumed], and takes off the line it added. */
    suspend fun undo(consumed: Consumed) = db.withWriteTransaction {
        pantry.save(consumed.before)
        consumed.shoppingId?.let { shopping.remove(it) }
    }

    /**
     * "Cooked it": takes [plan]'s amounts out of the pantry, uses up what that empties, and marks the plan cooked.
     */
    suspend fun cooked(plan: MealPlan, today: LocalDate) {
        val cooked = cook(checkPlan(plan, pantry.items().first(), today))
        db.withWriteTransaction {
            cooked.updated.forEach { pantry.save(it) }
            cooked.usedUp.forEach { usedUp(it, today) }
            meals.markCooked(plan)
        }
    }

    private suspend fun usedUp(item: PantryItem, today: LocalDate): Long? {
        pantry.delete(item.id)
        return shopping.addUsedUp(item.name, today)
    }
}
