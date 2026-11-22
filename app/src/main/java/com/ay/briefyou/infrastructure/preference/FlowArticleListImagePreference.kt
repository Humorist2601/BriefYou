@file:Suppress("UNCHECKED_CAST")

package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.flowArticleListImage
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalFlowArticleListImage =
    compositionLocalOf<FlowArticleListImagePreference> { FlowArticleListImagePreference.default }

sealed class FlowArticleListImagePreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListImagePreference(true)
    object OFF : FlowArticleListImagePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.flowArticleListImage,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[flowArticleListImage]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListImagePreference.not(): FlowArticleListImagePreference =
    when (value) {
        true -> FlowArticleListImagePreference.OFF
        false -> FlowArticleListImagePreference.ON
    }
