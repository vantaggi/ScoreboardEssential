package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserPreferencesRepository(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val hasSeenTutorial: Flow<Boolean> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_HAS_SEEN_TUTORIAL) {
                        trySend(hasSeenTutorial())
                    }
                }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            // Emit the initial value
            trySend(hasSeenTutorial())
            // Unregister the listener when the flow is closed
            awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    suspend fun setHasSeenTutorial(hasSeen: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_HAS_SEEN_TUTORIAL, hasSeen)
        }
    }

    private fun hasSeenTutorial(): Boolean = sharedPreferences.getBoolean(KEY_HAS_SEEN_TUTORIAL, false)

    companion object {
        private const val PREFERENCES_NAME = "user_preferences"
        private const val KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"
    }
}
