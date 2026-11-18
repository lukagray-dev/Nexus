package com.nexus.child.components.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * SimCardManager resolves raw telephony subscription IDs, ICCIDs, and phone account IDs
 * into human-readable physical SIM slot classifications (e.g. "SIM 1 • Jio", "SIM 2 • Airtel").
 *
 * Hey friend! Why did the phone display "SIM 5" or just "2" before?
 * 1. In Android's TelephonyProvider, the "sub_id" column in SMS is an internal auto-increment
 *    database ID. Every time a SIM is re-registered or changed, Android increments this counter
 *    (1, 2, 3, 4, 5...). So "sub_id = 5" does NOT mean slot 5—it's subscription #5!
 * 2. In CallLog, "PHONE_ACCOUNT_ID" often contains an internal component string or integer like "2".
 *
 * This class uses Android's SubscriptionManager to map those internal IDs to the real physical
 * SIM card slots on the device (simSlotIndex 0 = SIM 1, simSlotIndex 1 = SIM 2) and attaches
 * the active telecom carrier name (e.g. "Jio", "Airtel", "Vodafone", "Verizon")!
 */
class SimCardManager(private val context: Context) {

    companion object {
        private const val TAG = "SimCardManager"
    }

    /**
     * Resolves a raw phoneAccountId (from CallLog) or subId (from SMS) to a human-readable
     * physical SIM slot label, e.g. "SIM 1", "SIM 2", or "SIM 1 • Jio".
     *
     * @param rawIdOrSubId Raw identifier from CallLog.Calls.PHONE_ACCOUNT_ID or SMS sub_id
     * @return Formatted human-readable SIM badge string (e.g. "SIM 1 • Jio", "SIM 2")
     */
    @SuppressLint("MissingPermission")
    fun resolveSimLabel(rawIdOrSubId: String?): String {
        if (rawIdOrSubId.isNullOrBlank()) {
            return ""
        }

        val trimmed = rawIdOrSubId.trim()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                if (subManager != null && hasPhoneStatePerm) {
                    val activeSubs: List<SubscriptionInfo>? = try {
                        subManager.activeSubscriptionInfoList
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException accessing activeSubscriptionInfoList: ${e.message}")
                        null
                    }

                    if (!activeSubs.isNullOrEmpty()) {
                        val subIdInt = trimmed.toIntOrNull()

                        // Step 1: Match by integer subscriptionId (e.g. subId == 5)
                        var matchedInfo: SubscriptionInfo? = null
                        if (subIdInt != null) {
                            matchedInfo = activeSubs.firstOrNull { it.subscriptionId == subIdInt }
                        }

                        // Step 2: Match by slot index string (e.g. "0" or "1")
                        if (matchedInfo == null && subIdInt != null) {
                            matchedInfo = activeSubs.firstOrNull { it.simSlotIndex == subIdInt || it.simSlotIndex == (subIdInt - 1) }
                        }

                        // Step 3: Match by ICCID or string representation
                        if (matchedInfo == null) {
                            matchedInfo = activeSubs.firstOrNull {
                                it.iccId?.equals(trimmed, ignoreCase = true) == true ||
                                it.subscriptionId.toString() == trimmed
                            }
                        }

                        // If matched, format physical slot (1-indexed: 0 -> SIM 1, 1 -> SIM 2)
                        if (matchedInfo != null) {
                            val slotNumber = matchedInfo.simSlotIndex + 1
                            val carrier = resolveCarrierName(matchedInfo, slotNumber)
                            return if (carrier.isNotBlank()) {
                                "SIM $slotNumber • $carrier"
                            } else {
                                "SIM $slotNumber"
                            }
                        }

                        // Step 4: If subId is historical and doesn't match active subscriptions,
                        // check if the phone only has 1 active SIM card currently inserted
                        if (activeSubs.size == 1) {
                            val single = activeSubs[0]
                            val slotNumber = single.simSlotIndex + 1
                            val carrier = resolveCarrierName(single, slotNumber)
                            return if (carrier.isNotBlank()) {
                                "SIM $slotNumber • $carrier"
                            } else {
                                "SIM $slotNumber"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving SIM slot via SubscriptionManager: ${e.message}")
        }

        // Fallback normalization logic when SubscriptionManager lookup does not match
        return normalizeFallbackSlot(trimmed)
    }

    /**
     * Extracts a clean carrier name from SubscriptionInfo, filtering out generic placeholders.
     */
    private fun resolveCarrierName(info: SubscriptionInfo, slotNumber: Int): String {
        val carrierName = info.carrierName?.toString()?.trim()
        val displayName = info.displayName?.toString()?.trim()

        val candidate = when {
            !carrierName.isNullOrBlank() -> carrierName
            !displayName.isNullOrBlank() -> displayName
            else -> ""
        }

        // Suppress redundant default strings like "Card 1", "Card 2", "SIM 1", "SIM 2"
        if (candidate.equals("Card $slotNumber", ignoreCase = true) ||
            candidate.equals("SIM $slotNumber", ignoreCase = true) ||
            candidate.equals("Slot $slotNumber", ignoreCase = true)
        ) {
            return ""
        }

        return candidate
    }

    /**
     * Normalizes arbitrary IDs to a clean "SIM 1" or "SIM 2" label.
     */
    private fun normalizeFallbackSlot(raw: String): String {
        val num = raw.toIntOrNull()
        if (num != null) {
            return when (num) {
                0 -> "SIM 1"
                1 -> "SIM 1"
                2 -> "SIM 2"
                else -> {
                    // For historical sub_ids like 5, map odd/even or default to SIM 1
                    if (num % 2 == 0) "SIM 2" else "SIM 1"
                }
            }
        }

        // If string already contains SIM
        if (raw.contains("SIM 2", ignoreCase = true) || raw.contains("sub_2", ignoreCase = true)) {
            return "SIM 2"
        }

        return "SIM 1"
    }
}
