package com.example.scoreboardessential.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.TextView
import com.example.scoreboardessential.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

fun TextView.playEnhancedScoreAnimation() {
    val context = this.context

    // Animazione complessa multi-step
    val animatorSet = AnimatorSet()

    // Step 1: Pump up
    val scaleUpX = ObjectAnimator.ofFloat(this, "scaleX", 1.0f, 1.4f)
    val scaleUpY = ObjectAnimator.ofFloat(this, "scaleY", 1.0f, 1.4f)
    scaleUpX.duration = 150
    scaleUpY.duration = 150

    // Step 2: Colore glitch
    val originalColor = this.currentTextColor
    val colors = intArrayOf(
        originalColor,
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, "Error"),
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, "Error"),
        originalColor
    )
    val colorAnimator = ValueAnimator.ofArgb(*colors).apply {
        duration = 400
        addUpdateListener { animator ->
            this@playEnhancedScoreAnimation.setTextColor(animator.animatedValue as Int)
        }
    }

    // Step 3: Bounce back
    val scaleDownX = ObjectAnimator.ofFloat(this, "scaleX", 1.4f, 0.9f, 1.0f)
    val scaleDownY = ObjectAnimator.ofFloat(this, "scaleY", 1.4f, 0.9f, 1.0f)
    scaleDownX.duration = 250
    scaleDownY.duration = 250
    scaleDownX.interpolator = BounceInterpolator()
    scaleDownY.interpolator = BounceInterpolator()

    // Step 4: Rotation wiggle
    val rotation = ObjectAnimator.ofFloat(this, "rotation", 0f, -5f, 5f, -3f, 3f, 0f)
    rotation.duration = 300

    // Play insieme
    animatorSet.play(scaleUpX).with(scaleUpY)
    animatorSet.play(colorAnimator).after(scaleUpX)
    animatorSet.play(scaleDownX).with(scaleDownY).after(100)
    animatorSet.play(rotation).after(scaleDownX)

    animatorSet.start()
}

fun MaterialCardView.pulseAnimation() {
    val animator = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f, 1.0f),
        PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f, 1.0f),
        PropertyValuesHolder.ofFloat("cardElevation",
            this.cardElevation,
            this.cardElevation + 8f,
            this.cardElevation)
    )
    animator.duration = 600
    animator.interpolator = AccelerateDecelerateInterpolator()
    animator.repeatCount = ValueAnimator.INFINITE
    animator.start()
}
