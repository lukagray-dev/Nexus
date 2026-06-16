package nexus.android.child.configuration

/**
 * Application configuration constants
 * Centralized configuration for better maintainability
 */
object AppConfig {

    // Firebase Configuration
    object Firebase {
        const val DATABASE_URL = "https://nexus-33-default-rtdb.firebaseio.com"
    }

    // WebRTC Configuration
    object WebRTC {
        // STUN server configuration for NAT traversal
        val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302"
        )
    }
}