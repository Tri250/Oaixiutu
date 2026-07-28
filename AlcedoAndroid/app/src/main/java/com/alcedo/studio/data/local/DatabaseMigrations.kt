package com.alcedo.studio.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration definitions for [SleeveDatabase]. The schema starts at v1; each
 * future migration is appended to [ALL] and wired into the database builder.
 * Currently there are no released migrations beyond v1, so [ALL] is empty — the
 * placeholder below documents the pattern for future schema changes.
 */
object DatabaseMigrations {

    /**
     * Example migration from v1 -> v2: add an EXIF ISO column to the images
     * table. Kept as a template; not added to [ALL] until the schema bumps.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE images ADD COLUMN isoNew INTEGER")
        }
    }

    /** All migrations to apply, in order. Empty until a new schema version ships. */
    val ALL: Array<Migration> = arrayOf(
        // Add MIGRATION_1_2 here when bumping VERSION to 2.
    )

    /** Pre-populate built-in presets and the root sleeve on first creation. */
    fun seedDefaults(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO sleeve_elements
                (id, parentId, name, sleevePath, isFolder, createdAt, modifiedAt,
                 imageId, childCount, imageCount, isSmartCollection, smartFilterJson)
            VALUES ('root', NULL, '/', '/', 1, $now, $now, NULL, 0, 0, 0, NULL)
            """.trimIndent(),
        )
    }
}
