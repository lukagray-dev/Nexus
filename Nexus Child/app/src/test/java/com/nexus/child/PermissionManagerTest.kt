package com.nexus.child

import com.nexus.child.permission.PermissionManager
import com.nexus.child.permission.PermissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the integrity and contractual consistency of the complete Permission system.
 */
class PermissionManagerTest {

    @Test
    fun testTotalPermissionCount() {
        val total = PermissionManager.getTotalCount()
        assertEquals("Permission manager must track exactly 15 permissions", 15, total)
        assertEquals(15, PermissionManager.allPermissions.size)
    }

    @Test
    fun testRuntimeAndSpecialSplit() {
        val runtimeCount = PermissionManager.runtimePermissions.size
        val specialCount = PermissionManager.specialPermissions.size
        assertEquals("There should be 10 runtime permissions", 10, runtimeCount)
        assertEquals("There should be 5 special system permissions", 5, specialCount)
        assertEquals(15, runtimeCount + specialCount)
    }

    @Test
    fun testAllPermissionsHaveValidMetadata() {
        for (perm in PermissionManager.allPermissions) {
            assertTrue("Title string resource must be valid for ${perm.name}", perm.titleRes != 0)
            assertTrue("Description string resource must be valid for ${perm.name}", perm.descriptionRes != 0)
            assertTrue("Category name must not be blank for ${perm.name}", perm.categoryName.isNotBlank())
            assertTrue("Icon drawable resource must be valid for ${perm.name}", perm.iconRes != 0)
            assertNotNull("Category name must be non-null for ${perm.name}", perm.categoryName)
        }
    }

    @Test
    fun testRuntimePermissionsClassification() {
        assertTrue("Camera must be runtime", PermissionType.CAMERA.isRuntimePermission)
        assertTrue("Microphone must be runtime", PermissionType.MICROPHONE.isRuntimePermission)
        assertTrue("Location must be runtime", PermissionType.LOCATION.isRuntimePermission)
        assertTrue("Call Logs must be runtime", PermissionType.CALL_LOGS.isRuntimePermission)
        assertTrue("Contacts must be runtime", PermissionType.CONTACTS.isRuntimePermission)
        assertTrue("Phone must be runtime", PermissionType.PHONE.isRuntimePermission)
        assertTrue("SMS must be runtime", PermissionType.SMS.isRuntimePermission)
        assertTrue("Photos & Videos must be runtime", PermissionType.PHOTOS_AND_VIDEOS.isRuntimePermission)
        assertTrue("Music & Audio must be runtime", PermissionType.MUSIC_AND_AUDIO.isRuntimePermission)
        assertTrue("Notifications must be runtime", PermissionType.NOTIFICATIONS.isRuntimePermission)

        assertFalse("All Files must be special", PermissionType.ALL_FILES_ACCESS.isRuntimePermission)
        assertFalse("Battery must be special", PermissionType.BATTERY_OPTIMIZATION.isRuntimePermission)
        assertFalse("Auto Start must be special", PermissionType.AUTO_START.isRuntimePermission)
        assertFalse("Accessibility must be special", PermissionType.ACCESSIBILITY_SERVICE.isRuntimePermission)
        assertFalse("Notification listener must be special", PermissionType.NOTIFICATION_LISTENER.isRuntimePermission)
    }

    @Test
    fun testRuntimePermissionArrays() {
        val callLogsPerms = PermissionType.CALL_LOGS.getRequiredRuntimePermissions()
        assertTrue("Call logs perms array must contain READ_CALL_LOG", callLogsPerms.contains(android.Manifest.permission.READ_CALL_LOG))

        val contactsPerms = PermissionType.CONTACTS.getRequiredRuntimePermissions()
        assertTrue("Contacts perms array must contain READ_CONTACTS", contactsPerms.contains(android.Manifest.permission.READ_CONTACTS))

        val smsPerms = PermissionType.SMS.getRequiredRuntimePermissions()
        assertTrue("SMS perms array must contain READ_SMS", smsPerms.contains(android.Manifest.permission.READ_SMS))
        assertTrue("SMS perms array must contain RECEIVE_SMS", smsPerms.contains(android.Manifest.permission.RECEIVE_SMS))

        val phonePerms = PermissionType.PHONE.getRequiredRuntimePermissions()
        assertTrue("Phone perms array must contain READ_PHONE_STATE", phonePerms.contains(android.Manifest.permission.READ_PHONE_STATE))
    }
}
