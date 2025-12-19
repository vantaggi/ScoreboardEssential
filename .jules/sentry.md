## 2024-05-23 - [Initial Setup]
**Learning:** Sentry initialized. Standardizing test practices.
**Action:** Always verify test files exist for critical ViewModels and UseCases.

## 2024-05-23 - [Robolectric & Coroutines]
**Learning:** When testing suspend functions in Robolectric, ensure proper Dispatcher injection. `StandardTestDispatcher` is preferred.
**Action:** Use `runTest` and `advanceUntilIdle` to handle coroutine execution in tests.

## 2024-05-23 - [Service Binding in Robolectric]
**Learning:** `bindService` requires explicit shadow configuration in Robolectric. `shadowOf(application).setComponentNameAndServiceForBindService` is key.
**Action:** Always configure service shadows before ViewModel initialization if `bindService` is called in `init`.

## 2024-05-23 - [Mockito & Suspend Functions]
**Learning:** Mocking suspend functions that return primitives requires explicit stubbing, otherwise Mockito returns `null` (causing NPEs) instead of default primitives.
**Action:** Always stub suspend functions that return primitives, e.g., `whenever(repo.getVal()).thenReturn(0L)`.

## 2024-05-23 - [Service Connection Callbacks]
**Learning:** `onServiceConnected` callbacks run on the main looper. In `runTest`, use `shadowOf(Looper.getMainLooper()).idle()` to force execution of pending UI/Looper tasks that aren't coroutines.
**Action:** When testing service interactions or LiveData updates triggered by callbacks, idle the main looper.
