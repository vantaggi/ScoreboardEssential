package it.vantaggi.scoreboardessential.ui

import android.view.MotionEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoreGestureListenerTest {

    private val onIncreaseScore: () -> Unit = mock()
    private val onDecreaseScore: () -> Unit = mock()
    private val listener = ScoreGestureListener(onIncreaseScore, onDecreaseScore)

    @Test
    fun `onDoubleTap calls onIncreaseScore`() {
        val event = mock<MotionEvent>()
        listener.onDoubleTap(event)
        verify(onIncreaseScore).invoke()
        verifyNoInteractions(onDecreaseScore)
    }

    @Test
    fun `onFling up calls onIncreaseScore`() {
        // Swipe UP: Y decreases
        val e1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 200f, 0)
        val e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 50f, 0)
        // diffY = 50 - 200 = -150 (abs > 100, negative -> UP)

        listener.onFling(e1, e2, 0f, 0f)

        verify(onIncreaseScore).invoke()
        verifyNoInteractions(onDecreaseScore)
    }

    @Test
    fun `onFling down calls onDecreaseScore`() {
        // Swipe DOWN: Y increases
        val e1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 50f, 0)
        val e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 200f, 0)
        // diffY = 200 - 50 = 150 (abs > 100, positive -> DOWN)

        listener.onFling(e1, e2, 0f, 0f)

        verify(onDecreaseScore).invoke()
        verifyNoInteractions(onIncreaseScore)
    }

    @Test
    fun `onFling with small distance does nothing`() {
        val e1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 100f, 0)
        val e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 150f, 0)
        // diffY = 150 - 100 = 50 (abs < 100)

        listener.onFling(e1, e2, 0f, 0f)

        verifyNoInteractions(onIncreaseScore)
        verifyNoInteractions(onDecreaseScore)
    }

    @Test
    fun `onFling with null e1 does nothing`() {
        val e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 150f, 0)

        listener.onFling(null, e2, 0f, 0f)

        verifyNoInteractions(onIncreaseScore)
        verifyNoInteractions(onDecreaseScore)
    }
}
