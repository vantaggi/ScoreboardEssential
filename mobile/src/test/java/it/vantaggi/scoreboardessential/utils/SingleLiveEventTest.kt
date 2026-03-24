package it.vantaggi.scoreboardessential.utils

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SingleLiveEventTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var singleLiveEvent: SingleLiveEvent<Int>
    private lateinit var owner: TestLifecycleOwner
    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        singleLiveEvent = SingleLiveEvent()
        owner = TestLifecycleOwner()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        observer = mock(Observer::class.java) as Observer<Int>
    }

    @Test
    fun `value set notifies observer`() {
        singleLiveEvent.observe(owner, observer)
        singleLiveEvent.value = 1
        verify(observer).onChanged(1)
    }

    @Test
    fun `value set before observing notifies observer`() {
        singleLiveEvent.value = 1
        singleLiveEvent.observe(owner, observer)
        verify(observer).onChanged(1)
    }

    @Test
    fun `only one observer is notified when multiple observers are registered`() {
        val observer2 = mock(Observer::class.java) as Observer<Int>
        singleLiveEvent.observe(owner, observer)
        singleLiveEvent.observe(owner, observer2)

        singleLiveEvent.value = 1

        // Only one of them should be notified.
        // LiveData notifies in order, so 'observer' should be notified and 'observer2' not.
        verify(observer).onChanged(1)
        verify(observer2, never()).onChanged(1)
    }

    @Test
    fun `observer is not notified again on lifecycle changes after consumption`() {
        singleLiveEvent.observe(owner, observer)
        singleLiveEvent.value = 1
        verify(observer, times(1)).onChanged(1)

        // Simulate lifecycle change (e.g., rotation)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Should NOT be notified again
        verify(observer, times(1)).onChanged(1)
    }

    @Test
    fun `call notifies observer with null`() {
        val nullableEvent = SingleLiveEvent<Int?>()
        val nullableObserver = mock(Observer::class.java) as Observer<Int?>
        nullableEvent.observe(owner, nullableObserver)
        nullableEvent.call()
        verify(nullableObserver).onChanged(null)
    }

    @Test
    fun `subsequent value sets notify observer again`() {
        singleLiveEvent.observe(owner, observer)

        singleLiveEvent.value = 1
        verify(observer).onChanged(1)

        singleLiveEvent.value = 2
        verify(observer).onChanged(2)
    }

    @Test
    fun `value set after consumption notifies new observer`() {
        singleLiveEvent.observe(owner, observer)
        singleLiveEvent.value = 1
        verify(observer).onChanged(1)

        val observer2 = mock(Observer::class.java) as Observer<Int>
        singleLiveEvent.observe(owner, observer2)

        // observer2 should not be notified of the old value
        verify(observer2, never()).onChanged(1)

        singleLiveEvent.value = 2
        // Since both observers are observing the underlying MutableLiveData,
        // and pending flag is shared among them,
        // only one observer should be triggered when value changes to 2.
        // Therefore observer will be triggered and observer2 will NOT be triggered.
        verify(observer).onChanged(2)
        verify(observer2, never()).onChanged(2)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun handleLifecycleEvent(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }
}
