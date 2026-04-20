package com.example.neodocscanner.feature.vault.data.service.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all file I/O for the scan pipeline.
 *
 * iOS equivalent: FileManagerService.swift — the enum namespace that uses
 * FileManager to save/delete images within the app's Documents directory.
 *
 * Android equivalent stores everything in [Context.filesDir]:
 *   filesDir/
 *     documents/{instanceId}/{documentId}/original.jpg
 *     documents/{instanceId}/{documentId}/masked.jpg
 *     documents/{instanceId}/{documentId}/thumb.jpg
 *
 * Using internal storage means no WRITE_EXTERNAL_STORAGE permission is needed
 * and data is automatically deleted if the app is uninstalled.
 */
@Singleton
class DocumentFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG             = "DocumentFileManager"
        private const val DOCS_ROOT       = "documents"
        private const val ORIGINAL_NAME   = "original.jpg"
        private const val MASKED_NAME     = "masked.jpg"
        private const val THUMB_NAME      = "thumb.jpg"
        private const val THUMB_MAX_PX    = 400          // longest side
        private const val JPEG_QUALITY    = 90
        private const val THUMB_QUALITY   = 75
    }

    // ── Directory helpers ─────────────────────────────────────────────────────

    private fun docDir(instanceId: String, documentId: String): File =
        File(context.filesDir, "$DOCS_ROOT/$instanceId/$documentId").also { it.mkdirs() }

    /** Converts an absolute path to a relative path (from filesDir). */
    fun toRelative(absolutePath: String): String =
        absolutePath.removePrefix(context.filesDir.path).trimStart('/')

    /** Resolves a relative path back to an absolute [File]. */
    fun toAbsolute(relativePath: String): File =
        File(context.filesDir, relativePath)

    // ── Save from scanner URI ─────────────────────────────────────────────────

    /**
     * Copies a scanned image [Uri] (from ML Kit Document Scanner) to internal
     * storage, applies EXIF rotation correction, and returns the relative path.
     *
     * iOS equivalent: The FileManager.copyItem / UIImage.pngData() calls in
     * DocuVaultViewModel+Scanning.swift.
     */
    suspend fun saveScannedImage(
        sourceUri: Uri,
        instanceId: String,
        documentId: String
    ): String? = runCatching {
        val dest = File(docDir(instanceId, documentId), ORIGINAL_NAME)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        // Correct EXIF orientation so all downstream Bitmap reads are upright
        correctExifRotation(dest)
        toRelative(dest.absolutePath)
    }.onFailure { Log.e(TAG, "saveScannedImage failed", it) }.getOrNull()

    // ── Save from Bitmap ──────────────────────────────────────────────────────

    /**
     * Saves a [Bitmap] (e.g. the masked Aadhaar image) to internal storage.
     * Returns the relative path or null on failure.
     */
    fun saveBitmap(
        bitmap: Bitmap,
        instanceId: String,
        documentId: String,
        fileName: String
    ): String? = runCatching {
        val dest = File(docDir(instanceId, documentId), fileName)
        FileOutputStream(dest).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        toRelative(dest.absolutePath)
    }.onFailure { Log.e(TAG, "saveBitmap failed", it) }.getOrNull()

    // ── Thumbnail generation ──────────────────────────────────────────────────

    /**
     * Generates a thumbnail from the original image file and saves it
     * alongside the original. Returns the relative thumb path or null.
     *
     * iOS equivalent: ImageProcessingService.generateThumbnail() which uses
     * UIGraphicsImageRenderer to produce a 400×400 thumbnail.
     */
    fun generateThumbnail(
        instanceId: String,
        documentId: String
    ): String? = runCatching {
        val original = File(docDir(instanceId, documentId), ORIGINAL_NAME)
        if (!original.exists()) return@runCatching null

        val src = BitmapFactory.decodeFile(original.absolutePath) ?: return@runCatching null
        val thumb = scaleBitmap(src, THUMB_MAX_PX)
        src.recycle()

        val dest = File(docDir(instanceId, documentId), THUMB_NAME)
        FileOutputStream(dest).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        }
        thumb.recycle()
        toRelative(dest.absolutePath)
    }.onFailure { Log.e(TAG, "generateThumbnail failed", it) }.getOrNull()

    // ── Load Bitmap (for ML / masking) ────────────────────────────────────────

    /**
     * Loads a [Bitmap] from a relative path. Returns null if file does not exist
     * or decoding fails. Callers are responsible for recycling the bitmap.
     */
    fun loadBitmap(relativePath: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(toAbsolute(relativePath).absolutePath)
    }.onFailure { Log.e(TAG, "loadBitmap failed", it) }.getOrNull()

    // ── Delete document directory ─────────────────────────────────────────────

    /**
     * Deletes all files for a document (original, masked, thumb).
     * Safe to call even if the directory doesn't exist.
     *
     * iOS equivalent: FileManager.removeItem() in DocuVaultViewModel.
     */
    fun deleteDocumentFiles(instanceId: String, documentId: String) {
        runCatching {
            docDir(instanceId, documentId).deleteRecursively()
        }.onFailure { Log.e(TAG, "deleteDocumentFiles failed", it) }
    }

    /** Deletes all files for an entire vault instance. */
    fun deleteInstanceFiles(instanceId: String) {
        runCatching {
            File(context.filesDir, "$DOCS_ROOT/$instanceId").deleteRecursively()
        }.onFailure { Log.e(TAG, "deleteInstanceFiles failed", it) }
    }

    // ── Image preprocessing helpers ───────────────────────────────────────────

    /**
     * Scales a [Bitmap] so its longest side equals [maxPx], preserving aspect ratio.
     * iOS equivalent: ImageProcessingService.resizeImage().
     */
    fun scaleBitmap(src: Bitmap, maxPx: Int): Bitmap {
        val (w, h) = src.width to src.height
        val scale  = maxPx.toFloat() / maxOf(w, h)
        if (scale >= 1f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    /**
     * Prepares a 224×224 ARGB bitmap for TF Lite inference.
     * iOS equivalent: ImageProcessingService.prepareForML() which also produces
     * 224×224 input for the TFLite model.
     */
    fun prepareForML(src: Bitmap): Bitmap =
        Bitmap.createScaledBitmap(src, 224, 224, true)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun correctExifRotation(file: File) {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else                                  -> return   // no rotation needed
        }
        val src = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        src.recycle()
        FileOutputStream(file).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        rotated.recycle()
        // Clear the EXIF orientation tag so future reads don't double-rotate
        exif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL.toString()
        )
        exif.saveAttributes()
    }
}
