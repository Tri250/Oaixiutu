package com.alcedo.studio.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SleeveDatabase::class.java
    )

    @Test
    fun migration1To2PreservesData() {
        // Create v1 database
        val db = migrationTestHelper.createDatabase("test-db", 1)
        // Insert some data into v1 tables
        db.execSQL(
            "INSERT INTO sleeve_elements (element_id, element_name, element_type, parent_id, added_time, last_modified_time) " +
            "VALUES (1, 'TestFile', 1, 0, 1000, 1000)"
        )
        db.close()

        // Run migration 1->2
        val migratedDb = migrationTestHelper.runMigrationsAndValidate("test-db", 2, true, DatabaseMigrations.MIGRATION_1_2)

        // Verify v2 tables were created
        val cursor = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('semantic_embeddings', 'semantic_labels_v2', 'collections_v2', 'collection_images_v2')")
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        assertEquals("All 4 v2 tables should exist", 4, tables.size)

        // Verify v1 data is preserved
        val v1Cursor = migratedDb.query("SELECT element_name FROM sleeve_elements WHERE element_id = 1")
        assertTrue("v1 data should still exist", v1Cursor.moveToFirst())
        assertEquals("TestFile", v1Cursor.getString(0))
        v1Cursor.close()
        migratedDb.close()
    }

    @Test
    fun migration2To3AddsDescriptionColumn() {
        // Create v2 database
        val db = migrationTestHelper.createDatabase("test-db-v2", 2)
        // Create pipeline_presets table as it would exist in v2
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `pipeline_presets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `params_json` TEXT NOT NULL,
                `created_time` INTEGER NOT NULL,
                `is_built_in` INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            "INSERT INTO pipeline_presets (name, category, params_json, created_time, is_built_in) " +
            "VALUES ('TestPreset', 'Film', '{\"exposure\":0.5}', 1000, 1)"
        )
        db.close()

        // Run migration 2->3
        val migratedDb = migrationTestHelper.runMigrationsAndValidate("test-db-v2", 3, true, DatabaseMigrations.MIGRATION_2_3)

        // Verify description column exists with default value
        val cursor = migratedDb.query("SELECT description FROM pipeline_presets WHERE name = 'TestPreset'")
        assertTrue("Row should exist", cursor.moveToFirst())
        assertEquals("Default description should be empty", "", cursor.getString(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun allMigrationsAreConsistent() {
        val allMigrations = DatabaseMigrations.ALL
        assertEquals("Should have 2 migrations", 2, allMigrations.size)
        assertEquals("First migration start version", 1, allMigrations[0].startVersion)
        assertEquals("First migration end version", 2, allMigrations[0].endVersion)
        assertEquals("Second migration start version", 2, allMigrations[1].startVersion)
        assertEquals("Second migration end version", 3, allMigrations[1].endVersion)
    }
}
