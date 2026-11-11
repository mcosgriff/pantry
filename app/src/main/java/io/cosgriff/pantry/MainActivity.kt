package io.cosgriff.pantry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.firstWebLink
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
        // A restored activity has its back stack already; only a fresh start opens a shared link or Use soon.
        val shared = if (savedInstanceState == null) sharedLink(intent) else null
        val openUseSoon = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_USE_SOON, false)
        setContent {
            PantryTheme {
                PantryNavHost(sharedLink = shared, openUseSoon = openUseSoon)
            }
        }
    }

    /** The link in text shared to Pantry (Share → Pantry in the browser, #43), or the text itself to show. */
    private fun sharedLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return firstWebLink(text) ?: text.trim()
    }

    companion object {
        /** Set by the digest notification: start on Use soon, with the pantry list behind it. */
        const val EXTRA_OPEN_USE_SOON = "io.cosgriff.pantry.OPEN_USE_SOON"
    }
}
