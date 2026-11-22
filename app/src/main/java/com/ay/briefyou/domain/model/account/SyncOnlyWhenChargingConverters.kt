package com.ay.briefyou.domain.model.account

import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import com.ay.briefyou.infrastructure.preference.SyncOnlyWhenChargingPreference

/**
 * Provide [TypeConverter] of [SyncOnlyWhenChargingPreference] for [RoomDatabase].
 */
class SyncOnlyWhenChargingConverters {

    @TypeConverter
    fun toSyncOnlyWhenCharging(syncOnlyWhenCharging: Boolean): SyncOnlyWhenChargingPreference {
        return SyncOnlyWhenChargingPreference.values.find { it.value == syncOnlyWhenCharging }
            ?: SyncOnlyWhenChargingPreference.default
    }

    @TypeConverter
    fun fromSyncOnlyWhenCharging(syncOnlyWhenCharging: SyncOnlyWhenChargingPreference): Boolean {
        return syncOnlyWhenCharging.value
    }
}
