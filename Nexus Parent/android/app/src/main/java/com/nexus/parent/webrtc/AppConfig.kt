package com.nexus.parent.webrtc

import com.nexus.parent.BuildConfig

/**
 * AppConfig.kt - Centralized Firebase and WebRTC runtime configuration for Nexus Parent Android.
 *
 * Hey friend! Just like `firebase-config.ts` on Desktop and `AppConfig.kt` on Nexus Child,
 * this object keeps all cloud credentials (injected from root `.env` at build time) and
 * STUN/DataChannel constants in a single deterministic module.
 */
object AppConfig {

    object Firebase {
        val API_KEY: String = BuildConfig.FIREBASE_API_KEY
        val AUTH_DOMAIN: String = BuildConfig.FIREBASE_AUTH_DOMAIN
        val DATABASE_URL: String = BuildConfig.FIREBASE_DATABASE_URL
        val PROJECT_ID: String = BuildConfig.FIREBASE_PROJECT_ID
        val APPLICATION_ID: String = BuildConfig.FIREBASE_APPLICATION_ID
        val STORAGE_BUCKET: String = BuildConfig.FIREBASE_STORAGE_BUCKET
    }

    object WebRtc {
        val STUN_SERVERS: List<String> = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302"
        )

        const val DATA_CHANNEL_NAME = "nexus-control"
        const val ICE_GATHERING_TIMEOUT_MS = 1000L
        const val INITIAL_HANDSHAKE_TIMEOUT_MS = 30_000L
    }
}
