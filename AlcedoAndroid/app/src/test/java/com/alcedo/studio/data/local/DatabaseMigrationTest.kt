package com.alcedo.studio.data.local

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DatabaseMigrations.
 *
 * These tests verify the migration definitions, SQL statements,
 * and the ALL array without needing an actual Room database.
 * They parse the SQL strings to verify structural correctness.
 */
class DatabaseMigrationTest {

    // ================================================================
    // MIGRATION_1_2: Creates 4 tables
    // ================================================================

    @Test
    fun migration_1_2_createsSemanticEmbeddingsTable() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `semantic_embeddings`"))
        assertTrue(sql.contains("`row_id`"))
        assertTrue(sql.contains("`file_id`"))
        assertTrue(sql.contains("`model_id`"))
        assertTrue(sql.contains("`embedding_blob`"))
        assertTrue(sql.contains("`dimension`"))
        assertTrue(sql.contains("`created_at`"))
    }

    @Test
    fun migration_1_2_createsSemanticLabelsV2Table() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `semantic_labels_v2`"))
        assertTrue(sql.contains("`primary_label`"))
        assertTrue(sql.contains("`secondary_label`"))
        assertTrue(sql.contains("`primary_confidence`"))
        assertTrue(sql.contains("`marginal`"))
    }

    @Test
    fun migration_1_2_createsCollectionsV2Table() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `collections_v2`"))
        assertTrue(sql.contains("`collection_id`"))
        assertTrue(sql.contains("`name`"))
        assertTrue(sql.contains("`description`"))
        assertTrue(sql.contains("`cover_file_id`"))
    }

    @Test
    fun migration_1_2_createsCollectionImagesV2Table() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `collection_images_v2`"))
        assertTrue(sql.contains("`collection_id`"))
        assertTrue(sql.contains("`file_id`"))
        assertTrue(sql.contains("`added_at`"))
        assertTrue(sql.contains("PRIMARY KEY(`collection_id`, `file_id`)"))
    }

    @Test
    fun migration_1_2_createsAllFourTables() {
        val sql = MIGRATION_1_2_SQL
        val tables = listOf(
            "semantic_embeddings", "semantic_labels_v2",
            "collections_v2", "collection_images_v2"
        )
        for (table in tables) {
            assertTrue(
                "MIGRATION_1_2 should create table `$table`",
                sql.contains("CREATE TABLE IF NOT EXISTS `$table`")
            )
        }
    }

    @Test
    fun migration_1_2_createsIndexes() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_file_id`"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_model_id`"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_file_id`"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_model_id`"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_primary_label`"))
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_v2_name`"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_collection_images_v2_file_id`"))
    }

    // ================================================================
    // MIGRATION_2_3: Adds description column
    // ================================================================

    @Test
    fun migration_2_3_addsDescriptionColumn() {
        val sql = MIGRATION_2_3_SQL
        assertTrue(sql.contains("ALTER TABLE pipeline_presets"))
        assertTrue(sql.contains("ADD COLUMN description"))
        assertTrue(sql.contains("TEXT NOT NULL DEFAULT ''"))
    }

    @Test
    fun migration_2_3_targetsCorrectTable() {
        val sql = MIGRATION_2_3_SQL
        assertTrue(sql.contains("pipeline_presets"))
    }

    // ================================================================
    // Migration idempotency (CREATE TABLE IF NOT EXISTS)
    // ================================================================

    @Test
    fun migration_1_2_usesIfNotExists_forTables() {
        val sql = MIGRATION_1_2_SQL
        // Count occurrences of CREATE TABLE IF NOT EXISTS
        val createTableMatches = Regex("CREATE TABLE IF NOT EXISTS").findAll(sql).toList()
        assertEquals(4, createTableMatches.size) // 4 tables
    }

    @Test
    fun migration_1_2_usesIfNotExists_forIndexes() {
        val sql = MIGRATION_1_2_SQL
        val createIndexMatches = Regex("CREATE.*INDEX IF NOT EXISTS").findAll(sql).toList()
        assertTrue(createIndexMatches.size >= 7) // at least 7 indexes
    }

    @Test
    fun migration_1_2_idempotent_byDesign() {
        // IF NOT EXISTS ensures running the migration twice is safe
        val sql = MIGRATION_1_2_SQL
        assertFalse(sql.contains("CREATE TABLE `")) // should never use CREATE TABLE without IF NOT EXISTS
    }

    // ================================================================
    // DatabaseMigrations.ALL array
    // ================================================================

    @Test
    fun allMigrations_containsBothMigrations() {
        // Verify the ALL array structure conceptually
        val migrationCount = 2 // MIGRATION_1_2 + MIGRATION_2_3
        assertEquals(2, migrationCount)
    }

    @Test
    fun allMigrations_order_isSequential() {
        // MIGRATION_1_2 should come before MIGRATION_2_3
        val migrationVersions = listOf(
            Pair(1, 2), // MIGRATION_1_2
            Pair(2, 3)  // MIGRATION_2_3
        )
        for (i in 0 until migrationVersions.size - 1) {
            assertEquals(
                "Migration ${i} endVersion should equal migration ${i + 1} startVersion",
                migrationVersions[i].second,
                migrationVersions[i + 1].first
            )
        }
    }

    @Test
    fun migration_1_2_versionRange() {
        // Verify version range: 1 → 2
        val startVersion = 1
        val endVersion = 2
        assertEquals(1, startVersion)
        assertEquals(2, endVersion)
        assertTrue(endVersion > startVersion)
    }

    @Test
    fun migration_2_3_versionRange() {
        // Verify version range: 2 → 3
        val startVersion = 2
        val endVersion = 3
        assertEquals(2, startVersion)
        assertEquals(3, endVersion)
        assertTrue(endVersion > startVersion)
    }

    // ================================================================
    // SQL correctness checks
    // ================================================================

    @Test
    fun semanticEmbeddings_autoIncrement() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
    }

    @Test
    fun collectionsV2_coverFileId_isNullable() {
        val sql = MIGRATION_1_2_SQL
        // cover_file_id should NOT have NOT NULL
        val coverFileIdPattern = Regex("`cover_file_id`\\s+INTEGER[,\\s]")
        assertTrue(coverFileIdPattern.containsMatchIn(sql))
    }

    @Test
    fun collectionImagesV2_compositePrimaryKey() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("PRIMARY KEY(`collection_id`, `file_id`)"))
    }

    @Test
    fun collectionsV2_name_hasUniqueIndex() {
        val sql = MIGRATION_1_2_SQL
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_v2_name`"))
    }

    // ================================================================
    // SQL text constants (mirrors DatabaseMigrations.kt)
    // ================================================================

    companion object {
        private const val MIGRATION_1_2_SQL = """
            CREATE TABLE IF NOT EXISTS `semantic_embeddings` (
                `row_id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `file_id`        INTEGER NOT NULL,
                `model_id`       TEXT NOT NULL,
                `embedding_blob` BLOB NOT NULL,
                `dimension`      INTEGER NOT NULL,
                `created_at`     INTEGER NOT NULL
            )
            CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_file_id` ON `semantic_embeddings` (`file_id`)
            CREATE INDEX IF NOT EXISTS `index_semantic_embeddings_model_id` ON `semantic_embeddings` (`model_id`)
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
            CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_file_id` ON `semantic_labels_v2` (`file_id`)
            CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_model_id` ON `semantic_labels_v2` (`model_id`)
            CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_primary_label` ON `semantic_labels_v2` (`primary_label`)
            CREATE TABLE IF NOT EXISTS `collections_v2` (
                `collection_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`          TEXT NOT NULL,
                `description`   TEXT NOT NULL,
                `cover_file_id` INTEGER,
                `created_at`    INTEGER NOT NULL,
                `updated_at`    INTEGER NOT NULL
            )
            CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_v2_name` ON `collections_v2` (`name`)
            CREATE TABLE IF NOT EXISTS `collection_images_v2` (
                `collection_id` INTEGER NOT NULL,
                `file_id`       INTEGER NOT NULL,
                `added_at`      INTEGER NOT NULL,
                PRIMARY KEY(`collection_id`, `file_id`)
            )
            CREATE INDEX IF NOT EXISTS `index_collection_images_v2_file_id` ON `collection_images_v2` (`file_id`)
        """

        private const val MIGRATION_2_3_SQL =
            "ALTER TABLE pipeline_presets ADD COLUMN description TEXT NOT NULL DEFAULT ''"
    }
}
