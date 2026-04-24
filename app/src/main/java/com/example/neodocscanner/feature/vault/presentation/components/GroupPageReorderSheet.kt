package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

/**
 * Drag-to-reorder sheet for pages within a document group.
 *
 * iOS equivalent: GroupPageReorderSheet.swift
 *
 * Implementation notes:
 *  - Uses sh.calvin.reorderable, the production-grade reorder library.
 *    Hand-rolling `detectDragGesturesAfterLongPress` on a `LazyColumn` row
 *    is fragile: any recomposition / row recycling mid-drag tears down
 *    the modifier and silently cancels the gesture (the "auto drop" bug).
 *  - Auto-scroll only kicks in when the finger nears the top/bottom edge
 *    of the list (library default `scrollThreshold` ≈ 48.dp from each edge).
 *  - The whole row is the long-press handle, matching the prior UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPageReorderSheet(
    initialOrder: List<Document>,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var orderedDocs by remember(initialOrder) { mutableStateOf(initialOrder) }
    val haptics = LocalHapticFeedback.current

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedDocs = orderedDocs.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

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

            // ── Reorderable list ──────────────────────────────────────────────
            LazyColumn(
                state          = lazyListState,
                modifier       = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(orderedDocs, key = { it.id }) { doc ->
                    val displayIndex = orderedDocs.indexOf(doc) + 1

                    ReorderableItem(reorderableState, key = doc.id) { isDragging ->
                        Surface(
                            shadowElevation = if (isDragging) 8.dp else 0.dp,
                            color           = if (isDragging)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                    onDragStopped = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.GestureEnd
                                        )
                                    }
                                )
                        ) {
                            PageRow(
                                doc        = doc,
                                index      = displayIndex,
                                isDragging = isDragging
                            )
                        }
                    }
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Icon(
            imageVector        = Icons.Default.DragHandle,
            contentDescription = "Long press to drag",
            tint               = if (isDragging)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
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
