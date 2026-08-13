package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.PinScreen
import com.example.ui.WebViewScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private companion object {
        const val PREFS_NAME = "ram_billing_prefs"
        const val KEY_REMEMBER_SESSION = "key_remember_session"
        const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        const val DOUBLE_BACK_DELAY = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialRemember = prefs.getBoolean(KEY_REMEMBER_SESSION, true)
        val initialLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false) && initialRemember

        setContent {
            MyApplicationTheme {
                var isAuthenticated by remember { mutableStateOf(initialLoggedIn) }
                var rememberSession by remember { mutableStateOf(initialRemember) }
                var lastBackPressTime by remember { mutableLongStateOf(0L) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = isAuthenticated,
                        animationSpec = tween(durationMillis = 350),
                        label = "AuthTransition"
                    ) { loggedIn ->
                        if (loggedIn) {
                            WebViewScreen(
                                initialUrl = "https://ram-billing.lovable.app/",
                                onLockApp = {
                                    isAuthenticated = false
                                    prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
                                    Toast.makeText(this@MainActivity, "App locked", Toast.LENGTH_SHORT).show()
                                },
                                onBackToExit = {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastBackPressTime < DOUBLE_BACK_DELAY) {
                                        finish()
                                    } else {
                                        lastBackPressTime = currentTime
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Press back again to exit RAM Billing",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        } else {
                            PinScreen(
                                correctPin = "8460",
                                rememberSession = rememberSession,
                                onRememberSessionChange = { remember ->
                                    rememberSession = remember
                                    prefs.edit().putBoolean(KEY_REMEMBER_SESSION, remember).apply()
                                },
                                onPinSuccess = {
                                    isAuthenticated = true
                                    if (rememberSession) {
                                        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
