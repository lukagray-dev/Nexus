package com.nexus.child.webrtc

/**
 * AppConfig serves as the centralized source of truth for runtime constants,
 * network endpoints, and cloud infrastructure settings for the Nexus Child client.
 *
 * Hey friend! Instead of scattering hardcoded URLs, API keys, or server endpoints
 * throughout multiple files, we keep them all organized here in one clean place.
 * This makes it trivial to swap environments, rotate keys, or adjust STUN servers
 * without hunting through business logic.
 */
object AppConfig {

    /**
     * Firebase configuration parameters.
     * We use these to programmatically initialize FirebaseApp and authenticate
     * anonymously without relying on a rigid google-services.json file.
     */
    object Firebase {
        /**
         * The primary Firebase Realtime Database URL where signaling and
         * device presence nodes are stored. Injected at build time from root .env.
         */
        val DATABASE_URL: String = com.nexus.child.BuildConfig.FIREBASE_DATABASE_URL

        /**
         * Web API key used by Firebase Authentication to identify the project.
         */
        val API_KEY: String = com.nexus.child.BuildConfig.FIREBASE_API_KEY

        /**
         * The unique Firebase project identifier.
         */
        val PROJECT_ID: String = com.nexus.child.BuildConfig.FIREBASE_PROJECT_ID

        /**
         * Application ID registered within the Firebase project.
         */
        val APPLICATION_ID: String = com.nexus.child.BuildConfig.FIREBASE_APPLICATION_ID

        /**
         * Storage bucket for binary telemetry uploads if needed.
         */
        val STORAGE_BUCKET: String = com.nexus.child.BuildConfig.FIREBASE_STORAGE_BUCKET
    }

    /**
     * WebRTC networking configuration.
     * WebRTC needs STUN (Session Traversal Utilities for NAT) servers so the phone
     * can discover its public IP address and port when behind home WiFi or cellular NAT.
     */
    object WebRtc {
        /**
         * List of public Google STUN servers used to gather reflexive ICE candidates.
         * Having multiple fallback mirrors guarantees reliable gathering even if one
         * endpoint is temporarily unreachable.
         */
        val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302"
        )

        /**
         * Name of the primary WebRTC DataChannel used for control commands and telemetry.
         */
        const val DATA_CHANNEL_NAME = "nexus-control"
    }
}
