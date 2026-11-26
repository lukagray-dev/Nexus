package com.nexus.parent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.ui.theme.NexusParentTheme
import org.junit.Rule
import org.junit.Test

/**
 * NexusUiFlowsTest.kt - Compose UI instrumentation tests verifying core user journeys.
 */
class NexusUiFlowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pairing_drawer_camera_mic_and_settings_flows_work_end_to_end() {
        val stateHolder = ParentStateHolder()

        composeRule.setContent {
            NexusParentTheme {
                NexusParentRoot(stateHolder = stateHolder)
            }
        }

        // 1. Pairing screen starts with Connect disabled until 12 digits entered
        composeRule.onNodeWithText("Connect Child Device").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_connect_btn").assertIsNotEnabled()

        composeRule.onNodeWithTag("pairing_id_input").performTextInput("482194021104")
        composeRule.onNodeWithTag("pairing_connect_btn").assertIsEnabled().performClick()

        // 2. Phase 2 Connected Dashboard appears
        composeRule.onNodeWithText("Device Connected & Monitoring Ready").assertIsDisplayed()

        // 3. Open Push Drawer and select Camera Feed
        composeRule.onNodeWithTag("capsule_menu_btn").performClick()
        composeRule.onNodeWithTag("drawer_item_camera").performClick()
        composeRule.onNodeWithText("LENS: REAR").assertIsDisplayed()

        // 4. Open Floating Mic HUD over Camera Feed
        composeRule.onNodeWithTag("capsule_mic_btn").performClick()
        composeRule.onNodeWithTag("floating_mic_hud").assertIsDisplayed()
        composeRule.onNodeWithTag("mic_hud_close_btn").performClick()

        // 5. Open Settings, switch tabs, and close
        composeRule.onNodeWithTag("capsule_settings_btn").performClick()
        composeRule.onNodeWithTag("settings_tab_surveillance").performClick()
        composeRule.onNodeWithText("Rolling DVR Buffer Duration").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_done_btn").performClick()
    }
}
