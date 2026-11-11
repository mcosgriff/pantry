package io.cosgriff.pantry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.pantry.ui.pantry.PantryListActions
import io.cosgriff.pantry.ui.pantry.PantryListScreen
import io.cosgriff.pantry.ui.theme.PantryTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PantryTheme {
                // Buttons for screens not built yet go nowhere: Item details (#7), Pantry settings (#8),
                // Scan (#10), Use soon (#16), Sort and filter (#17), Meal prep (#19), Shopping (#20).
                PantryListScreen(actions = PantryListActions())
            }
        }
    }
}
