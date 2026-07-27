package com.alcedo.studio

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.permission.PermissionHelper
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AppIntegrityChecker"

data class CheckItemResult(
    val name: String,
    val passed: Boolean,
    val repaired: Boolean,
    val severity: Severity,
    val message: String
)

enum class Severity {
    INFO, WARNING, ERROR, CRITICAL
}

data class IntegrityResult(
    val checkResults: List<CheckItemResult>,
    val anyRepairPerformed: Boolean,
    val criticalErrorCount: Int,
    val startTimeMs: Long,
    val durationMs: Long
) {
    val allPassed: Boolean get() = checkResults.all { it.passed }
    val passedCount: Int get() = checkResults.count { it.passed }
    val failedCount: Int get() = checkResults.size - passedCount
}

object AppIntegrityChecker {

    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    private const val LOW_MEMORY_THRESHOLD_PERCENT = 15
    private const val LOW_STORAGE_THRESHOLD_MB = 200L

    fun performFullCheckAndRepair(context: Context): IntegrityResult {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "========== AppIntegrityChecker started ==========")

        val results = mutableListOf<CheckItemResult>()
        var anyRepair = false

        runCatching {
            results.add(checkCrashLoop(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkCrashLoop outer catch", e)
            results.add(CheckItemResult("crash_loop", passed = false, repaired = false,
                severity = Severity.ERROR, message = "Check threw: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkDatabaseIntegrity(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkDatabaseIntegrity outer catch", e)
            results.add(CheckItemResult("database", passed = false, repaired = false,
                severity = Severity.ERROR, message = "Check threw: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkSharedPreferences(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkSharedPreferences outer catch", e)
            results.add(CheckItemResult("shared_prefs", passed = false, repaired = false,
                severity = Severity.WARNING, message = "Check threw: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(cleanOldCacheFiles(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "cleanOldCacheFiles outer catch", e)
            results.add(CheckItemResult("cache_cleanup", passed = true, repaired = false,
                severity = Severity.INFO, message = "Cache cleanup skipped due to: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkThumbnailDirectory(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkThumbnailDirectory outer catch", e)
            results.add(CheckItemResult("thumbnail_dir", passed = false, repaired = false,
                severity = Severity.WARNING, message = "Check threw: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkNdkLibraryAvailability().also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkNdkLibraryAvailability outer catch", e)
            results.add(CheckItemResult("ndk_library", passed = false, repaired = false,
                severity = Severity.WARNING, message = "Check threw: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkStoragePermissionState(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkStoragePermissionState outer catch", e)
            results.add(CheckItemResult("storage_permission", passed = true, repaired = false,
                severity = Severity.INFO, message = "Permission check skipped: ${e.javaClass.simpleName}"))
        }

        runCatching {
            results.add(checkMemoryHealth(context).also { if (it.repaired) anyRepair = true })
        }.onFailure { e ->
            Log.e(TAG, "checkMemoryHealth outer catch", e)
            results.add(CheckItemResult("memory_health", passed = true, repaired = false,
                severity = Severity.INFO, message = "Memory check skipped: ${e.javaClass.simpleName}"))
        }

        val duration = System.currentTimeMillis() - startTime
        val criticalCount = results.count { it.severity == Severity.CRITICAL }

        Log.i(TAG, "========== AppIntegrityChecker finished in ${duration}ms ==========")
        Log.i(TAG, "Results: passed=${results.count { it.passed }}/${results.size}, " +
                "repaired=$anyRepair, critical=$criticalCount")
        results.forEach {
            Log.i(TAG, "  [${it.severity}] ${it.name}: passed=${it.passed}, repaired=${it.repaired} — ${it.message}")
        }

        return IntegrityResult(
            checkResults = results,
            anyRepairPerformed = anyRepair,
            criticalErrorCount = criticalCount,
            startTimeMs = startTime,
            durationMs = duration
        )
    }

    // ----------------------------------------------------------------
    // a) 崩溃循环检测
    // ----------------------------------------------------------------
    private fun checkCrashLoop(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[a] Checking crash loop status...")
        val crashLoop = CrashHandler.isCrashLoopDetected(context)
        return if (crashLoop) {
            Log.w(TAG, "[a] Crash loop detected! CrashHandler already attempted cleanup during initialize().")
            val recentCrash = CrashHandler.hasRecentCrash()
            val reports = CrashHandler.getCrashReports().size
            CheckItemResult(
                name = "crash_loop",
                passed = false,
                repaired = true,
                severity = Severity.CRITICAL,
                message = "Crash loop detected (reports=$reports, recent=$recentCrash). Emergency cleanup already applied by CrashHandler."
            )
        } else {
            Log.i(TAG, "[a] No crash loop detected.")
            CheckItemResult(
                name = "crash_loop",
                passed = true,
                repaired = false,
                severity = Severity.INFO,
                message = "No crash loop detected."
            )
        }
    }.getOrElse { e ->
        Log.e(TAG, "[a] checkCrashLoop internal failure", e)
        CheckItemResult("crash_loop", passed = true, repaired = false,
            severity = Severity.WARNING, message = "Check failed (non-critical): ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // b) 数据库完整性检查
    // ----------------------------------------------------------------
    private fun checkDatabaseIntegrity(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[b] Checking database integrity...")
        val dbPath = context.getDatabasePath("alcedo_sleeve.db")
        if (!dbPath.exists()) {
            Log.i(TAG, "[b] Database file does not exist yet, skipping integrity check (first run).")
            return@runCatching CheckItemResult(
                name = "database",
                passed = true,
                repaired = false,
                severity = Severity.INFO,
                message = "Database not yet created."
            )
        }
        Log.d(TAG, "[b] Database file exists: size=${dbPath.length()} bytes")

        val integrityOk = runCatching {
            SQLiteDatabase.openDatabase(
                dbPath.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val cursor = db.rawQuery("PRAGMA integrity_check", null)
                var result = "ok"
                if (cursor.moveToFirst()) {
                    result = cursor.getString(0)
                }
                cursor.close()
                "ok".equals(result, ignoreCase = true)
            }
        }.getOrElse { e ->
            Log.e(TAG, "[b] PRAGMA integrity_check threw", e)
            false
        }

        return if (integrityOk) {
            Log.i(TAG, "[b] Database integrity check passed.")
            CheckItemResult("database", passed = true, repaired = false,
                severity = Severity.INFO, message = "PRAGMA integrity_check returned OK.")
        } else {
            Log.w(TAG, "[b] Database integrity check FAILED, attempting recovery by deleting database...")
            val deleted = runCatching {
                val wal = File(dbPath.parent, dbPath.name + "-wal")
                val shm = File(dbPath.parent, dbPath.name + "-shm")
                var ok = dbPath.delete()
                if (wal.exists()) ok = wal.delete() && ok
                if (shm.exists()) ok = shm.delete() && ok
                ok
            }.getOrElse { e ->
                Log.e(TAG, "[b] Failed to delete corrupted database", e)
                false
            }
            if (deleted) {
                Log.i(TAG, "[b] Corrupted database files removed successfully.")
                CheckItemResult("database", passed = true, repaired = true,
                    severity = Severity.ERROR, message = "Database corrupted, removed successfully. Will be recreated on next access.")
            } else {
                Log.e(TAG, "[b] Failed to remove corrupted database.")
                CheckItemResult("database", passed = false, repaired = false,
                    severity = Severity.CRITICAL, message = "Database corrupted, deletion failed. Manual intervention required.")
            }
        }
    }.getOrElse { e ->
        Log.e(TAG, "[b] checkDatabaseIntegrity internal failure", e)
        CheckItemResult("database", passed = true, repaired = false,
            severity = Severity.WARNING, message = "Database check skipped: ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // c) SharedPreferences 一致性检查
    // ----------------------------------------------------------------
    private fun checkSharedPreferences(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[c] Checking SharedPreferences consistency...")
        val knownPrefsNames = listOf(
            "alcedo_crash_loop",
            "alcedo_secure",
            "privacy_consent",
            "alcedo_prefs",
            "com.alcedo.studio_preferences"
        )
        var repaired = false
        val sb = StringBuilder()
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

        if (prefsDir.exists() && prefsDir.isDirectory) {
            val files = prefsDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()
            Log.d(TAG, "[c] Found ${files.size} shared_prefs XML files")
            for (file in files) {
                runCatching {
                    val name = file.nameWithoutExtension
                    val size = file.length()
                    if (size <= 0L) {
                        Log.w(TAG, "[c] Empty prefs file: ${file.name}, deleting...")
                        if (file.delete()) {
                            repaired = true
                            sb.append("empty:$name; ")
                        }
                        return@runCatching
                    }
                    val content = file.readText(Charsets.UTF_8)
                    if (!content.trim().startsWith("<") || !content.trim().endsWith(">")) {
                        Log.w(TAG, "[c] Corrupted (non-XML) prefs file: ${file.name}, deleting...")
                        if (file.delete()) {
                            repaired = true
                            sb.append("corrupted:$name; ")
                        }
                        return@runCatching
                    }
                    val prefs = runCatching {
                        context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    }.getOrNull()
                    if (prefs != null) {
                        val entryCount = runCatching { prefs.all.size }.getOrElse { -1 }
                        if (entryCount < 0) {
                            Log.w(TAG, "[c] Failed to read prefs entries for $name, clearing...")
                            prefs.edit().clear().apply()
                            repaired = true
                            sb.append("unreadable:$name; ")
                        }
                    }
                }.onFailure { e ->
                    Log.e(TAG, "[c] Failed processing prefs file ${file.name}", e)
                }
            }
        }

        knownPrefsNames.forEach { name ->
            runCatching {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                prefs.all
            }.onFailure { e ->
                Log.w(TAG, "[c] Failed to access known prefs $name, clearing", e)
                runCatching {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                    repaired = true
                    sb.append("cleared:$name; ")
                }
            }
        }

        val msg = if (sb.isEmpty()) "All prefs consistent." else "Fixed: ${sb.trimEnd(';', ' ')}"
        Log.i(TAG, "[c] SharedPreferences check done. repaired=$repaired, msg=$msg")
        CheckItemResult(
            name = "shared_prefs",
            passed = true,
            repaired = repaired,
            severity = if (repaired) Severity.WARNING else Severity.INFO,
            message = msg
        )
    }.getOrElse { e ->
        Log.e(TAG, "[c] checkSharedPreferences internal failure", e)
        CheckItemResult("shared_prefs", passed = true, repaired = false,
            severity = Severity.WARNING, message = "Prefs check skipped: ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // d) 缓存目录清理（清理 7 天前的临时文件）
    // ----------------------------------------------------------------
    private fun cleanOldCacheFiles(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[d] Cleaning cache files older than 7 days...")
        val now = System.currentTimeMillis()
        var deletedCount = 0
        var totalFreedBytes = 0L

        val dirsToScan = mutableListOf<File>().apply {
            add(context.cacheDir)
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                context.externalCacheDir?.let { add(it) }
            }
        }

        for (dir in dirsToScan) {
            if (!dir.exists() || !dir.isDirectory) continue
            Log.d(TAG, "[d] Scanning dir: ${dir.absolutePath}")
            walkAndClean(dir, now, SEVEN_DAYS_MS) { deleted, bytes ->
                deletedCount += deleted
                totalFreedBytes += bytes
            }
        }

        val msg = "Deleted $deletedCount old temp file(s), freed ${totalFreedBytes / 1024} KB."
        Log.i(TAG, "[d] $msg")
        CheckItemResult(
            name = "cache_cleanup",
            passed = true,
            repaired = deletedCount > 0,
            severity = Severity.INFO,
            message = msg
        )
    }.getOrElse { e ->
        Log.e(TAG, "[d] cleanOldCacheFiles internal failure", e)
        CheckItemResult("cache_cleanup", passed = true, repaired = false,
            severity = Severity.INFO, message = "Cache cleanup failed: ${e.javaClass.simpleName}")
    }

    private fun walkAndClean(
        dir: File, now: Long, ageMs: Long,
        onDeleted: (count: Int, bytes: Long) -> Unit
    ) {
        runCatching {
            val files = dir.listFiles() ?: return@runCatching
            for (f in files) {
                runCatching {
                    if (f.isDirectory) {
                        walkAndClean(f, now, ageMs, onDeleted)
                        if (f.list()?.isEmpty() == true && f != dir) {
                            val size = f.length()
                            if (f.delete()) onDeleted(1, size)
                        }
                    } else {
                        val age = now - f.lastModified()
                        if (age > ageMs) {
                            val size = f.length()
                            if (f.delete()) onDeleted(1, size)
                        }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "[d] Error processing ${f.absolutePath}", e)
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // e) 缩略图目录完整性（重建损坏的缩略图文件夹）
    // ----------------------------------------------------------------
    private fun checkThumbnailDirectory(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[e] Checking thumbnail directory integrity...")
        val thumbDir = File(context.cacheDir, "thumbnails")
        var repaired = false
        val messages = mutableListOf<String>()

        if (!thumbDir.exists()) {
            Log.i(TAG, "[e] Thumbnail dir does not exist, creating...")
            if (thumbDir.mkdirs()) {
                repaired = true
                messages.add("created directory")
            } else {
                Log.e(TAG, "[e] Failed to create thumbnail dir")
                return@runCatching CheckItemResult("thumbnail_dir", passed = false, repaired = false,
                    severity = Severity.ERROR, message = "Cannot create thumbnail directory.")
            }
        }

        if (thumbDir.exists() && !thumbDir.isDirectory) {
            Log.w(TAG, "[e] Thumbnail path is a file (not dir). Deleting and recreating...")
            if (thumbDir.delete() && thumbDir.mkdirs()) {
                repaired = true
                messages.add("replaced file with dir")
            }
        }

        if (thumbDir.exists() && thumbDir.isDirectory) {
            if (!thumbDir.canRead() || !thumbDir.canWrite()) {
                Log.w(TAG, "[e] Thumbnail dir permissions issue, fixing...")
                runCatching {
                    thumbDir.setReadable(true, false)
                    thumbDir.setWritable(true, false)
                    repaired = true
                    messages.add("fixed permissions")
                }
            }

            runCatching {
                val testFile = File(thumbDir, ".integrity_test_${System.nanoTime()}")
                val canWrite = try {
                    testFile.createNewFile() && testFile.delete()
                } catch (_: Throwable) {
                    false
                }
                if (!canWrite) {
                    Log.w(TAG, "[e] Thumbnail dir not writable, attempting to fix...")
                    val cleared = thumbDir.listFiles()?.all {
                        runCatching { it.deleteRecursively() }.getOrDefault(false)
                    } ?: true
                    if (cleared && thumbDir.delete() && thumbDir.mkdirs()) {
                        repaired = true
                        messages.add("cleared and recreated (not writable)")
                    }
                }
            }
        }

        val msg = if (messages.isEmpty()) "Thumbnail directory OK." else messages.joinToString("; ")
        Log.i(TAG, "[e] $msg")
        CheckItemResult(
            name = "thumbnail_dir",
            passed = true,
            repaired = repaired,
            severity = if (repaired) Severity.WARNING else Severity.INFO,
            message = msg
        )
    }.getOrElse { e ->
        Log.e(TAG, "[e] checkThumbnailDirectory internal failure", e)
        CheckItemResult("thumbnail_dir", passed = true, repaired = false,
            severity = Severity.WARNING, message = "Thumbnail check skipped: ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // f) NDK 库可用性自检（尝试调用简单 native 函数）
    // ----------------------------------------------------------------
    private fun checkNdkLibraryAvailability(): CheckItemResult = runCatching {
        Log.d(TAG, "[f] Checking NDK library availability...")

        if (!AlcedoNativeBridge.isAvailable) {
            Log.w(TAG, "[f] AlcedoNativeBridge reports not available. " +
                    "This may be expected on certain architectures. Running in CPU-only fallback mode.")
            return@runCatching CheckItemResult(
                name = "ndk_library",
                passed = true,
                repaired = false,
                severity = Severity.WARNING,
                message = "Native library unavailable. CPU-only fallback mode will be used."
            )
        }

        val result = runCatching {
            val s = AlcedoNativeBridge.stringFromJNI()
            val id = AlcedoNativeBridge.generateId()
            s != null && id > 0L
        }.getOrElse { e ->
            Log.e(TAG, "[f] Native call probe failed", e)
            false
        }

        return if (result) {
            Log.i(TAG, "[f] NDK library OK: native probe calls succeeded.")
            CheckItemResult("ndk_library", passed = true, repaired = false,
                severity = Severity.INFO, message = "Native library loaded and probe calls (stringFromJNI / generateId) succeeded.")
        } else {
            Log.w(TAG, "[f] NDK library probe failed despite available flag.")
            CheckItemResult("ndk_library", passed = true, repaired = false,
                severity = Severity.WARNING, message = "Native library reports available but probe failed. CPU-only fallback recommended.")
        }
    }.getOrElse { e ->
        Log.e(TAG, "[f] checkNdkLibraryAvailability internal failure", e)
        CheckItemResult("ndk_library", passed = true, repaired = false,
            severity = Severity.WARNING, message = "NDK check skipped: ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // g) 存储权限状态验证
    // ----------------------------------------------------------------
    private fun checkStoragePermissionState(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[g] Verifying storage permission state...")
        val audit = PermissionHelper.auditPermissionState(context)

        val hasRead = PermissionHelper.hasReadMediaAccess(context)
        val hasWrite = PermissionHelper.hasWriteAccess(context)
        val limited = PermissionHelper.isLimitedAccess(context)

        val message = buildString {
            append("readMediaAccess=$hasRead, writeAccess=$hasWrite, limitedAccess=$limited; ")
            append("granted=${audit.grantedPermissions.size}, denied=${audit.deniedPermissions.size}, ")
            append("permanentlyDenied=${audit.permanentlyDeniedPermissions.size}")
        }
        val severity = when {
            !hasWrite -> Severity.WARNING
            !hasRead -> Severity.WARNING
            audit.permanentlyDeniedPermissions.isNotEmpty() -> Severity.WARNING
            else -> Severity.INFO
        }
        Log.i(TAG, "[g] Permission state: $message")
        CheckItemResult(
            name = "storage_permission",
            passed = true,
            repaired = false,
            severity = severity,
            message = message
        )
    }.getOrElse { e ->
        Log.e(TAG, "[g] checkStoragePermissionState internal failure", e)
        CheckItemResult("storage_permission", passed = true, repaired = false,
            severity = Severity.INFO, message = "Permission check failed: ${e.javaClass.simpleName}")
    }

    // ----------------------------------------------------------------
    // h) 内存状态健康检查
    // ----------------------------------------------------------------
    private fun checkMemoryHealth(context: Context): CheckItemResult = runCatching {
        Log.d(TAG, "[h] Running memory / storage health check...")
        val messages = mutableListOf<String>()
        var severity = Severity.INFO
        var repaired = false

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val mi = ActivityManager.MemoryInfo()
            runCatching { am.getMemoryInfo(mi) }.getOrNull()
            val availablePct = (mi.availMem.toDouble() / mi.totalMem.toDouble() * 100).toInt()
            messages.add("mem_avail_pct=$availablePct% (${mi.availMem / 1048576L}MB/${mi.totalMem / 1048576L}MB)")
            if (mi.lowMemory) {
                severity = Severity.WARNING
                messages.add("SYSTEM FLAG lowMemory=true")
                Log.w(TAG, "[h] System reports low memory! Running emergency GC...")
                runCatching {
                    System.gc()
                    Runtime.getRuntime().gc()
                    repaired = true
                    messages.add("triggered emergency GC")
                }
            }
            if (availablePct < LOW_MEMORY_THRESHOLD_PERCENT) {
                severity = maxOf(severity, Severity.WARNING)
                messages.add("below ${LOW_MEMORY_THRESHOLD_PERCENT}% threshold")
            }
        }

        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val heapPct = (usedHeap.toDouble() / runtime.maxMemory().toDouble() * 100).toInt()
        messages.add("heap_used_pct=$heapPct% (${usedHeap / 1048576L}MB/${runtime.maxMemory() / 1048576L}MB)")
        if (heapPct >= 85) {
            severity = maxOf(severity, Severity.WARNING)
            messages.add("heap usage >= 85%")
        }

        runCatching {
            val dataDir = context.filesDir
            val stat = StatFs(dataDir.absolutePath)
            val availBytes = stat.availableBytes
            val availMb = availBytes / 1048576L
            messages.add("storage_avail=${availMb}MB")
            if (availMb < LOW_STORAGE_THRESHOLD_MB) {
                severity = maxOf(severity, Severity.WARNING)
                messages.add("storage below ${LOW_STORAGE_THRESHOLD_MB}MB threshold — dangerous")
            }
        }.onFailure { e ->
            Log.w(TAG, "[h] Cannot stat storage", e)
            messages.add("storage_stat_failed")
        }

        val msg = messages.joinToString("; ")
        Log.i(TAG, "[h] $msg")
        CheckItemResult(
            name = "memory_health",
            passed = true,
            repaired = repaired,
            severity = severity,
            message = msg
        )
    }.getOrElse { e ->
        Log.e(TAG, "[h] checkMemoryHealth internal failure", e)
        CheckItemResult("memory_health", passed = true, repaired = false,
            severity = Severity.INFO, message = "Memory health check skipped: ${e.javaClass.simpleName}")
    }
}
