package dev.aaa1115910.bv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.bv.screen.user.FavoriteScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class FavoriteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                FavoriteScreen()
            }
        }
    }
}
