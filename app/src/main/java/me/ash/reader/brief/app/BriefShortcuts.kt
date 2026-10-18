package me.ash.reader.brief.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.util.Consumer
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.ash.reader.R
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.MainActivity
import me.ash.reader.ui.page.nav3.key.Route

/**
 * App shortcuts (long-press the app icon): Triage, AI gist and the Unread list of everything, and
 * Sync now. Published from Brief You's own code (no manifest entry) and handled by Brief You's
 * navigation host, so Read You's files aren't involved.
 */
object BriefShortcuts {

    enum class Action(val id: String) {
        Triage("triage"),
        Gist("gist"),
        Unread("unread"),
        Sync("sync"),
    }

    private const val ACTION_PREFIX = "me.ash.reader.brief.shortcut."

    /** Triage or the gist, waiting for the Unread list to open (see BriefFlowOverlays). */
    var pending by mutableStateOf<Action?>(null)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun filterStateUseCase(): FilterStateUseCase

        fun rssService(): RssService
    }

    /** Publishes the shortcuts (on every start, so their labels follow the app's language). */
    fun publish(context: Context) {
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut(context, Action.Triage, R.string.shortcut_triage, R.drawable.ic_shortcut_triage, 0),
                    shortcut(context, Action.Gist, R.string.shortcut_gist, R.drawable.ic_shortcut_gist, 1),
                    shortcut(context, Action.Unread, R.string.shortcut_unread, R.drawable.ic_shortcut_unread, 2),
                    shortcut(context, Action.Sync, R.string.shortcut_sync, R.drawable.ic_shortcut_sync, 3),
                ),
            )
        }
    }

    private fun shortcut(context: Context, action: Action, label: Int, icon: Int, rank: Int) =
        ShortcutInfoCompat.Builder(context, action.id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setRank(rank)
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_PREFIX + action.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            .build()

    /** The shortcut an intent comes from, if any; the intent is cleared so it's only done once. */
    private fun take(intent: Intent?): Action? {
        val action = intent?.action?.takeIf { it.startsWith(ACTION_PREFIX) } ?: return null
        intent.action = null
        return Action.entries.firstOrNull { ACTION_PREFIX + it.id == action }
    }

    /**
     * Handles shortcut launches, the first one and later ones: Sync now syncs and stays on the
     * current screen; the others open the Unread list of everything (and then Triage or the gist).
     */
    @Composable
    fun Handler(backStack: NavBackStack<NavKey>) {
        val context = LocalContext.current
        val activity = context.findActivity() as? ComponentActivity ?: return
        DisposableEffect(activity) {
            val deps =
                EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
            fun handle(intent: Intent?) {
                val action = take(intent) ?: return
                // The first-start screen comes first.
                if (backStack.any { it == Route.Startup }) return
                when (action) {
                    Action.Sync -> {
                        deps.rssService().get().doSyncOneTime()
                        Toast.makeText(context, R.string.shortcut_syncing, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        deps.filterStateUseCase().init(feedId = null, groupId = null)
                        // Back to Feeds, then the list on top (where the list opens normally).
                        while (backStack.size > 1 && backStack.last() != Route.Feeds) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.lastOrNull() != Route.Feeds) {
                            backStack.clear()
                            backStack.add(Route.Feeds)
                        }
                        backStack.add(Route.Reading(articleId = null))
                        pending = action.takeIf { it == Action.Triage || it == Action.Gist }
                    }
                }
            }
            handle(activity.intent)
            val listener = Consumer<Intent> { handle(it) }
            activity.addOnNewIntentListener(listener)
            onDispose { activity.removeOnNewIntentListener(listener) }
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
