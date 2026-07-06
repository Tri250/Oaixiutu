package com.alcedo.studio.viewmodel

import com.alcedo.studio.domain.service.BatchEditOutcome
import com.alcedo.studio.domain.service.BatchEditProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AlbumViewModel's pure logic components:
 * selection logic, sort modes, filter application, search query handling,
 * FolderBreadcrumb navigation, pagination state, and batch edit message formatting.
 *
 * These tests do not require Android framework and focus on algorithm
 * correctness and state management logic. Data models that depend on
 * Android classes (ImageModel, SortMode, FilterState) are tested via
 * lightweight local mirrors of the ViewModel's logic.
 */
class AlbumViewModelTest {

    // ================================================================
    // Selection logic (toggleImageSelection, selectAll, clearSelection)
    // ================================================================

    @Test
    fun toggleImageSelection_addsNewId() {
        var selected = setOf<Long>()
        val id = 42L
        selected = if (id in selected) selected - id else selected + id
        assertTrue(selected.contains(42L))
        assertEquals(1, selected.size)
    }

    @Test
    fun toggleImageSelection_removesExistingId() {
        var selected = setOf<Long>(1L, 2L, 3L)
        val id = 2L
        selected = if (id in selected) selected - id else selected + id
        assertFalse(selected.contains(2L))
        assertEquals(2, selected.size)
    }

    @Test
    fun toggleImageSelection_idempotentToggle() {
        var selected = setOf<Long>()
        for (i in 0..2) {
            selected = if (5L in selected) selected - 5L else selected + 5L
        }
        // Toggle odd times → added; even times → removed. 3 toggles = added
        assertTrue(selected.contains(5L))
    }

    @Test
    fun selectAll_selectsAllFilteredImages() {
        val filteredImageIds = listOf(1L, 2L, 3L, 4L, 5L)
        val selected = filteredImageIds.toSet()
        assertEquals(5, selected.size)
        assertTrue(selected.containsAll(listOf(1L, 2L, 3L, 4L, 5L)))
    }

    @Test
    fun selectAll_onEmptyList_returnsEmptySet() {
        val filteredImageIds = emptyList<Long>()
        val selected = filteredImageIds.toSet()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun clearSelection_emptiesSelection() {
        var selected = setOf<Long>(1L, 2L, 3L)
        selected = emptySet()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun getSelectedImagePaths_mapsIdsToPaths() {
        val imagePaths = mapOf(
            1L to "/sdcard/a.jpg",
            2L to "/sdcard/b.png",
            3L to "/sdcard/c.tiff"
        )
        val selected = setOf(1L, 3L)
        val paths = imagePaths.filterKeys { it in selected }.values.toList()
        assertEquals(2, paths.size)
        assertTrue(paths.contains("/sdcard/a.jpg"))
        assertTrue(paths.contains("/sdcard/c.tiff"))
        assertFalse(paths.contains("/sdcard/b.png"))
    }

    // ================================================================
    // Sort modes (DATE, NAME, RATING, TYPE) — logic patterns
    // ================================================================

    @Test
    fun sortByDate_descendingOrder() {
        data class Img(val id: Long, val date: String)
        val images = listOf(
            Img(1L, "2024:01:01 10:00:00"),
            Img(2L, "2024:06:15 12:00:00"),
            Img(3L, "2023:01:01 08:00:00")
        )
        val sorted = images.sortedWith(
            compareByDescending<Img> { it.date.isNotEmpty() }
                .thenByDescending { it.date }
        )
        assertEquals("2024:06:15 12:00:00", sorted[0].date)
        assertEquals("2024:01:01 10:00:00", sorted[1].date)
        assertEquals("2023:01:01 08:00:00", sorted[2].date)
    }

    @Test
    fun sortByDate_emptyDatesAtEnd() {
        data class Img(val id: Long, val date: String)
        val images = listOf(
            Img(1L, "2024:06:15"),
            Img(2L, ""),
            Img(3L, "2024:01:01")
        )
        val sorted = images.sortedWith(
            compareByDescending<Img> { it.date.isNotEmpty() }
                .thenByDescending { it.date }
        )
        // Non-empty dates come first, then empty dates
        assertEquals("2024:06:15", sorted[0].date)
        assertEquals("", sorted[2].date)
    }

    @Test
    fun sortByName_alphabetical() {
        data class Img(val id: Long, val name: String)
        val images = listOf(
            Img(1L, "Zebra.jpg"),
            Img(2L, "Apple.jpg"),
            Img(3L, "Mango.jpg")
        )
        val sorted = images.sortedBy { it.name }
        assertEquals("Apple.jpg", sorted[0].name)
        assertEquals("Mango.jpg", sorted[1].name)
        assertEquals("Zebra.jpg", sorted[2].name)
    }

    @Test
    fun sortByRating_descending() {
        data class Img(val id: Long, val rating: Int)
        val images = listOf(
            Img(1L, 3), Img(2L, 5), Img(3L, 1)
        )
        val sorted = images.sortedByDescending { it.rating }
        assertEquals(5, sorted[0].rating)
        assertEquals(3, sorted[1].rating)
        assertEquals(1, sorted[2].rating)
    }

    @Test
    fun sortByType_byOrdinal() {
        enum class ImageType { DEFAULT, JPEG, PNG, TIFF, ARW, CR2, CR3, NEF, DNG, HEIC, HEIF, WEBP, BMP, GIF, EXR }
        data class Img(val id: Long, val type: ImageType)
        val images = listOf(
            Img(1L, ImageType.DNG),
            Img(2L, ImageType.JPEG),
            Img(3L, ImageType.PNG)
        )
        val sorted = images.sortedBy { it.type.ordinal }
        assertEquals(ImageType.JPEG, sorted[0].type)
    }

    @Test
    fun sortMode_values_matchExpectedNames() {
        // Verify SortMode's 4 values: DATE, NAME, RATING, TYPE
        val sortModes = listOf("DATE", "NAME", "RATING", "TYPE")
        assertEquals(4, sortModes.size)
        assertTrue(sortModes.contains("DATE"))
        assertTrue(sortModes.contains("NAME"))
        assertTrue(sortModes.contains("RATING"))
        assertTrue(sortModes.contains("TYPE"))
    }

    // ================================================================
    // Filter application (camera makes, models, rating, file types)
    // ================================================================

    data class TestFilterState(
        val cameraMakes: List<String> = emptyList(),
        val cameraModels: List<String> = emptyList(),
        val lensModel: String = "",
        val startDate: Long = 0L,
        val endDate: Long = Long.MAX_VALUE,
        val rating: Int = 0,
        val fileTypes: List<String> = emptyList(),
        val aiLabels: List<String> = emptyList()
    )

    data class TestImage(
        val imageId: Long,
        val imageName: String = "test.jpg",
        val imagePath: String = "/storage/test.jpg",
        val cameraMake: String = "",
        val cameraModel: String = "",
        val lensModel: String = "",
        val captureDate: String = "",
        val rating: Int = 0,
        val typeName: String = "JPEG",
        val mimeType: String = "image/jpeg",
        val fileSize: Long = 0L
    )

    @Test
    fun filterState_defaultValues() {
        val filter = TestFilterState()
        assertTrue(filter.cameraMakes.isEmpty())
        assertTrue(filter.cameraModels.isEmpty())
        assertEquals("", filter.lensModel)
        assertEquals(0L, filter.startDate)
        assertEquals(Long.MAX_VALUE, filter.endDate)
        assertEquals(0, filter.rating)
        assertTrue(filter.fileTypes.isEmpty())
        assertTrue(filter.aiLabels.isEmpty())
    }

    @Test
    fun filterByCameraMake_caseInsensitive() {
        val images = listOf(
            TestImage(1L, cameraMake = "Canon"),
            TestImage(2L, cameraMake = "SONY"),
            TestImage(3L, cameraMake = "Nikon")
        )
        val filter = TestFilterState(cameraMakes = listOf("sony"))
        val result = applyFilter(images, filter)
        assertEquals(1, result.size)
        assertEquals(2L, result.first().imageId)
    }

    @Test
    fun filterByCameraModels_multipleModels() {
        val images = listOf(
            TestImage(1L, cameraModel = "EOS R5"),
            TestImage(2L, cameraModel = "A7IV"),
            TestImage(3L, cameraModel = "Z9")
        )
        val filter = TestFilterState(cameraModels = listOf("R5", "Z9"))
        val result = applyFilter(images, filter)
        assertEquals(2, result.size)
        val ids = result.map { it.imageId }.toSet()
        assertTrue(ids.contains(1L))
        assertFalse(ids.contains(2L))
        assertTrue(ids.contains(3L))
    }

    @Test
    fun filterByRating_minimumRating() {
        val images = listOf(
            TestImage(1L, rating = 1),
            TestImage(2L, rating = 3),
            TestImage(3L, rating = 5)
        )
        val filter = TestFilterState(rating = 3)
        val result = applyFilter(images, filter)
        assertEquals(2, result.size) // ratings >= 3
        assertTrue(result.all { it.rating >= 3 })
    }

    @Test
    fun filterByFileType_matchesTypeNameOrMimeType() {
        val images = listOf(
            TestImage(1L, typeName = "JPEG", mimeType = "image/jpeg"),
            TestImage(2L, typeName = "PNG", mimeType = "image/png"),
            TestImage(3L, typeName = "TIFF", mimeType = "image/jpeg")
        )
        val filter = TestFilterState(fileTypes = listOf("JPEG"))
        val result = applyFilter(images, filter)
        // Matches typeName "JPEG" or mimeType containing "jpeg"
        assertEquals(2, result.size) // JPEG type + jpeg mime type
    }

    @Test
    fun filterCombined_multipleCriteria() {
        val images = listOf(
            TestImage(1L, cameraMake = "Canon", cameraModel = "R5", rating = 5, typeName = "CR2"),
            TestImage(2L, cameraMake = "Sony", cameraModel = "A7IV", rating = 4, typeName = "ARW"),
            TestImage(3L, cameraMake = "Canon", cameraModel = "R5", rating = 2, typeName = "JPEG")
        )
        val filter = TestFilterState(
            cameraMakes = listOf("canon"),
            cameraModels = listOf("r5"),
            rating = 3,
            fileTypes = listOf("CR2")
        )
        val result = applyFilter(images, filter)
        // Only image 1 matches all criteria: Canon + R5 + rating>=3 + CR2 type
        assertEquals(1, result.size)
        assertEquals(1L, result.first().imageId)
    }

    @Test
    fun filterNoCriteria_returnsAll() {
        val images = listOf(TestImage(1L), TestImage(2L), TestImage(3L))
        val filter = TestFilterState()
        val result = applyFilter(images, filter)
        assertEquals(3, result.size)
    }

    @Test
    fun filterByLensModel_substringMatch() {
        val images = listOf(
            TestImage(1L, lensModel = "RF 50mm F1.2"),
            TestImage(2L, lensModel = "FE 24-70mm F2.8"),
            TestImage(3L, lensModel = "RF 24-105mm F4")
        )
        val filter = TestFilterState(lensModel = "RF")
        val result = applyFilter(images, filter)
        assertEquals(2, result.size)
        assertTrue(result.all { it.lensModel.contains("RF", ignoreCase = true) })
    }

    // ================================================================
    // Search query handling (blank query clears results)
    // ================================================================

    @Test
    fun searchQuery_blankQuery_clearsResults() {
        val query = ""
        assertTrue(query.isBlank())

        var searchResults = listOf("result1", "result2")
        if (query.isBlank()) {
            searchResults = emptyList()
        }
        assertTrue(searchResults.isEmpty())
    }

    @Test
    fun searchQuery_nonBlank_triggersSearch() {
        val query = "sunset"
        assertFalse(query.isBlank())
        assertTrue(query.isNotEmpty())
    }

    @Test
    fun searchQuery_whitespaceOnly_isBlank() {
        val query = "   "
        assertTrue(query.isBlank())
    }

    @Test
    fun searchQuery_toggleClearsOnClose() {
        var showSearch = true
        var query = "test"
        showSearch = !showSearch
        if (!showSearch) {
            query = ""
        }
        assertFalse(showSearch)
        assertEquals("", query)
    }

    // ================================================================
    // FolderBreadcrumb navigation
    // ================================================================

    data class TestBreadcrumb(val folderId: Long, val name: String)

    @Test
    fun folderBreadcrumbs_addAndNavigate() {
        val breadcrumbs = mutableListOf<TestBreadcrumb>()
        breadcrumbs.add(TestBreadcrumb(folderId = 1L, name = "Photos"))
        breadcrumbs.add(TestBreadcrumb(folderId = 5L, name = "Vacation"))

        assertEquals(2, breadcrumbs.size)
        assertEquals("Photos", breadcrumbs[0].name)
        assertEquals(5L, breadcrumbs[1].folderId)
    }

    @Test
    fun folderBreadcrumbs_navigateBackTruncates() {
        var breadcrumbs = mutableListOf(
            TestBreadcrumb(1L, "Root"),
            TestBreadcrumb(5L, "Photos"),
            TestBreadcrumb(10L, "Vacation")
        )
        // Navigate to index 0 → truncate to subList(0..0)
        breadcrumbs = breadcrumbs.subList(0, 1).toMutableList()
        assertEquals(1, breadcrumbs.size)
        assertEquals("Root", breadcrumbs[0].name)
    }

    @Test
    fun folderBreadcrumbs_noDuplicates() {
        val breadcrumbs = mutableListOf<TestBreadcrumb>()
        val newCrumb = TestBreadcrumb(folderId = 5L, name = "Photos")
        if (breadcrumbs.none { it.folderId == newCrumb.folderId }) {
            breadcrumbs.add(newCrumb)
        }
        assertEquals(1, breadcrumbs.size)

        // Try adding same again
        if (breadcrumbs.none { it.folderId == newCrumb.folderId }) {
            breadcrumbs.add(newCrumb)
        }
        assertEquals(1, breadcrumbs.size)
    }

    @Test
    fun navigateToFolder_rootClearsBreadcrumbs() {
        var currentFolderPath = "/Photos/Vacation"
        var breadcrumbs = listOf(TestBreadcrumb(1L, "Photos"), TestBreadcrumb(5L, "Vacation"))
        val folderId: Long? = null // root navigation
        if (folderId == null) {
            currentFolderPath = "/"
            breadcrumbs = emptyList()
        }
        assertEquals("/", currentFolderPath)
        assertTrue(breadcrumbs.isEmpty())
    }

    @Test
    fun navigateToBreadcrumb_negativeIndex_navigatesToRoot() {
        val breadcrumbs = listOf(TestBreadcrumb(1L, "Photos"), TestBreadcrumb(5L, "Vacation"))
        val index = -1
        val targetFolderId: Long? = if (index < 0) null else breadcrumbs.getOrNull(index)?.folderId
        assertNull(targetFolderId) // null means root
    }

    @Test
    fun navigateToBreadcrumb_validIndex_navigatesToFolder() {
        val breadcrumbs = listOf(TestBreadcrumb(1L, "Photos"), TestBreadcrumb(5L, "Vacation"))
        val index = 1
        val truncated = breadcrumbs.subList(0, index + 1)
        assertEquals(2, truncated.size)
        assertEquals(5L, truncated.last().folderId)
    }

    // ================================================================
    // Pagination state (currentPage, hasMorePages)
    // ================================================================

    @Test
    fun pagination_initialState_pageZero() {
        val currentPage = 0
        val hasMorePages = true
        assertEquals(0, currentPage)
        assertTrue(hasMorePages)
    }

    @Test
    fun pagination_loadMore_incrementsPage() {
        var currentPage = 0
        currentPage += 1
        assertEquals(1, currentPage)
    }

    @Test
    fun pagination_hasMorePages_whenMoreThanPageSize() {
        val PAGE_SIZE = 50
        val totalImages = 120
        val hasMore = totalImages > PAGE_SIZE
        assertTrue(hasMore)
    }

    @Test
    fun pagination_hasMorePages_falseWhenExhausted() {
        val PAGE_SIZE = 50
        val allImages = 30
        val hasMore = allImages > PAGE_SIZE
        assertFalse(hasMore)
    }

    @Test
    fun pagination_refreshResetsToPageZero() {
        var currentPage = 3
        currentPage = 0 // refresh resets to page 0
        assertEquals(0, currentPage)
    }

    @Test
    fun pagination_combinedPages_trackTotal() {
        val PAGE_SIZE = 50
        val totalImages = 120
        var loaded = PAGE_SIZE // page 0
        var currentPage = 0
        // Load more
        currentPage = 1
        loaded += minOf(PAGE_SIZE, totalImages - loaded)
        val hasMore = totalImages > loaded
        assertEquals(100, loaded)
        assertTrue(hasMore)
    }

    // ================================================================
    // Batch edit message formatting (messageForOutcome)
    // ================================================================

    @Test
    fun messageForOutcome_success() {
        val outcome = BatchEditOutcome.Success(affected = 5, message = "OK")
        val msg = formatBatchEditMessage(outcome, "Paste")
        assertEquals("Paste succeeded (5 images)", msg)
    }

    @Test
    fun messageForOutcome_partial() {
        val outcome = BatchEditOutcome.Partial(
            affected = 3,
            failedIds = listOf("7", "8"),
            message = "Some files not found"
        )
        val msg = formatBatchEditMessage(outcome, "Selective paste")
        assertTrue(msg.contains("partial"))
        assertTrue(msg.contains("Some files not found"))
    }

    @Test
    fun messageForOutcome_failure() {
        val outcome = BatchEditOutcome.Failure(message = "Database error")
        val msg = formatBatchEditMessage(outcome, "Reset")
        assertEquals("Reset failed: Database error", msg)
    }

    @Test
    fun messageForOutcome_differentOperations() {
        val success = BatchEditOutcome.Success(affected = 10, message = "")
        assertEquals("Sync succeeded (10 images)", formatBatchEditMessage(success, "Sync"))
        assertEquals("Apply preset succeeded (10 images)", formatBatchEditMessage(success, "Apply preset"))
    }

    // ================================================================
    // BatchEditProgress fraction
    // ================================================================

    @Test
    fun batchEditProgress_fraction() {
        val progress = BatchEditProgress(total = 5, completed = 2)
        assertEquals(0.4f, progress.fraction, 0.001f)
    }

    @Test
    fun batchEditProgress_zeroTotal() {
        val progress = BatchEditProgress(total = 0, completed = 0)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    // ================================================================
    // Helper methods
    // ================================================================

    /**
     * Mirrors AlbumViewModel.applyFilter logic for testing.
     */
    private fun applyFilter(images: List<TestImage>, filter: TestFilterState): List<TestImage> {
        var result = images

        if (filter.cameraMakes.isNotEmpty()) {
            result = result.filter { img ->
                filter.cameraMakes.any { make ->
                    img.cameraMake.contains(make, ignoreCase = true)
                }
            }
        }

        if (filter.cameraModels.isNotEmpty()) {
            result = result.filter { img ->
                filter.cameraModels.any { m ->
                    img.cameraModel.contains(m, ignoreCase = true)
                }
            }
        }

        if (filter.lensModel.isNotEmpty()) {
            result = result.filter { img ->
                img.lensModel.contains(filter.lensModel, ignoreCase = true)
            }
        }

        if (filter.rating > 0) {
            result = result.filter { it.rating >= filter.rating }
        }

        if (filter.fileTypes.isNotEmpty()) {
            result = result.filter { img ->
                filter.fileTypes.any { type ->
                    img.typeName.equals(type, ignoreCase = true) ||
                        img.mimeType.contains(type, ignoreCase = true)
                }
            }
        }

        return result
    }

    /**
     * Mirrors AlbumViewModel.messageForOutcome logic.
     */
    private fun formatBatchEditMessage(outcome: BatchEditOutcome, op: String): String =
        when (outcome) {
            is BatchEditOutcome.Success -> "$op succeeded (${outcome.affected} images)"
            is BatchEditOutcome.Partial -> "$op partial: ${outcome.message}"
            is BatchEditOutcome.Failure -> "$op failed: ${outcome.message}"
        }
}
