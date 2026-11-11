package io.cosgriff.pantry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.notify.DigestScheduler
import io.cosgriff.pantry.ui.PantryNavHost
import io.cosgriff.pantry.ui.theme.PantryTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var digest: DigestScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The digest's alarm follows its settings (#27); setting it on each start also brings it back after
        // Android dropped it, as it does when the app is force-stopped.
        lifecycleScope.launch {
            settings.settings()
                .distinctUntilChangedBy { Triple(it.notifyExpiringSoon, it.notifyExpired, it.dailyDigestAt) }
                .collect(digest::schedule)
        }
        val openUseSoon = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_USE_SOON, false)
        setContent {
            PantryTheme {
                PantryNavHost(openUseSoon = openUseSoon)
            }
        }
    }

    companion object {
        /** Set by the digest notification: start on Use soon, with the pantry list behind it. */
        const val EXTRA_OPEN_USE_SOON = "io.cosgriff.pantry.OPEN_USE_SOON"
    }
}
