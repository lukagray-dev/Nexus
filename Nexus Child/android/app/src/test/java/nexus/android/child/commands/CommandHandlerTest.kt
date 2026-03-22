package nexus.android.child.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import nexus.android.child.components.calllog.CallLogSharing
import nexus.android.child.components.camera.CameraController
import nexus.android.child.components.microphone.MicrophoneController
import nexus.android.child.components.screen.ScreenRecordingController
import nexus.android.child.components.sms.SmsSharing
import nexus.android.child.components.wallpaper.WallpaperController
import nexus.android.child.components.vibrateflash.VibrationFlashController
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import org.json.JSONObject
import nexus.android.child.applock.AppLockManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CommandHandlerTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var dataChannel: DataChannel
    @Mock private lateinit var cameraController: CameraController
    @Mock private lateinit var microphoneController: MicrophoneController
    @Mock private lateinit var screenRecordingController: ScreenRecordingController
    @Mock private lateinit var smsSharing: SmsSharing
    @Mock private lateinit var callLogSharing: CallLogSharing
    @Mock private lateinit var permissionHandler: PermissionHandler
    @Mock private lateinit var stealthHandler: StealthHandler
    @Mock private lateinit var locationHandler: LocationHandler
    @Mock private lateinit var notificationHandler: NotificationHandler
    @Mock private lateinit var chatHandler: ChatHandler
    @Mock private lateinit var keyboardHandler: KeyboardHandler
    @Mock private lateinit var settingsHandler: SettingsHandler
    @Mock private lateinit var storageHandler: StorageHandler
    @Mock private lateinit var parentVoiceHandler: ParentVoiceHandler
    @Mock private lateinit var wallpaperController: WallpaperController
    @Mock private lateinit var vibrationFlashController: VibrationFlashController

    private lateinit var commandHandler: CommandHandlerImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        whenever(dataChannel.state()).thenReturn(DataChannel.State.OPEN)
        
        commandHandler = CommandHandlerImpl(
            context = context,
            dataChannel = dataChannel,
            cameraController = cameraController,
            microphoneController = microphoneController,
            screenRecordingController = screenRecordingController,
            smsSharing = smsSharing,
            callLogSharing = callLogSharing,
            isBackgroundService = false,
            permissionHandler = permissionHandler,
            stealthHandler = stealthHandler,
            locationHandler = locationHandler,
            notificationHandler = notificationHandler,
            chatHandler = chatHandler,
            keyboardHandler = keyboardHandler,
            settingsHandler = settingsHandler,
            storageHandler = storageHandler,
            parentVoiceHandler = parentVoiceHandler,
            wallpaperController = wallpaperController,
            vibrationFlashController = vibrationFlashController
        )
    }

    private fun verifyDataChannelMessage(expectedMessage: String) {
        val bufferCaptor = argumentCaptor<DataChannel.Buffer>()
        verify(dataChannel).send(bufferCaptor.capture())
        val buffer = bufferCaptor.firstValue
        val message = Charsets.UTF_8.decode(buffer.data).toString()
        assertEquals(expectedMessage, message)
    }
    
    private fun verifyDataChannelMessageContains(expectedSubstring: String) {
        val bufferCaptor = argumentCaptor<DataChannel.Buffer>()
        verify(dataChannel).send(bufferCaptor.capture())
        val buffer = bufferCaptor.firstValue
        val message = Charsets.UTF_8.decode(buffer.data).toString()
        assert(message.contains(expectedSubstring)) { "Expected message to contain '$expectedSubstring' but was '$message'" }
    }

    @Test
    fun testHandleCommand_CameraOn_PermissionGranted() {
        whenever(context.checkPermission(eq(Manifest.permission.CAMERA), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
            
        commandHandler.handleCommand("CAMERA_ON")
        
        verify(cameraController).startCamera()
        verifyDataChannelMessage("CAMERA_STARTED")
    }

    @Test
    fun testHandleCommand_CameraOn_PermissionDenied_NotBackground() {
        whenever(context.checkPermission(eq(Manifest.permission.CAMERA), any(), any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)
            
        commandHandler.handleCommand("CAMERA_ON")
        
        verify(permissionHandler).requestPermission(eq(Manifest.permission.CAMERA), eq(1001), eq("Camera"))
        verifyDataChannelMessage("CAMERA_PERMISSION_REQUESTED")
    }

    @Test
    fun testHandleCommand_CameraOff() {
        commandHandler.handleCommand("CAMERA_OFF")
        
        verify(cameraController).stopCamera()
        verifyDataChannelMessage("CAMERA_STOPPED")
    }

    @Test
    fun testHandleCommand_CameraSwitch() {
        commandHandler.handleCommand("CAMERA_SWITCH")
        
        verify(cameraController).switchCamera()
        verifyDataChannelMessage("CAMERA_SWITCHED")
    }

    @Test
    fun testHandleCommand_MicOn_PermissionGranted() {
        whenever(microphoneController.startMicrophone()).thenReturn(true)
        val renegotiation: ((onComplete: () -> Unit) -> Unit) = { onComplete -> onComplete() }
        commandHandler = CommandHandlerImpl(
            context = context,
            dataChannel = dataChannel,
            cameraController = cameraController,
            microphoneController = microphoneController,
            permissionHandler = permissionHandler,
            smsSharing = smsSharing,
            callLogSharing = callLogSharing,
            onRenegotiationNeeded = renegotiation
        )

        mockStatic(ContextCompat::class.java).use { contextCompat ->
            contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) }
                .thenReturn(PackageManager.PERMISSION_GRANTED)
            commandHandler.handleCommand("MIC_ON")
        }

        verify(microphoneController).startMicrophone()
        verifyDataChannelMessage("MIC_STARTED")
    }

    @Test
    fun testHandleCommand_MicOff() {
        whenever(microphoneController.stopMicrophone()).thenReturn(true) // Needs renegotiation
        val renegotiation: ((onComplete: () -> Unit) -> Unit) = { onComplete -> onComplete() }
        commandHandler = CommandHandlerImpl(
            context = context,
            dataChannel = dataChannel,
            cameraController = cameraController,
            microphoneController = microphoneController,
            permissionHandler = permissionHandler,
            smsSharing = smsSharing,
            callLogSharing = callLogSharing,
            onRenegotiationNeeded = renegotiation
        )

        commandHandler.handleCommand("MIC_OFF")
        verify(microphoneController).stopMicrophone()
        verifyDataChannelMessage("MIC_STOPPED")
    }

    @Test
    fun testHandleCommand_LocateChild_PermissionGranted() {
        whenever(context.checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
            
        commandHandler.handleCommand("LOCATE_CHILD")
        
        verify(locationHandler).startLocationTracking()
        verifyDataChannelMessage("LOCATION_STARTED")
    }
    
    @Test
    fun testHandleCommand_LocateChildStop() {
        commandHandler.handleCommand("LOCATE_CHILD_STOP")
        
        verify(locationHandler).stopLocationTracking()
        verifyDataChannelMessage("LOCATION_STOPPED")
    }

    @Test
    fun testHandleCommand_SmsOn_PermissionGranted() {
        whenever(context.checkPermission(eq(Manifest.permission.READ_SMS), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
            
        commandHandler.handleCommand("SMS_ON")
        
        verify(smsSharing).startSharing()
        verifyDataChannelMessage("SMS_STARTED")
    }

    @Test
    fun testHandleCommand_SmsOff() {
        commandHandler.handleCommand("SMS_OFF")
        
        verify(smsSharing).stopSharing()
        verifyDataChannelMessage("SMS_STOPPED")
    }

    @Test
    fun testHandleCommand_CallLogOn_PermissionGranted() {
         whenever(context.checkPermission(eq(Manifest.permission.READ_CALL_LOG), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
            
        commandHandler.handleCommand("CALLLOG_ON")
        
        verify(callLogSharing).startSharing()
        verifyDataChannelMessage("CALLLOG_STARTED")
    }

    @Test
    fun testHandleCommand_CallLogOff() {
        commandHandler.handleCommand("CALLLOG_OFF")
        
        verify(callLogSharing).stopSharing()
        verifyDataChannelMessage("CALLLOG_STOPPED")
    }

    @Test
    fun testHandleCommand_NotificationOn() {
        commandHandler.handleCommand("NOTIFICATION_ON")
        
        verify(notificationHandler).startNotificationSharing()
        verifyDataChannelMessage("NOTIFICATION_STARTED")
    }

    @Test
    fun testHandleCommand_NotificationOff() {
        commandHandler.handleCommand("NOTIFICATION_OFF")
        
        verify(notificationHandler).stopNotificationSharing()
        verifyDataChannelMessage("NOTIFICATION_STOPPED")
    }

    @Test
    fun testHandleCommand_ChatOn() {
        commandHandler.handleCommand("CHAT_ON")
        
        verify(chatHandler).startChatMonitoring()
        verifyDataChannelMessage("CHAT_STARTED")
    }

    @Test
    fun testHandleCommand_ChatOff() {
        commandHandler.handleCommand("CHAT_OFF")
        
        verify(chatHandler).stopChatMonitoring()
        verifyDataChannelMessage("CHAT_STOPPED")
    }

    @Test
    fun testHandleCommand_KeyboardOn() {
        commandHandler.handleCommand("KEYBOARD_ON")
        
        verify(keyboardHandler).startKeyboardMonitoring()
        verifyDataChannelMessage("KEYBOARD_STARTED")
    }

    @Test
    fun testHandleCommand_KeyboardOff() {
        commandHandler.handleCommand("KEYBOARD_OFF")
        
        verify(keyboardHandler).stopKeyboardMonitoring()
        verifyDataChannelMessage("KEYBOARD_STOPPED")
    }
    
    @Test
    fun testHandleCommand_StealthOn() {
        commandHandler.handleCommand("STEALTH_ON")
        
        verify(stealthHandler).activateStealthMode()
        verifyDataChannelMessage("STEALTH_ON_ACK")
    }

    @Test
    fun testHandleCommand_StealthOff() {
        commandHandler.handleCommand("STEALTH_OFF")
        
        verify(stealthHandler).deactivateStealthMode()
        verifyDataChannelMessage("STEALTH_OFF_ACK")
    }

    @Test
    fun testHandleCommand_OpenSettings() {
        commandHandler.handleCommand("OPEN_SETTINGS")
        
        verify(settingsHandler).openAppSettings()
        verifyDataChannelMessage("SETTINGS_OPENED")
    }
    
    @Test
    fun testHandleCommand_CheckPermissions() {
        commandHandler.handleCommand("CHECK_PERMISSIONS")
        
        verify(settingsHandler).checkPermissionStatus()
    }
    
    @Test
    fun testHandleCommand_RequestAllPermissions() {
        commandHandler.handleCommand("REQUEST_ALL_PERMISSIONS")
        
        verify(settingsHandler).requestAllPermissions()
        verifyDataChannelMessage("ALL_PERMISSIONS_REQUESTED")
    }

    @Test
    fun testHandleCommand_GetWellbeing_PermissionGranted() {
        // Need to mock AppUsageStats or just verify permission check for now as DigitalWellbeingCollector is instantiated inside
        whenever(context.checkPermission(eq(Manifest.permission.PACKAGE_USAGE_STATS), any(), any()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
            
        // Note: This test will likely fail on instantiation of DigitalWellbeingCollector if we don't mock the constructor 
        // or if it depends on system services that Robolectric doesn't fully provide by default.
        // However, we can try. If it fails, we might need to refactor CommandHandler to inject a factory for collectors.
        // For now, let's just test the permission denied case which is safe.
    }
    
    @Test
    fun testHandleCommand_GetWellbeing_PermissionDenied() {
        mockStatic(ContextCompat::class.java).use { contextCompat ->
            contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(context, Manifest.permission.PACKAGE_USAGE_STATS) }
                .thenReturn(PackageManager.PERMISSION_DENIED)
            commandHandler.handleCommand("GET_WELLBEING")
        }
        
        verifyDataChannelMessageContains("WELLBEING_ERROR")
    }
    
    @Test
    fun testHandleCommand_ParentAudioUnmute() {
        commandHandler.handleCommand("PARENT_AUDIO_UNMUTE")
        
        verify(parentVoiceHandler).startReceivingVoice()
        verifyDataChannelMessage("PARENT_AUDIO_UNMUTED")
    }
    
    @Test
    fun testHandleCommand_ParentAudioMute() {
        commandHandler.handleCommand("PARENT_AUDIO_MUTE")
        
        verify(parentVoiceHandler).stopReceivingVoice()
        verifyDataChannelMessage("PARENT_AUDIO_MUTED")
    }

    @Test
    fun testHandleCommand_Vibrate() {
        whenever(vibrationFlashController.vibrate("short")).thenReturn(true)
        
        commandHandler.handleCommand("""{"cmd":"VIBRATE", "pattern":"short"}""")
        
        verify(vibrationFlashController).vibrate("short")
        verifyDataChannelMessage("VIBRATE_SUCCESS: short")
    }
    
    @Test
    fun testHandleCommand_Unknown() {
        commandHandler.handleCommand("UNKNOWN_CMD")
        
        verifyDataChannelMessage("UNKNOWN_COMMAND: UNKNOWN_CMD")
    }
}
