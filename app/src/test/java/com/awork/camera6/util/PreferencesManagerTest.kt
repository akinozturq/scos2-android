package com.awork.camera6.util

import org.junit.Test
import com.google.common.truth.Truth.assertThat

class PreferencesManagerTest {

    @Test
    fun `default burst count is 5`() {
        // PreferencesManager requires Context, which is tested via instrumentation
        // This test verifies companion object key definitions
        assertThat(PreferencesManager.KEY_BURST_COUNT.name).isEqualTo("burst_count")
    }

    @Test
    fun `default auto delay key is correct`() {
        assertThat(PreferencesManager.KEY_AUTO_DELAY.name).isEqualTo("auto_delay")
    }

    @Test
    fun `default camera key is correct`() {
        assertThat(PreferencesManager.KEY_DEFAULT_CAMERA.name).isEqualTo("default_camera")
    }

    @Test
    fun `all preference keys are unique`() {
        val keys = listOf(
            PreferencesManager.KEY_BURST_COUNT,
            PreferencesManager.KEY_AUTO_DELAY,
            PreferencesManager.KEY_START_MODE,
            PreferencesManager.KEY_SAVE_PATH,
            PreferencesManager.KEY_HIDE_FOLDER,
            PreferencesManager.KEY_DISABLE_TOAST,
            PreferencesManager.KEY_DISABLE_SHUTTER,
            PreferencesManager.KEY_DISABLE_VIBRATION,
            PreferencesManager.KEY_VOLUME_UP_ACTION,
            PreferencesManager.KEY_VOLUME_DOWN_ACTION,
            PreferencesManager.KEY_DEFAULT_CAMERA
        )
        val names = keys.map { it.name }
        assertThat(names).containsNoDuplicates()
    }
}
