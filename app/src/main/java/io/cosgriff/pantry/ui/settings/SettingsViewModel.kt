package io.cosgriff.pantry.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val inUse = MutableStateFlow<Location.Custom?>(null)

    val state: StateFlow<SettingsState> =
        combine(settings.settings(), pantry.locations(), inUse) { prefs, locations, busy -> SettingsState(prefs, locations, busy) }
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
    fun onDefaultLocation(location: DefaultLocation) = viewModelScope.launch { settings.setDefaultLocation(location) }
}
