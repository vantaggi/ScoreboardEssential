package it.vantaggi.scoreboardessential

import android.widget.EditText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.database.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddEditPlayerActivityTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        // Create in-memory database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        // Inject database into singleton using reflection
        setDatabaseInstance(database)
    }

    @After
    fun tearDown() {
        database.close()
        // Reset singleton
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        try {
            // Try setting on AppDatabase class (static field)
            val instanceField = AppDatabase::class.java.getDeclaredField("instance")
            instanceField.isAccessible = true
            instanceField.set(null, instance)
        } catch (e: NoSuchFieldException) {
            // Fallback: try setting on Companion object
            val companionClass = AppDatabase.Companion::class.java
            val instanceField = companionClass.getDeclaredField("instance")
            instanceField.isAccessible = true
            // Companion object instance is needed if it's not static
            // But usually 'instance' inside companion is compiled to static on outer class or field on companion instance.
            // Accessing companion instance:
            val companionInstance = AppDatabase.Companion
            instanceField.set(companionInstance, instance)
        } catch (e: Exception) {
            throw RuntimeException("Failed to inject database mock", e)
        }
    }

    @Test
    fun savePlayer_withTooLongName_showsErrorAndDoesNotFinish() {
        val controller = Robolectric.buildActivity(AddEditPlayerActivity::class.java)
        val activity = controller.create().start().resume().visible().get()

        val nameInput = activity.findViewById<EditText>(R.id.player_name_input)

        // 31 chars
        val longName = "1234567890123456789012345678901"
        nameInput.setText(longName)

        // Trigger save using reflection
        val saveMethod = AddEditPlayerActivity::class.java.getDeclaredMethod("savePlayer")
        saveMethod.isAccessible = true
        saveMethod.invoke(activity)

        // Assert activity did NOT finish
        // Before fix: This assertion should fail because save proceeds and activity finishes
        assertEquals("Activity should not finish (save should fail)", false, activity.isFinishing)

        // Assert Toast
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertEquals("Player name is too long (max 30 chars)", latestToast)
    }
}
