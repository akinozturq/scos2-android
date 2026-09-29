package com.awork.camera6.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.awork.camera6.ui.theme.SCOSTheme
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_showsStartButton_whenServiceNotRunning() {
        composeTestRule.setContent {
            SCOSTheme {
                MainScreen()
            }
        }
        composeTestRule.onNodeWithText("Start Camera Service").assertIsDisplayed()
    }

    @Test
    fun mainScreen_showsAppTitle() {
        composeTestRule.setContent {
            SCOSTheme {
                MainScreen()
            }
        }
        composeTestRule.onNodeWithText("Spy Camera OS").assertIsDisplayed()
    }
}
