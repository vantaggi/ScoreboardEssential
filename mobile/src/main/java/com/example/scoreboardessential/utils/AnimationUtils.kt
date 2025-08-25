package com.example.scoreboardessential.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.widget.TextView
import com.google.android.material.R
import com.google.android.material.color.MaterialColors

fun TextView.playScoreChangeAnimation() {
    val context = this.context

    // "Snap" animation (scale up and down)
    val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1.0f, 1.5f, 1.0f)
    val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1.0f, 1.5f, 1.0f)
    scaleX.duration = 400
    scaleY.duration = 400

    // "Glitch" animation (flash color)
    val originalColor = this.currentTextColor
    val glitchColor = MaterialColors.getColor(context, R.attr.colorPrimary, "Error")

    val colorAnimator = ValueAnimator.ofArgb(originalColor, glitchColor, originalColor)
    colorAnimator.duration = 400
    colorAnimator.addUpdateListener { animator ->
        this.setTextColor(animator.animatedValue as Int)
    }

    // Play them together
    val animatorSet = AnimatorSet()
    animatorSet.playTogether(scaleX, scaleY, colorAnimator)
    animatorSet.start()
}
