package io.cosgriff.pantry.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.Contributions
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.data.ShareResult
import io.cosgriff.pantry.data.SignInResult
import io.cosgriff.pantry.domain.ContributeChoice
import io.cosgriff.pantry.domain.NewProduct
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The share prompt, then the form, then thanks. */
enum class ShareStep { Ask, Details, Done }

/** Why the last try didn't share. */
enum class ShareProblem { WrongDetails, Offline, AlreadyThere, SignedOut, Rejected }

/**
 * Sharing [product] with Open Food Facts (#42). [account] is the signed-in user name; while it is `null` the form
 * asks for [user] and [password], which are kept only until signing in.
 */
data class ShareState(
    val step: ShareStep = ShareStep.Ask,
    val product: NewProduct = NewProduct("", ""),
    val alwaysShare: Boolean = false,
    val account: String? = null,
    val user: String = "",
    val password: String = "",
    val sending: Boolean = false,
    val problem: ShareProblem? = null,
    /** The screen closes. */
    val closed: Boolean = false,
) {
    val canSend: Boolean
        get() = !sending && product.shareable && (account != null || (user.isNotBlank() && password.isNotEmpty()))
}

@HiltViewModel
class ShareProductViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val contributions: Contributions,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()
    private var loaded = false

    init {
        viewModelScope.launch { contributions.account().collect { account -> _state.update { it.copy(account = account) } } }
    }

    /** Starts on [barcode] and the [name] the user gave it: at the prompt, or the form with "Always share". */
    fun load(barcode: String, name: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val choice = settings.settings().first().contributing
            _state.update {
                it.copy(
                    product = NewProduct(barcode, name),
                    step = if (choice == ContributeChoice.Always) ShareStep.Details else ShareStep.Ask,
                    closed = choice == ContributeChoice.Never,
                )
            }
        }
    }

    fun onAlwaysShare(on: Boolean) = _state.update { it.copy(alwaysShare = on) }

    fun onContinue() {
        if (_state.value.alwaysShare) viewModelScope.launch { settings.setContributing(ContributeChoice.Always) }
        _state.update { it.copy(step = ShareStep.Details) }
    }

    fun onName(text: String) = editProduct { it.copy(name = text) }
    fun onBrand(text: String) = editProduct { it.copy(brand = text) }
    fun onSize(text: String) = editProduct { it.copy(size = text) }
    fun onUser(text: String) = _state.update { it.copy(user = text, problem = null) }
    fun onPassword(text: String) = _state.update { it.copy(password = text, problem = null) }

    private fun editProduct(change: (NewProduct) -> NewProduct) = _state.update { it.copy(product = change(it.product), problem = null) }

    /** Signs in first if needed (the password is then forgotten), and shares. */
    fun onShare() {
        val now = _state.value
        if (!now.canSend) return
        _state.update { it.copy(sending = true, problem = null) }
        viewModelScope.launch {
            if (now.account == null) {
                val problem = when (contributions.signIn(now.user, now.password)) {
                    is SignInResult.SignedIn -> null
                    SignInResult.WrongDetails -> ShareProblem.WrongDetails
                    SignInResult.Failed -> ShareProblem.Offline
                }
                if (problem != null) {
                    _state.update { it.copy(sending = false, problem = problem) }
                    return@launch
                }
                _state.update { it.copy(password = "") }
            }
            val result = contributions.share(now.product)
            _state.update {
                when (result) {
                    ShareResult.Shared -> it.copy(sending = false, step = ShareStep.Done)
                    ShareResult.AlreadyThere -> it.copy(sending = false, problem = ShareProblem.AlreadyThere)
                    ShareResult.SignedOut -> it.copy(sending = false, problem = ShareProblem.SignedOut)
                    ShareResult.Rejected -> it.copy(sending = false, problem = ShareProblem.Rejected)
                    ShareResult.Failed -> it.copy(sending = false, problem = ShareProblem.Offline)
                }
            }
        }
    }

    /** "Not now", "Skip", back, or "Done": nothing more is sent. */
    fun onClose() = _state.update { it.copy(closed = true, password = "") }
}
