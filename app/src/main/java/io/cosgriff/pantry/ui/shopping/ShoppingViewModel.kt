package io.cosgriff.pantry.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.ShoppingRepository
import io.cosgriff.pantry.domain.ShoppingEntry
import io.cosgriff.pantry.domain.ShoppingLine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingState(
    val loading: Boolean = true,
    val lines: List<ShoppingLine> = emptyList(),
    val cart: Set<String> = emptySet(),
)

/** Used by the Shopping list and the trip: both show the same lines and cart. */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<ShoppingState> = combine(shopping.lines(), shopping.cart()) { lines, cart -> ShoppingState(false, lines, cart) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

    fun onAdd(name: String) {
        viewModelScope.launch { shopping.addByHand(name, LocalDate.now(clock)) }
    }

    fun onRemove(entry: ShoppingEntry) {
        viewModelScope.launch { shopping.remove(entry) }
    }

    fun onToggle(line: ShoppingLine, inCart: Boolean) {
        viewModelScope.launch { shopping.setInCart(line.key, inCart) }
    }

    /** Ends the trip, then [then] (opening the scanner to restock). */
    fun onFinish(then: () -> Unit) {
        viewModelScope.launch {
            shopping.finishTrip()
            then()
        }
    }
}
