package com.example.portfoliopilot

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity

/** FragmentActivity (a ComponentActivity) because the biometric prompt of the app lock needs it. */
class MainActivity : FragmentActivity() {
    private val vm: PortfolioViewModel by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Navy system splash with the full logo (no white area before the app's own splash).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(
                themeMode = vm.themeMode,
                accent = vm.accentColor,
                fontStyle = vm.fontStyle,
                textSize = vm.textSize,
                darkBars = vm.showSplash
            ) {
                // With the app lock on, hide the screen in "recent apps" and block screenshots.
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (vm.appLockEnabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                PortfolioApp(vm, this@MainActivity)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Lock again when the app was in the background for more than 30 seconds.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 30_000L) vm.lock()
    }
}
