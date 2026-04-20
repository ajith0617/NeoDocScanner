package com.example.neodocscanner.feature.vault.data.service.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.neodocscanner.core.data.file.FileManagerRepository
import com.example.neodocscanner.core.domain.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts an ordered list of image Documents into a single PDF file.
 *
 * iOS equivalent: PDFExportService.swift
 *
 * Each document becomes one A4 page. The masked copy is preferred for Aadhaar
 * documents so the UID number is never exposed in the exported PDF.
 *
 * A4 at 72 dpi → 595 × 842 pts (same as iOS PDFExportService.pageSize)
 */
@Singleton
class PdfExportService @Inject constructor(
    private val fileManager: FileManagerRepository
) {
    companion object {
        // A4 at 72 dpi — matches iOS: CGSize(width: 595, height: 842)
        private const val PAGE_WIDTH  = 595
        private const val PAGE_HEIGHT = 842
    }

    /**
     * iOS: PDFExportService.generatePersistent(from:name:)
     *
     * Generates a PDF from documents and saves it persistently in
     * app's NeoDocs/Exports/ directory.
     *
     * @return Pair of (absoluteFile, relativePath)
     */
    suspend fun generatePersistent(
        documents: List<Document>,
        name: String
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val pdfData = generatePdfBytes(documents)

        val safeName = name
            .replace(Regex("[^a-zA-Z0-9 \\-_]"), "")
            .trim()
            .ifEmpty { "Group_Export" }
        val fileName = "$safeName.pdf"

        val exportsDir = fileManager.ensureExportsDir()
        var destFile = File(exportsDir, fileName)
        // Overwrite if already exists (re-export)
        destFile.delete()
        destFile = File(exportsDir, fileName)

        FileOutputStream(destFile).use { out -> out.write(pdfData) }

        val relativePath = "${FileManagerRepository.EXPORTS_DIR}/$fileName"
        Pair(destFile, relativePath)
    }

    /**
     * iOS: PDFExportService.generate(from:name:)
     *
     * Generates PDF bytes for sharing (written to a temp file).
     */
    suspend fun generateTemp(
        documents: List<Document>,
        name: String
    ): File = withContext(Dispatchers.IO) {
        val pdfData = generatePdfBytes(documents)
        val safeName = name.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim().ifEmpty { "Group_Export" }
        val tempFile = File.createTempFile(safeName, ".pdf")
        tempFile.writeBytes(pdfData)
        tempFile
    }

    // ── Private ────────────────────────────────────────────────────────────────

    private fun generatePdfBytes(documents: List<Document>): ByteArray {
        val pdfDoc = PdfDocument()
        try {
            for ((index, doc) in documents.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                drawDocumentPage(canvas, doc)
                pdfDoc.finishPage(page)
            }
            val baos = java.io.ByteArrayOutputStream()
            pdfDoc.writeTo(baos)
            return baos.toByteArray()
        } finally {
            pdfDoc.close()
        }
    }

    /**
     * iOS: For each doc page:
     *  - Prefer masked version (Aadhaar UID is redacted)
     *  - Fit image on A4, aspect ratio preserved, centred
     *  - Pre-scale at 1× to keep PDF size small (iOS scaledImage at 1× scale)
     */
    private fun drawDocumentPage(canvas: Canvas, doc: Document) {
        val bitmap = loadBitmapForDoc(doc)
        if (bitmap != null) {
            // Scale at 1× (matches iOS "forces 1× regardless of device Retina scale")
            val scaledBitmap = scaleBitmapToPage(bitmap)
            val fitRect = aspectFitRect(scaledBitmap.width, scaledBitmap.height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            // Fill background white
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(scaledBitmap, null, fitRect, paint)
        } else {
            // Fallback: blank page with filename label (mirrors iOS fallback)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.DKGRAY
                textSize = 14f
                isAntiAlias = true
            }
            canvas.drawText(doc.fileName, 40f, 60f, paint)
        }
    }

    /** iOS: loadImage(for:) — prefer masked copy for Aadhaar */
    private fun loadBitmapForDoc(doc: Document): Bitmap? {
        val maskedPath = doc.maskedRelativePath
        if (maskedPath != null && fileManager.fileExists(maskedPath)) {
            val file = fileManager.resolveAbsolute(maskedPath)
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
        if (doc.relativePath.isNotBlank()) {
            val file = fileManager.resolveAbsolute(doc.relativePath)
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        return null
    }

    /**
     * iOS: scaledImage(_:to:) — pre-render at 1× scale.
     * We target the page size directly (already at 1× equivalent since PdfDocument uses pts).
     */
    private fun scaleBitmapToPage(source: Bitmap): Bitmap {
        val fitRect = aspectFitRect(source.width, source.height)
        val targetW = fitRect.width().toInt().coerceAtLeast(1)
        val targetH = fitRect.height().toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    /** iOS: aspectFitRect(for:in:) — preserve aspect ratio, centre on A4 page */
    private fun aspectFitRect(imgW: Int, imgH: Int): RectF {
        if (imgW <= 0 || imgH <= 0) return RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        val scaleX = PAGE_WIDTH.toFloat() / imgW
        val scaleY = PAGE_HEIGHT.toFloat() / imgH
        val scale  = minOf(scaleX, scaleY)
        val w = imgW * scale
        val h = imgH * scale
        val left = (PAGE_WIDTH  - w) / 2f
        val top  = (PAGE_HEIGHT - h) / 2f
        return RectF(left, top, left + w, top + h)
    }
}
