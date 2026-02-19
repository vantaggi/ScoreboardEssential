package it.vantaggi.scoreboardessential.ui

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class ScoreGestureListener(
    private val onIncreaseScore: () -> Unit,
    private val onDecreaseScore: () -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        e1?.let {
            val diffY = e2.y - it.y
            if (abs(diffY) > 100) {
                if (diffY < 0) {
                    onIncreaseScore()
                } else {
                    onDecreaseScore()
                }
                return true
            }
        }
        return false
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        onIncreaseScore()
        return true
    }
}
