@file:Suppress("UNCHECKED_CAST")

package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.swipeEndAction
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.swipeStartAction
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

data object SwipeGestureActions {
    const val None = 0
    const val ToggleRead = 1
    const val ToggleStarred = 2
    const val Share = 3
    const val OpenInBrowser = 4
}

val LocalArticleListSwipeEndAction = compositionLocalOf { SwipeEndActionPreference.default }

sealed class SwipeEndActionPreference(val action: Int) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.swipeEndAction, action
            )
        }
    }

    data object None : SwipeEndActionPreference(SwipeGestureActions.None)
    data object ToggleRead : SwipeEndActionPreference(SwipeGestureActions.ToggleRead)
    data object ToggleStarred :
        SwipeEndActionPreference(SwipeGestureActions.ToggleStarred)
    data object Share : SwipeEndActionPreference(SwipeGestureActions.Share)
    data object OpenInBrowser : SwipeEndActionPreference(SwipeGestureActions.OpenInBrowser)

    val desc: String
        @Composable get() = when (this) {
            None -> stringResource(id = R.string.none)
            ToggleRead -> stringResource(id = R.string.toggle_read)
            ToggleStarred -> stringResource(id = R.string.toggle_starred)
            Share -> stringResource(id = R.string.share)
            OpenInBrowser -> stringResource(id = R.string.open_in_browser)
        }

    companion object {
        val default: SwipeEndActionPreference = ToggleRead
        val values = listOf(
            None,
            ToggleRead,
            ToggleStarred,
            Share,
            OpenInBrowser,
        )

        fun fromPreferences(preferences: Preferences): SwipeEndActionPreference {
            return when (preferences[DataStoreKey.keys[swipeEndAction]?.key as Preferences.Key<Int>]) {
                SwipeGestureActions.None -> None
                SwipeGestureActions.ToggleRead -> ToggleRead
                SwipeGestureActions.ToggleStarred -> ToggleStarred
                SwipeGestureActions.Share -> Share
                SwipeGestureActions.OpenInBrowser -> OpenInBrowser
                else -> default
            }
        }
    }
}

val LocalArticleListSwipeStartAction = compositionLocalOf { SwipeStartActionPreference.default }

sealed class SwipeStartActionPreference(val action: Int) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKey.swipeStartAction, action)
        }
    }

    data object None : SwipeStartActionPreference(SwipeGestureActions.None)
    data object ToggleRead : SwipeStartActionPreference(SwipeGestureActions.ToggleRead)
    data object ToggleStarred :
        SwipeStartActionPreference(SwipeGestureActions.ToggleStarred)
    data object Share : SwipeStartActionPreference(SwipeGestureActions.Share)
    data object OpenInBrowser : SwipeStartActionPreference(SwipeGestureActions.OpenInBrowser)

    val desc: String
        @Composable get() = when (this) {
            None -> stringResource(id = R.string.none)
            ToggleRead -> stringResource(id = R.string.toggle_read)
            ToggleStarred -> stringResource(id = R.string.toggle_starred)
            Share -> stringResource(id = R.string.share)
            OpenInBrowser -> stringResource(id = R.string.open_in_browser)
        }

    companion object {
        val default: SwipeStartActionPreference = ToggleStarred
        val values = listOf(
            None,
            ToggleRead,
            ToggleStarred,
            Share,
            OpenInBrowser,
        )

        fun fromPreferences(preferences: Preferences): SwipeStartActionPreference {
            return when (preferences[DataStoreKey.keys[swipeStartAction]?.key as Preferences.Key<Int>]) {
                SwipeGestureActions.None -> None
                SwipeGestureActions.ToggleRead -> ToggleRead
                SwipeGestureActions.ToggleStarred -> ToggleStarred
                SwipeGestureActions.Share -> Share
                SwipeGestureActions.OpenInBrowser -> OpenInBrowser
                else -> default
            }
        }
    }
}
