package io.cosgriff.pantry.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.Contributions
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ContributeChoice
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantrySettings
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Pantry settings shows. [locationInUse] names a location that couldn't be deleted because it holds items. */
data class SettingsState(
    val settings: PantrySettings = PantrySettings(),
    val locations: List<Location> = Location.defaults,
    val locationInUse: Location.Custom? = null,
    /** The signed-in Open Food Facts user name (#42), or `null`. */
    val offAccount: String? = null,
    /** Products shared with Open Food Facts from this phone. */
    val offShared: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
    private val contributions: Contributions,
) : ViewModel() {
    private val inUse = MutableStateFlow<Location.Custom?>(null)

    val state: StateFlow<SettingsState> =
        combine(settings.settings(), pantry.locations(), inUse, contributions.account(), contributions.sharedCount()) { prefs, locations, busy, account, shared ->
            SettingsState(prefs, locations, busy, account, shared)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun onAddLocation(name: String, shelfLifeDays: Int?) = viewModelScope.launch {
        pantry.addLocation(name.trim(), shelfLifeDays?.let(Period::ofDays))
    }

    fun onUpdateLocation(location: Location.Custom, name: String, shelfLifeDays: Int?) = viewModelScope.launch {
        pantry.updateLocation(location.copy(name = name.trim(), shelfLife = shelfLifeDays?.let(Period::ofDays)))
    }

    fun onDeleteLocation(location: Location.Custom) = viewModelScope.launch {
        if (!pantry.deleteLocation(location)) inUse.value = location
    }

    fun onDismissInUse() {
        inUse.value = null
    }

    fun onWarningDays(days: Int) = viewModelScope.launch { settings.setWarningDays(days) }
    fun onBatchScan(on: Boolean) = viewModelScope.launch { settings.setBatchScanByDefault(on) }
    fun onProductLookup(on: Boolean) = viewModelScope.launch { settings.setProductLookup(on) }
    fun onContributing(choice: ContributeChoice) = viewModelScope.launch { settings.setContributing(choice) }
    fun onSignOut() = viewModelScope.launch { contributions.signOut() }
    fun onDefaultLocation(location: DefaultLocation) = viewModelScope.launch { settings.setDefaultLocation(location) }
}
