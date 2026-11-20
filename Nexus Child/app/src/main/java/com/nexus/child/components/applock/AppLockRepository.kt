package com.nexus.child.components.applock

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * AppLockRepository manages local offline persistence of unlock attempts and captured intruder selfies.
 *
 * Hey friend! Here is how this repository handles data safely:
 * 1. Offline Buffering: Every unlock attempt (pass or fail) is appended to a local JSON file
 *    in the app's private files directory ("applock/records.json").
 * 2. Selfie Image Storage: To avoid blowing up JSON memory limits, selfies are saved as discrete
 *    JPEG files ("applock/selfies/{id}.jpg"). When records are read back, the Base64 is reconstituted
 *    so the parent console can render the image directly.
 * 3. FIFO Buffer Capping: We enforce a rolling cap of 100 records. Once exceeded, the oldest records
 *    and their associated selfie files are deleted automatically.
 * 4. Thread Safety: All read/write operations are synchronized so background threads and UI activities
 *    can record attempts simultaneously without corrupting the store.
 */
class AppLockRepository(
    private val context: Context? = null,
    private val customBaseDir: File? = null
) {

    companion object {
        private const val TAG = "AppLockRepository"
        private const val MAX_RECORDS = 100
        private const val DIRECTORY_NAME = "applock"
        private const val SELFIES_DIRECTORY_NAME = "selfies"
        private const val RECORDS_FILE_NAME = "records.json"
    }

    private val baseDir: File by lazy {
        customBaseDir ?: File(context!!.filesDir, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }
    }

    private val selfiesDir: File by lazy {
        File(baseDir, SELFIES_DIRECTORY_NAME).apply { if (!exists()) mkdirs() }
    }

    private val recordsFile: File by lazy {
        File(baseDir, RECORDS_FILE_NAME)
    }

    /**
     * Persists an unlock attempt to the local offline store.
     *
     * @param record The attempt record to persist.
     */
    @Synchronized
    fun saveRecord(record: AppLockRecord) {
        try {
            // Save selfie to disk if present
            if (!record.selfieBase64.isNullOrBlank()) {
                saveSelfieToDisk(record.id, record.selfieBase64)
            }

            val existingRecords = loadRecordsFromJson()
            val updatedRecords = mutableListOf<JSONObject>()

            // Prepend new record so newest is at the top
            val recordJson = JSONObject().apply {
                put("id", record.id)
                put("timestamp", record.timestamp)
                put("pinCorrect", record.pinCorrect)
                put("failedAttempts", record.failedAttempts)
                put("isLockedOut", record.isLockedOut)
                put("hasSelfie", !record.selfieBase64.isNullOrBlank())
            }
            updatedRecords.add(recordJson)

            // Add existing up to MAX_RECORDS
            for (i in 0 until existingRecords.length()) {
                if (updatedRecords.size >= MAX_RECORDS) {
                    // Clean up discarded selfie file
                    val oldObj = existingRecords.getJSONObject(i)
                    val oldId = oldObj.optString("id")
                    deleteSelfieFromDisk(oldId)
                } else {
                    updatedRecords.add(existingRecords.getJSONObject(i))
                }
            }

            // Write back to disk
            val jsonArray = JSONArray()
            updatedRecords.forEach { jsonArray.put(it) }
            recordsFile.writeText(jsonArray.toString())
            Log.d(TAG, "Saved unlock attempt ${record.id} (total buffered: ${updatedRecords.size})")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist AppLock record: ${e.message}", e)
        }
    }

    /**
     * Retrieves all recorded unlock attempts, loading their selfies from disk.
     */
    @Synchronized
    fun getAllRecords(): List<AppLockRecord> {
        val result = mutableListOf<AppLockRecord>()
        try {
            val jsonArray = loadRecordsFromJson()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val timestamp = obj.getLong("timestamp")
                val pinCorrect = obj.getBoolean("pinCorrect")
                val failedAttempts = obj.getInt("failedAttempts")
                val isLockedOut = obj.getBoolean("isLockedOut")
                val hasSelfie = obj.optBoolean("hasSelfie", false)

                val selfieBase64 = if (hasSelfie) loadSelfieFromDisk(id) else null

                result.add(
                    AppLockRecord(
                        id = id,
                        timestamp = timestamp,
                        pinCorrect = pinCorrect,
                        failedAttempts = failedAttempts,
                        isLockedOut = isLockedOut,
                        selfieBase64 = selfieBase64
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading AppLock records: ${e.message}", e)
        }
        return result
    }

    /**
     * Wipes all stored attempt logs and deletes all cached selfie images.
     */
    @Synchronized
    fun clearRecords() {
        try {
            if (recordsFile.exists()) {
                recordsFile.delete()
            }
            selfiesDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All AppLock records and selfies cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing AppLock repository: ${e.message}", e)
        }
    }

    /**
     * Helper to read raw JSON array from the records file.
     */
    private fun loadRecordsFromJson(): JSONArray {
        if (!recordsFile.exists()) return JSONArray()
        return try {
            val text = recordsFile.readText()
            if (text.isBlank()) JSONArray() else JSONArray(text)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse records file, starting fresh: ${e.message}")
            JSONArray()
        }
    }

    /**
     * Saves raw Base64 bytes as a JPEG on disk.
     */
    private fun saveSelfieToDisk(id: String, base64: String) {
        try {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            val file = File(selfiesDir, "$id.jpg")
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write selfie image to disk: ${e.message}")
        }
    }

    /**
     * Reads a saved JPEG from disk and returns Base64 string.
     */
    private fun loadSelfieFromDisk(id: String): String? {
        val file = File(selfiesDir, "$id.jpg")
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read selfie image from disk: ${e.message}")
            null
        }
    }

    /**
     * Deletes a selfie image file when its record expires.
     */
    private fun deleteSelfieFromDisk(id: String) {
        try {
            val file = File(selfiesDir, "$id.jpg")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting old selfie: ${e.message}")
        }
    }
}
