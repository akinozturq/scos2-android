package com.awork.camera6

import org.junit.Test
import com.google.common.truth.Truth.assertThat

class SpyCamServiceTest {

    @Test
    fun `action constants have correct prefix`() {
        val actions = listOf(
            SpyCamService.ACTION_CAPTURE_SINGLE,
            SpyCamService.ACTION_CAPTURE_BURST,
            SpyCamService.ACTION_CAPTURE_AUTO,
            SpyCamService.ACTION_CAPTURE_FACE,
            SpyCamService.ACTION_RECORD_VIDEO,
            SpyCamService.ACTION_STOP_RECORDING,
            SpyCamService.ACTION_SWITCH_CAMERA,
            SpyCamService.ACTION_SHOW_OVERLAY,
            SpyCamService.ACTION_HIDE_OVERLAY,
            SpyCamService.ACTION_TOGGLE_OVERLAY,
            SpyCamService.ACTION_BLACK_MODE,
            SpyCamService.ACTION_EXIT
        )
        actions.forEach { action ->
            assertThat(action).startsWith("com.awork.camera6.action.")
        }
    }

    @Test
    fun `all action constants are unique`() {
        val actions = listOf(
            SpyCamService.ACTION_CAPTURE_SINGLE,
            SpyCamService.ACTION_CAPTURE_BURST,
            SpyCamService.ACTION_CAPTURE_AUTO,
            SpyCamService.ACTION_CAPTURE_FACE,
            SpyCamService.ACTION_RECORD_VIDEO,
            SpyCamService.ACTION_STOP_RECORDING,
            SpyCamService.ACTION_SWITCH_CAMERA,
            SpyCamService.ACTION_SHOW_OVERLAY,
            SpyCamService.ACTION_HIDE_OVERLAY,
            SpyCamService.ACTION_TOGGLE_OVERLAY,
            SpyCamService.ACTION_BLACK_MODE,
            SpyCamService.ACTION_EXIT
        )
        assertThat(actions).containsNoDuplicates()
    }

    @Test
    fun `notification ID is positive`() {
        assertThat(SpyCamService.NOTIFICATION_ID).isGreaterThan(0)
    }
}
