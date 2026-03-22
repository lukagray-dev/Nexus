package nexus.android.child.permissions

import android.app.Activity
import android.content.Intent
import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for PermissionHelper.
 * Tests OEM-specific permission settings navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PermissionHelperTest {

    private lateinit var mockActivity: Activity

    @Before
    fun setup() {
        mockActivity = mock {
            on { packageName } doReturn "nexus.android.child"
        }
    }

    @Test
    fun testOpenPermissionSettings_withLocationPermission_opensAppDetails() {
        // Given
        val deniedPermissions = listOf("android.permission.ACCESS_FINE_LOCATION")

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS &&
            intent.data.toString().contains("nexus.android.child")
        })
    }

    @Test
    fun testOpenPermissionSettings_withCoarseLocationPermission_opensAppDetails() {
        // Given
        val deniedPermissions = listOf("android.permission.ACCESS_COARSE_LOCATION")

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
    }

    @Test
    fun testOpenPermissionSettings_vendorSpecificFlow_startsActivity() {
        // Given
        val deniedPermissions = listOf("android.permission.CAMERA")

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then - should attempt to start some settings activity.
        verify(mockActivity, atLeastOnce()).startActivity(any())
    }

    @Test
    fun testOpenPermissionSettings_withNullPermissions_opensAppDetails() {
        // When
        PermissionHelper.openPermissionSettings(mockActivity, null)

        // Then
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
    }

    @Test
    fun testOpenPermissionSettings_withEmptyPermissions_opensAppDetails() {
        // When
        PermissionHelper.openPermissionSettings(mockActivity, emptyList())

        // Then
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
    }

    @Test
    fun testOpenPermissionSettings_withNonLocationPermissions_opensSettings() {
        // Given
        val deniedPermissions = listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then
        verify(mockActivity).startActivity(any())
    }

    @Test
    fun testOpenPermissionSettings_withMixedPermissions_prioritizesLocation() {
        // Given - location permission mixed with others
        val deniedPermissions = listOf(
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO"
        )

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then - should open app details due to location permission
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
    }

    @Test
    fun testOpenPermissionSettings_activityThrowsException_doesNotCrash() {
        // Given
        val mockActivity: Activity = mock {
            on { packageName } doReturn "nexus.android.child"
            on { startActivity(any()) } doThrow RuntimeException("Activity not found")
        }

        // When/Then - should not throw
        PermissionHelper.openPermissionSettings(mockActivity, null)
    }

    @Test
    fun testOpenPermissionSettings_withBackgroundLocation_opensAppDetails() {
        // Given
        val deniedPermissions = listOf("android.permission.ACCESS_BACKGROUND_LOCATION")

        // When
        PermissionHelper.openPermissionSettings(mockActivity, deniedPermissions)

        // Then
        verify(mockActivity).startActivity(argThat { intent ->
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
    }
}
