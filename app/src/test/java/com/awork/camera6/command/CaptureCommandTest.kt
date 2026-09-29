package com.awork.camera6.command

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureCommandTest {

    @Test
    fun `all commands have valid bidirectional action mappings`() {
        for (cmd in CaptureCommand.values()) {
            val action = cmd.toAction()
            assertThat(action).startsWith("com.awork.camera6.action.")
            val parsed = CaptureCommand.fromAction(action)
            assertThat(parsed).isEqualTo(cmd)
        }
    }

    @Test
    fun `preference action mappings correctly map known keys`() {
        assertThat(CaptureCommand.fromPreferenceAction("capture")).isEqualTo(CaptureCommand.SINGLE_CAPTURE)
        assertThat(CaptureCommand.fromPreferenceAction("burst")).isEqualTo(CaptureCommand.BURST_CAPTURE)
        assertThat(CaptureCommand.fromPreferenceAction("auto")).isEqualTo(CaptureCommand.AUTO_CAPTURE)
        assertThat(CaptureCommand.fromPreferenceAction("video")).isEqualTo(CaptureCommand.RECORD_VIDEO)
        assertThat(CaptureCommand.fromPreferenceAction("black")).isEqualTo(CaptureCommand.BLACK_MODE)
        assertThat(CaptureCommand.fromPreferenceAction("none")).isNull()
    }
}
