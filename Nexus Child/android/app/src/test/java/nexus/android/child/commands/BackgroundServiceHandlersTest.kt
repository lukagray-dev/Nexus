package nexus.android.child.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests for BackgroundService command handlers.
 * Tests permission, stealth, and settings handlers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundServiceHandlersTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock()
    }

    // ========== BackgroundServicePermissionHandler Tests ==========

    @Test
    fun testPermissionHandler_requestPermission_logsWarning() {
        // Given
        val handler = BackgroundServicePermissionHandler()

        // When/Then - should not throw, just log
        handler.requestPermission(Manifest.permission.CAMERA, 100, "Camera")
    }

    @Test
    fun testPermissionHandler_multipleRequests_doesNotCrash() {
        // Given
        val handler = BackgroundServicePermissionHandler()

        // When/Then - should handle multiple requests
        handler.requestPermission(Manifest.permission.CAMERA, 100, "Camera")
        handler.requestPermission(Manifest.permission.RECORD_AUDIO, 101, "Microphone")
        handler.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, 102, "Location")
    }

    // ========== BackgroundServiceExtendedStealthHandler Tests ==========

    @Test
    fun testStealthHandler_activateStealthMode_setsFlag() {
        // Given
        val stealthActivated = AtomicBoolean(false)
        val onActivate: () -> Unit = mock()
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onActivate = onActivate
        )

        // When
        handler.activateStealthMode()

        // Then
        assertTrue(stealthActivated.get())
        verify(onActivate).invoke()
    }

    @Test
    fun testStealthHandler_deactivateStealthMode_clearsFlag() {
        // Given
        val stealthActivated = AtomicBoolean(true)
        val onDeactivate: () -> Unit = mock()
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onDeactivate = onDeactivate
        )

        // When
        handler.deactivateStealthMode()

        // Then
        assertFalse(stealthActivated.get())
        verify(onDeactivate).invoke()
    }

    @Test
    fun testStealthHandler_activateWithoutCallback_doesNotCrash() {
        // Given
        val stealthActivated = AtomicBoolean(false)
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onActivate = null
        )

        // When/Then - should not throw
        handler.activateStealthMode()
        assertTrue(stealthActivated.get())
    }

    @Test
    fun testStealthHandler_deactivateWithoutCallback_doesNotCrash() {
        // Given
        val stealthActivated = AtomicBoolean(true)
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onDeactivate = null
        )

        // When/Then - should not throw
        handler.deactivateStealthMode()
        assertFalse(stealthActivated.get())
    }

    @Test
    fun testStealthHandler_multipleActivations_handlesCorrectly() {
        // Given
        val stealthActivated = AtomicBoolean(false)
        val onActivate: () -> Unit = mock()
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onActivate = onActivate
        )

        // When
        handler.activateStealthMode()
        handler.activateStealthMode()
        handler.activateStealthMode()

        // Then
        assertTrue(stealthActivated.get())
        verify(onActivate, times(3)).invoke()
    }

    @Test
    fun testStealthHandler_callbackThrowsException_doesNotCrash() {
        // Given
        val stealthActivated = AtomicBoolean(false)
        val onActivate: () -> Unit = { throw RuntimeException("Test exception") }
        val handler = BackgroundServiceExtendedStealthHandler(
            stealthActivated = stealthActivated,
            onActivate = onActivate
        )

        // When/Then - should catch exception and not crash
        handler.activateStealthMode()
        // Flag is not set because callback throws before state update.
        assertFalse(stealthActivated.get())
    }

    // ========== BackgroundServiceSettingsHandler Tests ==========

    @Test
    fun testSettingsHandler_openAppSettings_logsWarning() {
        // Given
        val send: (String) -> Unit = mock()
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When/Then - should not throw, just log
        handler.openAppSettings()
        verifyNoInteractions(send)
    }

    @Test
    fun testSettingsHandler_checkPermissionStatus_sendsStatus() {
        // Given
        val send: (String) -> Unit = mock()
        whenever(mockContext.checkPermission(any(), anyOrNull(), anyOrNull())).thenReturn(PackageManager.PERMISSION_GRANTED)
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When
        handler.checkPermissionStatus()

        // Then
        verify(send).invoke(argThat { message -> message.startsWith("PERMISSION_STATUS:") })
    }

    @Test
    fun testSettingsHandler_checkPermissionStatus_withDeniedPermissions() {
        // Given
        val send: (String) -> Unit = mock()
        whenever(mockContext.checkPermission(any(), anyOrNull(), anyOrNull())).thenReturn(PackageManager.PERMISSION_DENIED)
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When
        handler.checkPermissionStatus()

        // Then
        verify(send).invoke(argThat { message ->
            message.startsWith("PERMISSION_STATUS:") && message.contains("DENIED")
        })
    }

    @Test
    fun testSettingsHandler_checkPermissionStatus_formatCorrect() {
        // Given
        val capturedMessages = mutableListOf<String>()
        val send: (String) -> Unit = { msg -> capturedMessages.add(msg) }
        whenever(mockContext.checkPermission(any(), anyOrNull(), anyOrNull())).thenReturn(PackageManager.PERMISSION_GRANTED)
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When
        handler.checkPermissionStatus()

        // Then
        assertEquals(1, capturedMessages.size)
        val message = capturedMessages[0]
        assertTrue(message.startsWith("PERMISSION_STATUS:"))
        assertTrue(message.contains("CAMERA:"))
        assertTrue(message.contains("MICROPHONE:"))
        assertTrue(message.contains("LOCATION:"))
        assertTrue(message.contains("SMS:"))
        assertTrue(message.contains("CALL_LOG:"))
        assertTrue(message.contains("|"))
    }

    @Test
    fun testSettingsHandler_requestAllPermissions_logsWarning() {
        // Given
        val send: (String) -> Unit = mock()
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When/Then - should not throw, just log
        handler.requestAllPermissions()
        verifyNoInteractions(send)
    }

    @Test
    fun testSettingsHandler_showPermissionSettingsDialog_logsWarning() {
        // Given
        val send: (String) -> Unit = mock()
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When/Then - should not throw, just log
        handler.showPermissionSettingsDialog("Camera")
        verifyNoInteractions(send)
    }

    @Test
    fun testSettingsHandler_multipleCheckPermissionStatus_sendsMultiple() {
        // Given
        val send: (String) -> Unit = mock()
        whenever(mockContext.checkPermission(any(), anyOrNull(), anyOrNull())).thenReturn(PackageManager.PERMISSION_GRANTED)
        val handler = BackgroundServiceSettingsHandler(mockContext, send)

        // When
        handler.checkPermissionStatus()
        handler.checkPermissionStatus()
        handler.checkPermissionStatus()

        // Then
        verify(send, times(3)).invoke(any())
    }
}
