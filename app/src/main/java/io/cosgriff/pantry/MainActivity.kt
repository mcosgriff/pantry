package io.cosgriff.pantry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.pantry.domain.firstWebLink
import io.cosgriff.pantry.ui.PantryNavHost
import io.cosgriff.pantry.ui.theme.PantryTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A restored activity has its back stack already; only a fresh share opens the import.
        val shared = if (savedInstanceState == null) sharedLink(intent) else null
        setContent {
            PantryTheme {
                PantryNavHost(sharedLink = shared)
            }
        }
    }

    /** The link in text shared to Pantry (Share → Pantry in the browser, #43), or the text itself to show. */
    private fun sharedLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return firstWebLink(text) ?: text.trim()
    }
}
