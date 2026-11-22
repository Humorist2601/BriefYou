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
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.flowArticleListReadIndicator
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalFlowArticleListReadIndicator =
    compositionLocalOf<FlowArticleReadIndicatorPreference> { FlowArticleReadIndicatorPreference.default }

sealed class FlowArticleReadIndicatorPreference(val value: Int) : Preference() {
    data object ExcludingStarred : FlowArticleReadIndicatorPreference(0)
    data object AllRead : FlowArticleReadIndicatorPreference(1)
    data object None : FlowArticleReadIndicatorPreference(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                flowArticleListReadIndicator,
                value
            )
        }
    }

    val description: String
        @Composable get() {
            return when (this) {
                AllRead -> stringResource(id = R.string.all_read)
                ExcludingStarred -> stringResource(id = R.string.read_excluding_starred)
                None -> stringResource(id = R.string.none)
            }
        }

    companion object {

        val default = ExcludingStarred
        val values = listOf(ExcludingStarred, AllRead, None)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[flowArticleListReadIndicator]?.key as Preferences.Key<Int>]) {
                0 -> ExcludingStarred
                1 -> AllRead
                2 -> None
                else -> default
            }

    }
}