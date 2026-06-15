package com.charles.ollama.client.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncEnabled = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ENABLED, false))
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_AT, 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _lastError = MutableStateFlow(prefs.getString(KEY_LAST_ERROR, null))
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentUid = MutableStateFlow(prefs.getString(KEY_CURRENT_UID, null))
    val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        _syncEnabled.value = enabled
    }

    fun setCurrentUid(uid: String?) {
        prefs.edit().putString(KEY_CURRENT_UID, uid).apply()
        _currentUid.value = uid
    }

    fun markSynced(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, timestamp)
            .remove(KEY_LAST_ERROR)
            .apply()
        _lastSyncAt.value = timestamp
        _lastError.value = null
    }

    fun markError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
        _lastError.value = message
    }

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_CURRENT_UID = "current_uid"
    }
}
