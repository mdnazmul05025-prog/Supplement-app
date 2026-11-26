package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "supplement_prefs")

class PreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_SEEN_UPDATE_MILLIS = longPreferencesKey("last_seen_update_millis")
    }

    private val defaultLanguage: AppLanguage =
        if (Locale.getDefault().language.equals("bn", ignoreCase = true)) {
            AppLanguage.BENGALI
        } else {
            AppLanguage.ENGLISH
        }

    val languageFlow: Flow<AppLanguage> = appContext.dataStore.data.map { prefs ->
        when (prefs[Keys.LANGUAGE]) {
            AppLanguage.BENGALI.code -> AppLanguage.BENGALI
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> defaultLanguage
        }
    }

    val notificationsEnabledFlow: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: false
    }

    val lastSeenUpdateMillisFlow: Flow<Long> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SEEN_UPDATE_MILLIS] ?: 0L
    }

    suspend fun setLanguage(language: AppLanguage) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun markUpdatesSeen(timestampMillis: Long) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.LAST_SEEN_UPDATE_MILLIS] ?: 0L
            if (timestampMillis > current) {
                prefs[Keys.LAST_SEEN_UPDATE_MILLIS] = timestampMillis
            }
        }
    }
}
