// FILE: app/src/main/java/com/sanjog/pdfscrollreader/ui/util/AnimationUtils.kt
package com.sanjog.pdfscrollreader.ui.util

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

object AnimationUtils {

    fun View.springScaleIn(delay: Long = 0) {
        postDelayed({
            scaleX = 0.85f
            scaleY = 0.85f
            alpha = 0f

            val springX = SpringAnimation(this, SpringAnimation.SCALE_X, 1f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            val springY = SpringAnimation(this, SpringAnimation.SCALE_Y, 1f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }

            animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            springX.start()
            springY.start()
        }, delay)
    }

    fun View.pulseAnimation(): ObjectAnimator {
        return ObjectAnimator.ofFloat(this, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun View.slideUpReveal(durationMs: Long = 300) {
        translationY = 60f
        alpha = 0f
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    fun View.glowEffect(color: Int) {
        val originalElevation = translationZ
        animate().translationZ(originalElevation + 8f)
            .setDuration(150)
            .withEndAction {
                animate().translationZ(originalElevation)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    fun RecyclerView.staggeredFadeIn(startDelay: Long = 0, staggerDelay: Long = 50) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child?.alpha = 0f
            child?.animate()
                ?.alpha(1f)
                ?.setDuration(250)
                ?.setStartDelay(startDelay + i * staggerDelay)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.start()
        }
    }

    fun View.slideInFromLeft(durationMs: Long = 300) {
        translationX = (-width).toFloat()
        alpha = 0f
        animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    fun View.fadeOutThenGone(durationMs: Long = 250) {
        animate()
            .alpha(0f)
            .setDuration(durationMs)
            .withEndAction { visibility = View.GONE }
            .start()
    }

    fun View.fadeIn(durationMs: Long = 250) {
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}
