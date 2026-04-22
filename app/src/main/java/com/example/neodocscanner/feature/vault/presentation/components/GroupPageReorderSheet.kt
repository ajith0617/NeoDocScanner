package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import java.io.File

/**
 * Drag-to-reorder sheet for pages within a document group.
 *
 * iOS equivalent: GroupPageReorderSheet.swift
 *
 * Uses native Compose long-press drag gestures to reorder items —
 * no external library needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPageReorderSheet(
    initialOrder: List<Document>,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var orderedDocs by remember(initialOrder) { mutableStateOf(initialOrder.toMutableList()) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = 88.dp   // approximate row height (taller rows for visible thumbnails)
    val lazyListState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    text       = "Reorder Pages",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center
                )
                TextButton(onClick = { onDone(orderedDocs.map { it.id }) }) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Reorderable list (long-press drag) ────────────────────────────
            LazyColumn(
                state    = lazyListState,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                itemsIndexed(orderedDocs, key = { _, doc -> doc.id }) { index, doc ->
                    val isDragging = index == draggingIndex
                    PageRow(
                        doc        = doc,
                        index      = index + 1,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { _ ->
                                        draggingIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag      = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        // Compute target index from drag offset
                                        val targetIndex = (index + (dragOffsetY / itemHeightPx.value).toInt())
                                            .coerceIn(0, orderedDocs.size - 1)
                                        if (targetIndex != draggingIndex) {
                                            val mutable = orderedDocs.toMutableList()
                                            val item = mutable.removeAt(draggingIndex)
                                            mutable.add(targetIndex, item)
                                            orderedDocs = mutable
                                            dragOffsetY -= (targetIndex - draggingIndex) * itemHeightPx.value
                                            draggingIndex = targetIndex
                                        }
                                    },
                                    onDragEnd   = {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    doc: Document,
    index: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .offset(y = if (isDragging) dragOffsetY.dp else 0.dp)
            .graphicsLayer {
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Page number chip
        Box(
            modifier         = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "$index",
                color      = Color.White,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        ReorderPageThumbnail(
            doc      = doc,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Name + side indicator
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = doc.fileName.substringBeforeLast("."),
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            val subtitle = when (doc.aadhaarSide) {
                "front"   -> "Aadhaar Front"
                "back"    -> "Aadhaar Back"
                else -> when (doc.passportSide) {
                    "data"    -> "Passport Data Page"
                    "address" -> "Passport Address Page"
                    else      -> "Page $index"
                }
            }
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Drag handle hint
        Icon(
            imageVector        = Icons.Default.DragHandle,
            contentDescription = "Long press to drag",
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(20.dp)
        )
    }
}

/** Same path rules as [com.example.neodocscanner.feature.vault.presentation.components.DocumentGalleryCard]. */
private fun resolveReorderThumbnailFile(context: android.content.Context, rawPath: String?): File? {
    if (rawPath.isNullOrBlank()) return null
    val trimmed = rawPath.trim().removePrefix("/")
    val candidates = listOf(
        File(context.filesDir, "NeoDocs/$trimmed"),
        File(context.filesDir, trimmed),
        File(rawPath)
    )
    return candidates.firstOrNull { it.exists() && it.isFile }
}

@Composable
private fun ReorderPageThumbnail(doc: Document, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (doc.processingStatus) {
        ProcessingStatus.QUEUED, ProcessingStatus.ANALYSING -> {
            Box(
                modifier         = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        ProcessingStatus.COMPLETE -> {
            val rawPath = doc.maskedRelativePath
                ?: doc.thumbnailRelativePath
                ?: doc.relativePath
            val imageFile = remember(doc.id, rawPath) {
                resolveReorderThumbnailFile(context, rawPath)
            }
            if (imageFile != null) {
                AsyncImage(
                    model              = ImageRequest.Builder(context)
                        .data(imageFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = modifier
                )
            } else {
                Box(
                    modifier         = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Image,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
