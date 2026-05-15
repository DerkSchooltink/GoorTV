package dev.goor.tv

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dev.goor.tv.ui.navigation.AppNavigation
import dev.goor.tv.ui.theme.GoorTVTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoorTVTheme {
                AppNavigation()
            }
        }
    }
}
