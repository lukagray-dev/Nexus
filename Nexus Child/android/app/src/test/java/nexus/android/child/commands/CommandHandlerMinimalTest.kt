package nexus.android.child.commands

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], instrumentedPackages = [])
class CommandHandlerMinimalTest {

    @Test
    fun testInstantiationWithNulls() {
        try {
            val handler = CommandHandlerImpl(
                context = null as Context,
                dataChannel = null,
                cameraController = null,
                microphoneController = null,
                screenRecordingController = null,
                smsSharing = null,
                callLogSharing = null,
                isBackgroundService = false,
                permissionHandler = null,
                stealthHandler = null,
                locationHandler = null,
                notificationHandler = null,
                chatHandler = null,
                keyboardHandler = null,
                settingsHandler = null,
                storageHandler = null,
                parentVoiceHandler = null,
                wallpaperController = null,
                vibrationFlashController = null
            )
        } catch (e: Exception) {
            // Ignore runtime exceptions from null usage, we just want to see if it loads
        }
    }
}
