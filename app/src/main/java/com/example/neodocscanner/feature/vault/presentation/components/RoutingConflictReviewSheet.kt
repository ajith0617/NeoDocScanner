package com.example.neodocscanner.feature.vault.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.SectionRoutingConflict
import java.io.File

/** Same path resolution as [DocumentGalleryCard] thumbnails — files may live under NeoDocs/ or absolute paths. */
private fun resolveVaultImageFile(context: Context, rawPath: String?): File? {
    if (rawPath.isNullOrBlank()) return null
    val trimmed = rawPath.trim().removePrefix("/")
    return listOf(
        File(context.filesDir, "NeoDocs/$trimmed"),
        File(context.filesDir, trimmed),
        File(rawPath)
    ).firstOrNull { it.exists() && it.isFile }
}

/** Minimal sheet: scan folder vs identified type, per-doc and bulk actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingConflictReviewSheet(
    conflicts: List<SectionRoutingConflict>,
    documentById: Map<String, Document>,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onResolveOne: (conflictId: String, useDetectedCategory: Boolean) -> Unit,
    onKeepAllHinted: () -> Unit,
    onMoveAllToDetected: () -> Unit
) {
    if (conflicts.isEmpty()) return

    val context = LocalContext.current
    val total = conflicts.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Folder mismatch",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (total > 1) {
                Text(
                    text = "$total documents",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(conflicts, key = { _, c -> c.id }) { index, conflict ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    val doc = documentById[conflict.documentId]
                    val title = doc?.displayName ?: "Document ${index + 1}"
                    RoutingConflictRow(
                        indexPrefix = if (total > 1) "${index + 1}/$total " else "",
                        title = title,
                        conflict = conflict,
                        document = doc,
                        context = context,
                        onKeep = { onResolveOne(conflict.id, false) },
                        onMoveToDetected = { onResolveOne(conflict.id, true) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onKeepAllHinted,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep all", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onMoveAllToDetected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Move all", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RoutingConflictRow(
    indexPrefix: String,
    title: String,
    conflict: SectionRoutingConflict,
    document: Document?,
    context: Context,
    onKeep: () -> Unit,
    onMoveToDetected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rawPath = document?.maskedRelativePath
                ?: document?.thumbnailRelativePath
                ?: document?.relativePath
            val imageFile = remember(document?.id, rawPath) {
                resolveVaultImageFile(context, rawPath)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (imageFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = indexPrefix + title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${conflict.hintSectionTitle} · ${conflict.detectedLabel} → ${conflict.mlSectionTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onKeep,
                modifier = Modifier.weight(1f)
            ) {
                Text("Keep", maxLines = 1)
            }
            FilledTonalButton(
                onClick = onMoveToDetected,
                modifier = Modifier.weight(1f)
            ) {
                Text("Move", maxLines = 1)
            }
        }
    }
}
