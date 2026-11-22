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
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.flowSortUnreadArticles
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalSortUnreadArticles =
    compositionLocalOf<SortUnreadArticlesPreference> { SortUnreadArticlesPreference.default }

sealed class SortUnreadArticlesPreference(val value: Boolean) : Preference() {
    data object Latest : SortUnreadArticlesPreference(false)
    data object Earliest : SortUnreadArticlesPreference(true)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                flowSortUnreadArticles,
                value
            )
        }
    }

    @Composable
    fun description(): String {
        return when (this) {
            Earliest -> stringResource(R.string.earliest)
            Latest -> stringResource(R.string.latest)
        }
    }

    companion object {

        val default = Latest
        val values = listOf(Latest, Earliest)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[flowSortUnreadArticles]?.key as Preferences.Key<Boolean>]) {
                true -> Earliest
                false -> Latest
                else -> default
            }
    }
}