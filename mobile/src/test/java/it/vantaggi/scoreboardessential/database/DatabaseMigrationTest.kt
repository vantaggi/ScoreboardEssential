package it.vantaggi.scoreboardessential.database

import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P])
class DatabaseMigrationTest {

@get:Rule
val helper: MigrationTestHelper = MigrationTestHelper(
InstrumentationRegistry.getInstrumentation(),
AppDatabase::class.java,
emptyList(),
FrameworkSQLiteOpenHelperFactory()
)

@Test
@Throws(IOException::class)
fun migrate8To9_containsCorrectIndices() {
// Create the database with version 8
helper.createDatabase(TEST_DB, 8).apply {
execSQL("INSERT INTO roles VALUES (1, 'Portiere', 'PORTA')")
execSQL("INSERT INTO players VALUES (1, 'Test Player', 0, 0)")
execSQL("INSERT INTO player_role_cross_ref VALUES (1, 1)")
close()
}

// Re-open the database with version 9 and provide MIGRATION_8_9
helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).apply {
// Query to check if indices exist
query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='player_role_cross_ref'").use { cursor ->
val indices = mutableListOf<String>()
while (cursor.moveToNext()) {
indices.add(cursor.getString(0))
}

// Verify all three indices exist
assert(indices.any { it.contains("playerId") })
assert(indices.any { it.contains("roleId") })
assert(indices.any { it.contains("playerId_roleId") })
}
close()
}
}

companion object {
private const val TEST_DB = "migration-test"
}
}
