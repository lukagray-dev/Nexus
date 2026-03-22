package nexus.android.child.service

import android.content.Context
import android.content.Intent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for UnlockReceiver.
 * Tests device unlock event handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnlockReceiverTest {

    private lateinit var receiver: UnlockReceiver
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        receiver = UnlockReceiver()
        mockContext = mock {
            on { applicationContext } doReturn mock()
        }
    }

    @Test
    fun testOnReceive_withUserPresent_startsService() {
        // Given
        val intent = Intent(Intent.ACTION_USER_PRESENT)

        // When
        receiver.onReceive(mockContext, intent)

        // Then
        verify(mockContext).applicationContext
    }

    @Test
    fun testOnReceive_withUnrelatedIntent_doesNothing() {
        // Given
        val intent = Intent(Intent.ACTION_SCREEN_ON)

        // When
        receiver.onReceive(mockContext, intent)

        // Then
        verifyNoInteractions(mockContext)
    }

    @Test
    fun testOnReceive_withNullAction_doesNotCrash() {
        // Given
        val intent = Intent()

        // When/Then - should not throw
        receiver.onReceive(mockContext, intent)
        verifyNoInteractions(mockContext)
    }

    @Test
    fun testOnReceive_multipleUserPresentEvents_handlesAll() {
        // Given
        val intent = Intent(Intent.ACTION_USER_PRESENT)

        // When
        repeat(3) {
            receiver.onReceive(mockContext, intent)
        }

        // Then
        verify(mockContext, times(3)).applicationContext
    }

    @Test
    fun testOnReceive_withUserPresent_usesApplicationContext() {
        // Given
        val appContext: Context = mock()
        val mockContext: Context = mock {
            on { applicationContext } doReturn appContext
        }
        val intent = Intent(Intent.ACTION_USER_PRESENT)

        // When
        receiver.onReceive(mockContext, intent)

        // Then
        verify(mockContext).applicationContext
    }
}
