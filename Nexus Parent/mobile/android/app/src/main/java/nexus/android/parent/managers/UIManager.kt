package nexus.android.parent.managers

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * UIManager - Manages UI state and transitions
 * Handles smooth animations and view visibility changes
 */
class UIManager {

    companion object {
        private const val ANIMATION_DURATION = 300L
    }

    /**
     * Fade in animation for views
     */
    fun fadeIn(view: View, duration: Long = ANIMATION_DURATION) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Fade out animation for views
     */
    fun fadeOut(view: View, duration: Long = ANIMATION_DURATION, onComplete: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.visibility = View.GONE
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * Slide in from right animation
     */
    fun slideInFromRight(view: View, duration: Long = ANIMATION_DURATION) {
        view.translationX = view.width.toFloat()
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, "translationX", view.width.toFloat(), 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Slide out to right animation
     */
    fun slideOutToRight(view: View, duration: Long = ANIMATION_DURATION, onComplete: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, view.width.toFloat()).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.visibility = View.GONE
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * Show view with animation
     */
    fun show(view: View, animated: Boolean = true) {
        if (animated) {
            fadeIn(view)
        } else {
            view.visibility = View.VISIBLE
        }
    }

    /**
     * Hide view with animation
     */
    fun hide(view: View, animated: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (animated) {
            fadeOut(view, onComplete = onComplete)
        } else {
            view.visibility = View.GONE
            onComplete?.invoke()
        }
    }
}
