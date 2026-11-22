package com.ay.briefyou.brief.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ay.briefyou.ui.ext.DEFAULT_GROUP_SALT
import com.ay.briefyou.ui.ext.OLD_DEFAULT_GROUP_SALT
import com.ay.briefyou.ui.ext.dataStore
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Once, after the default group's id changed from `<account>$read_you_app_default_group` to
 * `<account>$default_group` (the database part is MIGRATION_9_10): everything Brief You saved by that
 * id in its settings and preference files (group icons and pictures, filters, order, the Triage
 * and Feeds settings) is moved to the new id, in keys and in saved text.
 */
object GroupIdMigration {
    private const val OLD = "$$OLD_DEFAULT_GROUP_SALT"
    private const val NEW = "$$DEFAULT_GROUP_SALT"

    fun runOnce(context: Context) {
        val done = context.getSharedPreferences("brief_migration", Context.MODE_PRIVATE)
        if (done.getBoolean("default_group_salt", false)) return
        runCatching { sharedPreferences(context) }
        runCatching { runBlocking { moveDataStoreKeys(context) } }
        done.edit().putBoolean("default_group_salt", true).apply()
    }

    private fun sharedPreferences(context: Context) {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles { f -> f.name.endsWith(".xml") }?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            if (name == "brief_migration") return@forEach
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val all = prefs.all
            if (all.none { (k, v) -> k.contains(OLD) || (v is String && v.contains(OLD)) || (v is Set<*> && v.any { it is String && it.contains(OLD) }) }) return@forEach
            val edit = prefs.edit()
            for ((key, value) in all) {
                val newKey = key.replace(OLD, NEW)
                val newValue: Any? =
                    when (value) {
                        is String -> value.replace(OLD, NEW)
                        is Set<*> -> value.filterIsInstance<String>().map { it.replace(OLD, NEW) }.toSet()
                        else -> value
                    }
                if (newKey == key && newValue == value) continue
                edit.remove(key)
                when (newValue) {
                    is String -> edit.putString(newKey, newValue)
                    is Boolean -> edit.putBoolean(newKey, newValue)
                    is Int -> edit.putInt(newKey, newValue)
                    is Long -> edit.putLong(newKey, newValue)
                    is Float -> edit.putFloat(newKey, newValue)
                    is Set<*> -> edit.putStringSet(newKey, newValue.filterIsInstance<String>().toSet())
                }
            }
            edit.commit()
        }
    }

    private suspend fun moveDataStoreKeys(context: Context) {
        context.dataStore.edit { prefs ->
            for ((key, value) in prefs.asMap().toMap()) {
                val newName = key.name.replace(OLD, NEW)
                val newValue: Any =
                    when (value) {
                        is String -> value.replace(OLD, NEW)
                        is Set<*> -> value.filterIsInstance<String>().map { it.replace(OLD, NEW) }.toSet()
                        else -> value
                    }
                if (newName == key.name && newValue == value) continue
                prefs.remove(key)
                when (newValue) {
                    is String -> prefs[stringPreferencesKey(newName)] = newValue
                    is Boolean -> prefs[booleanPreferencesKey(newName)] = newValue
                    is Int -> prefs[intPreferencesKey(newName)] = newValue
                    is Long -> prefs[longPreferencesKey(newName)] = newValue
                    is Float -> prefs[floatPreferencesKey(newName)] = newValue
                    is Double -> prefs[doublePreferencesKey(newName)] = newValue
                    is Set<*> -> prefs[stringSetPreferencesKey(newName)] = newValue.filterIsInstance<String>().toSet()
                }
            }
        }
    }
}
