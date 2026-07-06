package com.alcedo.studio.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Centralized Room [Migration] definitions for [SleeveDatabase].
 *
 * The database is currently at version 2. Schema changes must be expressed as
 * explicit [Migration] objects here and registered via
 * `Room.databaseBuilder(...).addMigrations(...)`. Destructive fallback is only
 * permitted on downgrade (see [SleeveDatabase]); upgrades must always preserve
 * user data.
 *
 * Guidelines for adding a new migration:
 *   1. Bump `version` in the `@Database` annotation on [SleeveDatabase].
 *   2. Add a `MIGRATION_N_N+1` val below that issues the minimal set of
 *      `ALTER TABLE` / `CREATE TABLE` / `CREATE INDEX` statements required to
 *      transform the previous schema into the new one. Prefer `ALTER TABLE`
 *      over recreate-and-copy when possible, since it preserves rowids and
 *      avoids moving large blobs.
 *   3. Register the new migration in `addMigrations(...)` inside both
 *      `buildEncryptedDatabase` and `buildPlainDatabase`.
 *   4. If a column type changes or a table must be rebuilt, copy data with an
 *      `INSERT INTO new_table SELECT ... FROM old_table` statement and drop the
 *      old table.
 */
object DatabaseMigrations {

    /**
     * Migration from schema v1 to v2.
     *
     * v2 introduced the "desktop-compatible" schema variants alongside the
     * existing tables (see the `*V2` entities in `ImageModel.kt`):
     *   - `semantic_embeddings`     (per-file embedding blobs keyed by model)
     *   - `semantic_labels_v2`      (desktop-style primary/secondary labels)
     *   - `collections_v2`          (desktop-style collection metadata)
     *   - `collection_images_v2`    (desktop-style collection membership)
     *
     * All four tables are additive: they do not touch any v1 table, so all
     * existing user data (sleeve elements, files, folders, ratings, the
     * original `collections` / `collection_images` / `semantic_labels`) is
     * preserved untouched. `CREATE TABLE IF NOT EXISTS` is used so the
     * migration is idempotent if it ever runs against a partially-migrated
     * database (e.g. after a crashed upgrade).
     *
     * Column types and nullability match the entity definitions exactly so
     * Room's post-migration schema validation passes.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- semantic_embeddings -----------------------------------------
            // row_id is INTEGER PRIMARY KEY AUTOINCREMENT (autoGenerate on Long).
            // embedding_blob is a non-null BLOB (ByteArray with no default).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `semantic_embeddings` (
                    `row_id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `file_id`        INTEGER NOT NULL,
                    `model_id`       TEXT NOT NULL,
                    `embedding_blob` BLOB NOT NULL,
                    `dimension`      INTEGER NOT NULL,
                    `created_at`     INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_file_id` ON `semantic_embeddings` (`file_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_model_id` ON `semantic_embeddings` (`model_id`)")

            // --- semantic_labels_v2 -----------------------------------------
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `semantic_labels_v2` (
                    `row_id`            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `file_id`           INTEGER NOT NULL,
                    `model_id`          TEXT NOT NULL,
                    `primary_label`     TEXT NOT NULL,
                    `secondary_label`   TEXT NOT NULL,
                    `primary_confidence` REAL NOT NULL,
                    `marginal`          REAL NOT NULL,
                    `created_at`        INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_file_id` ON `semantic_labels_v2` (`file_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_model_id` ON `semantic_labels_v2` (`model_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_primary_label` ON `semantic_labels_v2` (`primary_label`)")

            // --- collections_v2 ---------------------------------------------
            // cover_file_id is nullable (Long?). name has a UNIQUE index.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collections_v2` (
                    `collection_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name`          TEXT NOT NULL,
                    `description`   TEXT NOT NULL,
                    `cover_file_id` INTEGER,
                    `created_at`    INTEGER NOT NULL,
                    `updated_at`    INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_v2_name` ON `collections_v2` (`name`)")

            // --- collection_images_v2 ---------------------------------------
            // Composite primary key (collection_id, file_id).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_images_v2` (
                    `collection_id` INTEGER NOT NULL,
                    `file_id`       INTEGER NOT NULL,
                    `added_at`      INTEGER NOT NULL,
                    PRIMARY KEY(`collection_id`, `file_id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_images_v2_file_id` ON `collection_images_v2` (`file_id`)")
        }
    }

    /**
     * Framework stub for the v2 -> v3 migration.
     *
     * Schema v3 is not yet defined, so this migration has no SQL to run. The
     * object is intentionally registered with [Migration] (not omitted) so
     * that:
     *   - bumping `version` to 3 without writing the migration body produces a
     *     loud, explicit failure rather than silently falling back to
     *     destructive migration, and
     *   - the registration site in [SleeveDatabase] already references it, so
     *     a future contributor only needs to fill in `migrate(...)` below.
     *
     * When v3 is introduced, replace the body of [migrate] with the required
     * `ALTER TABLE` / `CREATE TABLE` statements (see the pattern in
     * [MIGRATION_1_2]) and remove the `IllegalStateException`.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            throw IllegalStateException(
                "MIGRATION_2_3 has not been implemented yet. Schema v3 was " +
                "requested but no migration path from v2 was provided. " +
                "Implement DatabaseMigrations.MIGRATION_2_3#migrate(...) " +
                "before bumping SleeveDatabase to version 3."
            )
        }
    }

    /** All registered migrations, in order. Used by SleeveDatabase builders. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
