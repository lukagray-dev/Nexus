package com.nexus.child.components.wallpaper

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * WallpaperCommandHandler coordinates WebRTC DataChannel commands, live wallpaper streaming,
 * and chunked image packet reception/dispatching between Child and Parent.
 *
 * Hey friend! Here is how the wallpaper protocol functions:
 * 1. Parent clicks HUD -> sends "WALLPAPER_GET". We query the child device wallpapers and slice
 *    the Base64 payload into 16KB JSON chunks (WALLPAPER_CHUNK) so SCTP buffers are never overwhelmed.
 * 2. Parent clicks Play -> sends "WALLPAPER_ON". We register the live listener, reply with
 *    "WALLPAPER_STARTED", and dispatch the current wallpaper snapshot.
 * 3. While live, if the child changes wallpaper, OnColorsChangedListener triggers and dispatches
 *    fresh WALLPAPER_CHUNK packets to auto-update the parent HUD in real-time!
 * 4. Parent clicks Pause -> sends "WALLPAPER_OFF". We halt observers and reply "WALLPAPER_STOPPED".
 * 5. Parent pushes wallpaper -> sends PUSH_WALLPAPER_START followed by PUSH_WALLPAPER_CHUNK packets.
 *    We reassemble the image, call WallpaperManager.setBitmap(), and confirm with "WALLPAPER_SET_SUCCESS".
 */
class WallpaperCommandHandler(
    private val wallpaperController: IWallpaperController,
    private val dataSender: (message: String) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    companion object {
        private const val TAG = "WallpaperCmdHandler"
        private const val CHUNK_SIZE = 16384 // 16KB chunk size for reliable SCTP transport

        // Commands
        const val CMD_WALLPAPER_GET = "WALLPAPER_GET"
        const val CMD_WALLPAPER_ON = "WALLPAPER_ON"
        const val CMD_WALLPAPER_OFF = "WALLPAPER_OFF"
        const val ALIAS_WALLPAPER_START = "WALLPAPER_START"
        const val ALIAS_WALLPAPER_STOP = "WALLPAPER_STOP"

        // Responses
        const val RESP_WALLPAPER_STARTED = "WALLPAPER_STARTED"
        const val RESP_WALLPAPER_STOPPED = "WALLPAPER_STOPPED"
        const val RESP_WALLPAPER_ERROR_PREFIX = "WALLPAPER_ERROR: "
        const val RESP_WALLPAPER_SET_SUCCESS = "WALLPAPER_SET_SUCCESS"
        const val RESP_WALLPAPER_SET_ERROR_PREFIX = "WALLPAPER_SET_ERROR: "

        // Telemetry Types
        const val TYPE_WALLPAPER_CHUNK = "WALLPAPER_CHUNK"
        const val TYPE_PUSH_WALLPAPER_START = "PUSH_WALLPAPER_START"
        const val TYPE_PUSH_WALLPAPER_CHUNK = "PUSH_WALLPAPER_CHUNK"
    }

    // Buffer for receiving pushed wallpapers from parent
    private val pushBuffer = ByteArrayOutputStream()
    private var expectedPushChunks = 0
    private var receivedPushChunks = 0
    private var pushTarget = "both"

    /**
     * Inspects incoming DataChannel strings and routes wallpaper commands.
     *
     * @param command Raw message received over DataChannel
     * @return true if handled by WallpaperCommandHandler; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        val trimmed = command.trim()

        return when {
            trimmed == CMD_WALLPAPER_GET -> {
                handleWallpaperGet()
                true
            }
            trimmed == CMD_WALLPAPER_ON || trimmed == ALIAS_WALLPAPER_START -> {
                handleWallpaperOn()
                true
            }
            trimmed == CMD_WALLPAPER_OFF || trimmed == ALIAS_WALLPAPER_STOP -> {
                handleWallpaperOff()
                true
            }
            trimmed.startsWith("{") && trimmed.contains(TYPE_PUSH_WALLPAPER_START) -> {
                handlePushWallpaperStart(trimmed)
                true
            }
            trimmed.startsWith("{") && trimmed.contains(TYPE_PUSH_WALLPAPER_CHUNK) -> {
                handlePushWallpaperChunk(trimmed)
                true
            }
            else -> false
        }
    }

    private fun handleWallpaperGet() {
        Log.d(TAG, "Processing one-shot WALLPAPER_GET request...")
        coroutineScope.launch(ioDispatcher) {
            try {
                val bundle = wallpaperController.getActiveWallpapers()
                sendWallpaperBundle(bundle)
            } catch (e: Exception) {
                val err = "${RESP_WALLPAPER_ERROR_PREFIX}Failed fetching active wallpaper: ${e.message}"
                Log.e(TAG, err, e)
                dataSender(err)
            }
        }
    }

    private fun handleWallpaperOn() {
        Log.d(TAG, "Processing WALLPAPER_ON command...")
        if (!wallpaperController.hasWallpaperPermission()) {
            val err = "${RESP_WALLPAPER_ERROR_PREFIX}Wallpaper feature not supported or permitted on child device"
            Log.w(TAG, err)
            dataSender(err)
            return
        }

        val started = wallpaperController.startWallpaperMonitoring { bundle ->
            Log.d(TAG, "Live wallpaper change detected. Dispatched updated bundle to parent.")
            sendWallpaperBundle(bundle)
        }

        if (started) {
            Log.d(TAG, "Wallpaper monitoring active. Confirming with $RESP_WALLPAPER_STARTED")
            dataSender(RESP_WALLPAPER_STARTED)

            // Immediately send current active wallpaper snapshot
            coroutineScope.launch(ioDispatcher) {
                try {
                    val bundle = wallpaperController.getActiveWallpapers()
                    sendWallpaperBundle(bundle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching initial wallpaper snapshot: ${e.message}", e)
                }
            }
        } else {
            val err = "${RESP_WALLPAPER_ERROR_PREFIX}Failed registering wallpaper change listeners"
            Log.e(TAG, err)
            dataSender(err)
        }
    }

    private fun handleWallpaperOff() {
        Log.d(TAG, "Processing WALLPAPER_OFF command...")
        wallpaperController.stopWallpaperMonitoring()
        Log.d(TAG, "Wallpaper monitoring halted. Confirming with $RESP_WALLPAPER_STOPPED")
        dataSender(RESP_WALLPAPER_STOPPED)
    }

    /**
     * Slices and transmits a wallpaper data bundle across the DataChannel in 16KB JSON chunks.
     */
    fun sendWallpaperBundle(bundle: WallpaperDataBundle) {
        val home = bundle.homeBase64
        val lock = bundle.lockBase64

        // 1. Dispatch Home Screen Chunks
        if (!home.isNullOrEmpty()) {
            sendScreenChunks(screen = "home", base64Data = home, isSame = bundle.isSame)
        }

        // 2. If Lock screen has distinct wallpaper, dispatch Lock Screen Chunks
        if (!bundle.isSame && !lock.isNullOrEmpty()) {
            sendScreenChunks(screen = "lock", base64Data = lock, isSame = false)
        }
    }

    private fun sendScreenChunks(screen: String, base64Data: String, isSame: Boolean) {
        val totalLength = base64Data.length
        val totalChunks = (totalLength + CHUNK_SIZE - 1) / CHUNK_SIZE

        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, totalLength)
            val chunkSlice = base64Data.substring(start, end)

            val json = buildWallpaperChunkJson(
                screen = screen,
                chunkIndex = i,
                totalChunks = totalChunks,
                data = chunkSlice,
                isSame = isSame
            )

            dataSender(json)
        }
        Log.d(TAG, "Dispatched $totalChunks chunks for screen '$screen' (isSame=$isSame)")
    }

    /**
     * Builds the JSON string for a single wallpaper chunk envelope without relying on stubbed JSONObject.
     */
    fun buildWallpaperChunkJson(
        screen: String,
        chunkIndex: Int,
        totalChunks: Int,
        data: String,
        isSame: Boolean
    ): String {
        return "{\"type\":\"$TYPE_WALLPAPER_CHUNK\"," +
                "\"screen\":\"$screen\"," +
                "\"chunkIndex\":$chunkIndex," +
                "\"totalChunks\":$totalChunks," +
                "\"data\":\"$data\"," +
                "\"isSame\":$isSame}"
    }

    private fun handlePushWallpaperStart(jsonText: String) {
        try {
            pushTarget = extractJsonString(jsonText, "target") ?: "both"
            expectedPushChunks = extractJsonInt(jsonText, "totalChunks") ?: 0
            receivedPushChunks = 0
            pushBuffer.reset()
            Log.d(TAG, "Ready to receive pushed wallpaper: target=$pushTarget, totalChunks=$expectedPushChunks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing PUSH_WALLPAPER_START: ${e.message}", e)
        }
    }

    private fun handlePushWallpaperChunk(jsonText: String) {
        try {
            val chunkIndex = extractJsonInt(jsonText, "chunkIndex") ?: 0
            val totalChunks = extractJsonInt(jsonText, "totalChunks") ?: 1
            val base64Chunk = extractJsonString(jsonText, "data") ?: ""

            val bytes = decodeBase64(base64Chunk)
            pushBuffer.write(bytes)
            receivedPushChunks++

            Log.d(TAG, "Received push chunk ${chunkIndex + 1}/$totalChunks")

            if (receivedPushChunks >= totalChunks && totalChunks > 0) {
                Log.d(TAG, "All wallpaper chunks received (${pushBuffer.size()} bytes). Applying to device...")
                val fullImageBytes = pushBuffer.toByteArray()
                pushBuffer.reset()
                expectedPushChunks = 0
                receivedPushChunks = 0

                coroutineScope.launch(ioDispatcher) {
                    val applied = wallpaperController.applyWallpaper(fullImageBytes, pushTarget)
                    if (applied) {
                        Log.d(TAG, "Successfully applied pushed wallpaper to $pushTarget screen.")
                        dataSender(RESP_WALLPAPER_SET_SUCCESS)
                    } else {
                        val err = "${RESP_WALLPAPER_SET_ERROR_PREFIX}Failed applying wallpaper to device hardware"
                        Log.e(TAG, err)
                        dataSender(err)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed processing PUSH_WALLPAPER_CHUNK: ${e.message}", e)
            val err = "${RESP_WALLPAPER_SET_ERROR_PREFIX}Chunk processing failed: ${e.message}"
            dataSender(err)
        }
    }

    private fun decodeBase64(base64Str: String): ByteArray {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.util.Base64.getDecoder().decode(base64Str)
            } else {
                android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            }
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(base64Str)
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Fail-safe invoked when the WebRTC peer disconnects.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Stopping wallpaper monitoring and resetting push buffers...")
        wallpaperController.stopWallpaperMonitoring()
        pushBuffer.reset()
        expectedPushChunks = 0
        receivedPushChunks = 0
    }

    /**
     * Cleans up all controller and observer resources.
     */
    fun cleanup() {
        wallpaperController.cleanup()
        pushBuffer.reset()
    }
}
