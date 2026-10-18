package me.ash.reader.brief.app

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.brief.notifications.NotificationHelper
import me.ash.reader.brief.splash.BriefSplash
import me.ash.reader.brief.ui.DialogHost
import me.ash.reader.infrastructure.preference.DarkThemePreference
import me.ash.reader.ui.ext.dataStore

/*
 * Brief You's additions to Read You's MainActivity, kept here so Read You's file only needs a few
 * calls: the animated splash, dialogs drawn in the app's window, the system splash following the
 * app's dark theme setting, and clearing the new-articles digest when the app is opened.
 */
object BriefActivity {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun notificationHelper(): NotificationHelper
    }

    /**
     * Call in onCreate. Returns whether the animated splash plays: on fresh launches only (not
     * when the activity is recreated). Its first frame is the system splash's (the straight card),
     * so the system splash is removed at once and BriefSplash tips the card and carries on.
     */
    fun onCreate(activity: ComponentActivity, savedInstanceState: Bundle?): Boolean {
        val playSplash = savedInstanceState == null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playSplash) {
            activity.splashScreen.setOnExitAnimationListener { view -> view.remove() }
        }
        syncSplashNightMode(activity)
        BriefShortcuts.publish(activity)
        return playSplash
    }

    /** Call in onResume: opening the app clears the new-articles digest. */
    fun onResume(context: Context) {
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
            .notificationHelper()
            .clearDigest()
    }

    /**
     * The system splash is drawn before the app runs, from the phone's light or dark mode. Tell
     * Android which one the app uses (its Dark theme setting), so the splash on the next launch
     * matches the app: a light splash for a light app on a dark phone, and the other way round.
     */
    private fun syncSplashNightMode(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = activity.getSystemService(UiModeManager::class.java) ?: return
        activity.lifecycleScope.launch {
            activity.dataStore.data
                .map { DarkThemePreference.fromPreferences(it) }
                .distinctUntilChanged()
                .collect { preference ->
                    val mode =
                        when (preference) {
                            DarkThemePreference.ON -> UiModeManager.MODE_NIGHT_YES
                            DarkThemePreference.OFF -> UiModeManager.MODE_NIGHT_NO
                            else -> UiModeManager.MODE_NIGHT_AUTO
                        }
                    // Only when it changed: setting it can restart the screen.
                    val prefs =
                        activity.getSharedPreferences("splash_night_mode", Context.MODE_PRIVATE)
                    if (prefs.getInt("mode", -1) != mode) {
                        prefs.edit().putInt("mode", mode).apply()
                        uiModeManager.setApplicationNightMode(mode)
                    }
                }
        }
    }
}

/**
 * Around the app's content: dialogs drawn in the app's window (see InWindowDialog), and the
 * animated splash over everything when [playSplash].
 */
@Composable
fun BriefRoot(playSplash: Boolean, content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(playSplash) }
    Box(modifier = Modifier.fillMaxSize()) {
        DialogHost { content() }
        if (showSplash) BriefSplash(onFinished = { showSplash = false })
    }
}
