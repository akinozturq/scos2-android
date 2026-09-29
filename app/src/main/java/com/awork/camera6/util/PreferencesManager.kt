package com.awork.camera6.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scos_prefs")

class PreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<Preferences.Key<*>, Any>()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                memoryCache.putAll(prefs.asMap())
            }
        }
    }

    // Fast memory-cached property accessors (non-blocking disk persistence)
    var burstCount: Int
        get() = getSync(KEY_BURST_COUNT, 5)
        set(value) = putSync(KEY_BURST_COUNT, value)

    var autoDelay: Int
        get() = getSync(KEY_AUTO_DELAY, 3)
        set(value) = putSync(KEY_AUTO_DELAY, value)

    var startMode: String
        get() = getSync(KEY_START_MODE, "normal")
        set(value) = putSync(KEY_START_MODE, value)

    var savePath: String
        get() = getSync(KEY_SAVE_PATH, "")
        set(value) = putSync(KEY_SAVE_PATH, value)

    var hideFolder: Boolean
        get() = getSync(KEY_HIDE_FOLDER, false)
        set(value) = putSync(KEY_HIDE_FOLDER, value)

    var disableToast: Boolean
        get() = getSync(KEY_DISABLE_TOAST, false)
        set(value) = putSync(KEY_DISABLE_TOAST, value)

    var disableShutter: Boolean
        get() = getSync(KEY_DISABLE_SHUTTER, true)
        set(value) = putSync(KEY_DISABLE_SHUTTER, value)

    var disableVibration: Boolean
        get() = getSync(KEY_DISABLE_VIBRATION, false)
        set(value) = putSync(KEY_DISABLE_VIBRATION, value)

    var volumeUpAction: String
        get() = getSync(KEY_VOLUME_UP_ACTION, "capture")
        set(value) = putSync(KEY_VOLUME_UP_ACTION, value)

    var volumeDownAction: String
        get() = getSync(KEY_VOLUME_DOWN_ACTION, "video")
        set(value) = putSync(KEY_VOLUME_DOWN_ACTION, value)

    var defaultCamera: String
        get() = getSync(KEY_DEFAULT_CAMERA, "back")
        set(value) = putSync(KEY_DEFAULT_CAMERA, value)

    // Flow-based accessors (for reactive Compose UI with distinct emissions)
    fun burstCountFlow(): Flow<Int> = observe(KEY_BURST_COUNT, 5)
    fun autoDelayFlow(): Flow<Int> = observe(KEY_AUTO_DELAY, 3)
    fun startModeFlow(): Flow<String> = observe(KEY_START_MODE, "normal")
    fun savePathFlow(): Flow<String> = observe(KEY_SAVE_PATH, "")
    fun hideFolderFlow(): Flow<Boolean> = observe(KEY_HIDE_FOLDER, false)
    fun defaultCameraFlow(): Flow<String> = observe(KEY_DEFAULT_CAMERA, "back")
    fun disableToastFlow(): Flow<Boolean> = observe(KEY_DISABLE_TOAST, false)
    fun disableShutterFlow(): Flow<Boolean> = observe(KEY_DISABLE_SHUTTER, true)
    fun disableVibrationFlow(): Flow<Boolean> = observe(KEY_DISABLE_VIBRATION, false)
    fun volumeUpActionFlow(): Flow<String> = observe(KEY_VOLUME_UP_ACTION, "capture")
    fun volumeDownActionFlow(): Flow<String> = observe(KEY_VOLUME_DOWN_ACTION, "video")

    // Generic helpers
    private fun <T> observe(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data
            .map { prefs -> prefs[key] ?: default }
            .distinctUntilChanged()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getSync(key: Preferences.Key<T>, default: T): T {
        val cached = memoryCache[key]
        if (cached != null) {
            return cached as T
        }
        return try {
            runBlocking(Dispatchers.IO) {
                val pref = dataStore.data.first()[key] ?: default
                memoryCache[key] = pref as Any
                pref
            }
        } catch (e: Exception) {
            default
        }
    }

    private fun <T> putSync(key: Preferences.Key<T>, value: T) {
        memoryCache[key] = value as Any
        scope.launch {
            try {
                dataStore.edit { prefs -> prefs[key] = value }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        memoryCache[key] = value as Any
        dataStore.edit { prefs -> prefs[key] = value }
    }

    companion object {
        val KEY_BURST_COUNT = intPreferencesKey("burst_count")
        val KEY_AUTO_DELAY = intPreferencesKey("auto_delay")
        val KEY_START_MODE = stringPreferencesKey("start_mode")
        val KEY_SAVE_PATH = stringPreferencesKey("save_path")
        val KEY_HIDE_FOLDER = booleanPreferencesKey("hide_folder")
        val KEY_DISABLE_TOAST = booleanPreferencesKey("disable_toast")
        val KEY_DISABLE_SHUTTER = booleanPreferencesKey("disable_shutter")
        val KEY_DISABLE_VIBRATION = booleanPreferencesKey("disable_vibration")
        val KEY_VOLUME_UP_ACTION = stringPreferencesKey("volume_up_action")
        val KEY_VOLUME_DOWN_ACTION = stringPreferencesKey("volume_down_action")
        val KEY_DEFAULT_CAMERA = stringPreferencesKey("default_camera")
    }
}
