package com.ay.briefyou.domain.model.account

import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import com.ay.briefyou.infrastructure.preference.SyncOnlyOnWiFiPreference

/**
 * Provide [TypeConverter] of [SyncOnlyOnWiFiPreference] for [RoomDatabase].
 */
class SyncOnlyOnWiFiConverters {

    @TypeConverter
    fun toSyncOnlyOnWiFi(syncOnlyOnWiFi: Boolean): SyncOnlyOnWiFiPreference {
        return SyncOnlyOnWiFiPreference.values.find { it.value == syncOnlyOnWiFi } ?: SyncOnlyOnWiFiPreference.default
    }

    @TypeConverter
    fun fromSyncOnlyOnWiFi(syncOnlyOnWiFi: SyncOnlyOnWiFiPreference): Boolean {
        return syncOnlyOnWiFi.value
    }
}
