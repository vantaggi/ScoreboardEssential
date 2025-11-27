package it.vantaggi.scoreboardessential.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
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
    val colors =
        intArrayOf(
            originalColor,
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, "Error"),
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, "Error"),
            originalColor,
        )
    val colorAnimator =
        ValueAnimator.ofArgb(*colors).apply {
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

fun TextView.playNativeGoalAnimation() {
    val context = this.context

    // 1. Animazione "Pump" (ingrandisce e rimpicciolisce con effetto overshoot)
    val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.5f, 1f)
    val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.5f, 1f)
    scaleX.duration = 400
    scaleY.duration = 400
    scaleX.interpolator = OvershootInterpolator()
    scaleY.interpolator = OvershootInterpolator()

    // 2. Animazione "Flash" del colore - più vivace
    val originalColor = this.currentTextColor
    val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    val secondaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary)

    val colorAnimator =
        ValueAnimator.ofArgb(originalColor, primaryColor, secondaryColor, originalColor).apply {
            duration = 400
            addUpdateListener { animator ->
                this@playNativeGoalAnimation.setTextColor(animator.animatedValue as Int)
            }
        }

    // 3. Leggera rotazione per dinamismo
    val rotation = ObjectAnimator.ofFloat(this, "rotation", 0f, -8f, 8f, 0f)
    rotation.duration = 300

    // 4. Combinare le animazioni con AnimatorSet
    AnimatorSet().apply {
        playTogether(scaleX, scaleY, colorAnimator)
        play(rotation).after(100) // Rotazione leggermente ritardata
        start()
    }
}

fun MaterialCardView.pulseAnimation() {
    val animator =
        ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f, 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f, 1.0f),
            PropertyValuesHolder.ofFloat(
                "cardElevation",
                this.cardElevation,
                this.cardElevation + 8f,
                this.cardElevation,
            ),
        )
    animator.duration = 600
    animator.interpolator = AccelerateDecelerateInterpolator()
    animator.repeatCount = ValueAnimator.INFINITE
    animator.start()
}
