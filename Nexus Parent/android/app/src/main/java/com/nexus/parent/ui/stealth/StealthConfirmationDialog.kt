package com.nexus.parent.ui.stealth

import androidx.compose.runtime.Composable
import com.nexus.parent.state.StealthStatus
import com.nexus.parent.ui.components.TacticalConfirmDialog
import com.nexus.parent.ui.components.TacticalNoticeDialog

/**
 * StealthConfirmationDialog.kt - Remote Stealth Mode Confirmation & Notice Modals (`stealth-manager.ts`).
 */
@Composable
fun StealthDialogsHost(
    showConfirmDialog: Boolean,
    showNotConnectedDialog: Boolean,
    currentStatus: StealthStatus,
    onConfirmToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showNotConnectedDialog) {
        TacticalNoticeDialog(
            title = "Device Not Connected",
            message = "Please pair and establish a live WebRTC connection with a child device before toggling Stealth Mode.",
            onDismiss = onDismiss
        )
    }

    if (showConfirmDialog) {
        val isActive = currentStatus == StealthStatus.ACTIVE
        TacticalConfirmDialog(
            title = if (isActive) "Deactivate Stealth Mode?" else "Activate Stealth Mode?",
            message = if (isActive) {
                "This operation will restore the Nexus Child application icon to the child device home screen launcher and app drawer."
            } else {
                "This operation will hide the Nexus Child application icon from the child device home screen and app drawer.\n\nAll background monitoring, telemetry, and camera/mic surveillance will remain fully operational."
            },
            noteText = if (isActive) {
                "The child device user will be able to see the app icon again."
            } else {
                "Stealth mode can be reversed at any time from this console or by tapping the 1x1 Nexus Child widget on the child device."
            },
            confirmText = if (isActive) "Deactivate Stealth" else "Activate Stealth",
            isDangerAction = !isActive,
            onConfirm = onConfirmToggle,
            onDismiss = onDismiss
        )
    }
}
