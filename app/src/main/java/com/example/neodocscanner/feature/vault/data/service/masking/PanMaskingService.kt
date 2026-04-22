package com.example.neodocscanner.feature.vault.data.service.masking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanMaskingService @Inject constructor(
    private val fileManager: DocumentFileManager
) {
    companion object {
        private val PAN_REGEX = Regex("""[A-Z]{5}[0-9]{4}[A-Z]""")
        private const val MASKED_FILE_SUFFIX = "_masked.png"
    }

    fun maskAndSave(
        originalRelativePath: String,
        regions: List<TextRegion>,
        instanceId: String,
        documentId: String
    ): String? {
        val bitmap = fileManager.loadBitmap(originalRelativePath) ?: return null
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        val maskRects = mutableListOf<RectF>()

        for (region in regions) {
            val line = region.text.uppercase().trim()
            if (line.isBlank()) continue
            val matches = PAN_REGEX.findAll(line)
            for (match in matches) {
                val totalChars = line.length.coerceAtLeast(1)
                val lineLeft = region.x * imgW
                val lineTop = region.y * imgH
                val lineRight = (region.x + region.w) * imgW
                val lineBottom = (region.y + region.h) * imgH
                val lineWidth = lineRight - lineLeft
                val panStart = match.range.first
                val panMaskEnd = panStart + 5
                val startFrac = panStart.toFloat() / totalChars
                val endFrac = panMaskEnd.toFloat() / totalChars
                val maskLeft = lineLeft + lineWidth * startFrac
                val maskRight = lineLeft + lineWidth * endFrac
                val padX = (maskRight - maskLeft) * 0.06f
                val padY = (lineBottom - lineTop) * 0.10f
                maskRects += RectF(
                    (maskLeft - padX).coerceAtLeast(0f),
                    (lineTop - padY).coerceAtLeast(0f),
                    (maskRight + padX).coerceAtMost(imgW),
                    (lineBottom + padY).coerceAtMost(imgH)
                )
            }
        }

        if (maskRects.isEmpty()) {
            bitmap.recycle()
            return null
        }

        val masked = renderMasked(bitmap, maskRects)
        bitmap.recycle()
        val maskedPath = fileManager.saveBitmap(masked, instanceId, documentId, MASKED_FILE_SUFFIX)
        masked.recycle()
        return maskedPath
    }

    private fun renderMasked(src: Bitmap, masks: List<RectF>): Bitmap {
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val fillPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        for (rect in masks) {
            canvas.drawRect(rect, fillPaint)
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = maxOf(rect.height() * 0.55f, 10f)
                typeface = Typeface.MONOSPACE
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val label = "XXXXX"
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val centerY = rect.centerY() + textBounds.height() / 2f
            canvas.drawText(label, rect.centerX(), centerY, textPaint)
        }
        return mutable
    }
}
