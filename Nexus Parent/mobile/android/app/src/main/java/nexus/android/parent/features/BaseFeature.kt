package nexus.android.parent.features

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * BaseFeature - Abstract base class for all feature implementations
 * Each feature (Camera, Screen, Location, etc.) extends this class
 * Provides common functionality and lifecycle management
 */
abstract class BaseFeature(protected val context: Context) {

    /**
     * Create and return the feature's UI view
     * This will be inflated into the panel content area
     */
    abstract fun createView(container: ViewGroup): View

    /**
     * Called when the feature panel is displayed
     * Use this to start any background tasks or initialize data
     */
    open fun onStart() {
        // Override in subclasses if needed
    }

    /**
     * Called when the feature panel is hidden/removed
     * Use this to stop background tasks and clean up resources
     */
    open fun onStop() {
        // Override in subclasses if needed
    }

    /**
     * Called when the feature needs to refresh its data
     */
    open fun onRefresh() {
        // Override in subclasses if needed
    }

    /**
     * Called when the app is paused
     */
    open fun onPause() {
        // Override in subclasses if needed
    }

    /**
     * Called when the app is resumed
     */
    open fun onResume() {
        // Override in subclasses if needed
    }

    /**
     * Handle permission results
     */
    open fun onPermissionResult(permission: String, granted: Boolean) {
        // Override in subclasses if needed
    }

    /**
     * Get feature title
     */
    abstract fun getTitle(): String

    /**
     * Get feature description
     */
    abstract fun getDescription(): String
}
