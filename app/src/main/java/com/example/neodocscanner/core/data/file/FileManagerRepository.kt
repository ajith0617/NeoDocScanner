package com.example.neodocscanner.core.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all file I/O within the app's private storage directory.
 *
 * iOS equivalent: FileManagerService.swift (enum namespace).
 *
 * Storage root: context.filesDir/NeoDocs/
 *   ├── <categoryName>/          — scanned images per category
 *   ├── Thumbnails/              — 300×300 px thumbnail PNGs
 *   └── Exports/                 — merged PDF files
 *
 * All paths stored in Room are RELATIVE to the NeoDocs root, matching iOS convention.
 */
@Singleton
class FileManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ROOT_DIR       = "NeoDocs"
        const val THUMBNAILS_DIR = "Thumbnails"
        const val EXPORTS_DIR    = "Exports"

        const val THUMB_SIZE = 300
    }

    /** Absolute root: context.filesDir/NeoDocs/ */
    val appDocumentsDir: File
        get() = File(context.filesDir, ROOT_DIR).also { it.mkdirs() }

    /** Resolves a relative path to an absolute File. */
    fun resolveAbsolute(relativePath: String): File =
        File(appDocumentsDir, relativePath)

    // ── Directory helpers ─────────────────────────────────────────────────────

    fun ensureCategoryDir(categoryName: String): File {
        return File(appDocumentsDir, categoryName).also { it.mkdirs() }
    }

    fun ensureThumbnailDir(): File {
        return File(appDocumentsDir, THUMBNAILS_DIR).also { it.mkdirs() }
    }

    fun ensureExportsDir(): File {
        return File(appDocumentsDir, EXPORTS_DIR).also { it.mkdirs() }
    }

    // ── File operations ───────────────────────────────────────────────────────

    /** Saves a bitmap as PNG in the given category directory. Returns the relative path. */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        fileName: String,
        categoryName: String
    ): String = withContext(Dispatchers.IO) {
        val dir  = ensureCategoryDir(categoryName)
        val file = deduplicateFile(File(dir, fileName))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        "$categoryName/${file.name}"
    }

    /**
     * Generates a 300×300 aspect-fill thumbnail and saves it to the Thumbnails directory.
     * Returns the relative path e.g. "Thumbnails/scan_1234_thumb.png".
     *
     * iOS equivalent: FileManagerService.saveThumbnail(for:named:)
     */
    suspend fun saveThumbnail(bitmap: Bitmap, baseName: String): String =
        withContext(Dispatchers.IO) {
            val dir       = ensureThumbnailDir()
            val thumbName = "${baseName}_thumb.png"
            val file      = File(dir, thumbName)

            val thumb = createAspectFillThumbnail(bitmap)
            FileOutputStream(file).use { out ->
                thumb.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            "$THUMBNAILS_DIR/$thumbName"
        }

    /** Deletes the file at the given relative path. Silent no-op if not found. */
    fun deleteFile(relativePath: String) {
        resolveAbsolute(relativePath).delete()
    }

    /** Returns true when the file at relativePath exists on disk. */
    fun fileExists(relativePath: String): Boolean =
        resolveAbsolute(relativePath).exists()

    /**
     * Renames a file (and its masked + thumbnail copies) in-place.
     *
     * Returns a data class with the new paths, or null on failure.
     *
     * iOS equivalent: FileManagerService.renameFile(document:to:)
     */
    suspend fun renameFile(
        relativePath: String,
        maskedRelativePath: String?,
        thumbnailRelativePath: String?,
        categoryName: String,
        newBaseName: String
    ): RenameResult? = withContext(Dispatchers.IO) {
        val sourceFile = resolveAbsolute(relativePath)
        if (!sourceFile.exists()) return@withContext null

        val ext      = sourceFile.extension
        val newName  = if (ext.isBlank()) newBaseName else "$newBaseName.$ext"
        val destFile = deduplicateFile(File(sourceFile.parentFile!!, newName))

        try {
            if (sourceFile.path != destFile.path) sourceFile.renameTo(destFile)
        } catch (_: Exception) {
            return@withContext null
        }

        val finalBase = destFile.nameWithoutExtension

        // Rename masked copy
        val newMaskedPath: String? = maskedRelativePath?.let { mp ->
            val mFile = resolveAbsolute(mp)
            if (mFile.exists()) {
                val mDest = File(mFile.parentFile!!, "${finalBase}_masked.png")
                mFile.renameTo(mDest)
                "$categoryName/${mDest.name}"
            } else null
        }

        // Rename thumbnail
        val newThumbPath: String? = thumbnailRelativePath?.let { tp ->
            val tFile = resolveAbsolute(tp)
            if (tFile.exists()) {
                val tDest = File(tFile.parentFile!!, "${finalBase}_thumb.png")
                tFile.renameTo(tDest)
                "$THUMBNAILS_DIR/${tDest.name}"
            } else null
        }

        RenameResult(
            fileName    = destFile.name,
            relativePath = "$categoryName/${destFile.name}",
            maskedRelativePath  = newMaskedPath,
            thumbnailRelativePath = newThumbPath
        )
    }

    /**
     * Moves a file from one category directory to another.
     * Returns the new relative path.
     *
     * iOS equivalent: FileManagerService.moveFile(document:to:)
     */
    suspend fun moveFile(
        relativePath: String,
        fileName: String,
        toCategory: String
    ): String = withContext(Dispatchers.IO) {
        val src  = resolveAbsolute(relativePath)
        val dest = deduplicateFile(File(ensureCategoryDir(toCategory), fileName))
        src.renameTo(dest)
        "$toCategory/${dest.name}"
    }

    /** Loads a Bitmap from a relative path. Returns null on failure. */
    fun loadBitmap(relativePath: String): Bitmap? {
        val file = resolveAbsolute(relativePath)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    /** Reads raw bytes from a relative path. Returns null on failure. */
    fun readBytes(relativePath: String): ByteArray? {
        return resolveAbsolute(relativePath).takeIf { it.exists() }?.readBytes()
    }

    /** Human-readable file size string. */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0  -> String.format("%.1f MB", mb)
            kb >= 1.0  -> String.format("%.0f KB", kb)
            else       -> "$bytes B"
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Appends _2, _3, … suffix if dest already exists. */
    private fun deduplicateFile(file: File): File {
        if (!file.exists()) return file
        var counter = 2
        var candidate: File
        do {
            val ext  = file.extension
            val base = file.nameWithoutExtension
            candidate = if (ext.isBlank()) {
                File(file.parentFile!!, "${base}_$counter")
            } else {
                File(file.parentFile!!, "${base}_$counter.$ext")
            }
            counter++
        } while (candidate.exists())
        return candidate
    }

    /**
     * Creates a 300×300 aspect-fill thumbnail.
     * iOS equivalent: The aspect-fill scaling in FileManagerService.saveThumbnail.
     */
    private fun createAspectFillThumbnail(source: Bitmap): Bitmap {
        val size  = THUMB_SIZE
        val scale = maxOf(
            size.toFloat() / source.width,
            size.toFloat() / source.height
        )
        val scaledW = (source.width  * scale).toInt()
        val scaledH = (source.height * scale).toInt()
        val offsetX = (scaledW - size) / 2
        val offsetY = (scaledH - size) / 2

        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val thumb  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumb)
        canvas.drawBitmap(scaled, Rect(offsetX, offsetY, offsetX + size, offsetY + size),
            Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        return thumb
    }

    // ── Result types ──────────────────────────────────────────────────────────

    data class RenameResult(
        val fileName: String,
        val relativePath: String,
        val maskedRelativePath: String?,
        val thumbnailRelativePath: String?
    )
}
