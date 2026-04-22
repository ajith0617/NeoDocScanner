package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import java.io.File

/**
 * 2-column grid card for the Uncategorised (Review) tab.
 * Supports long-press context menus matching iOS VaultReviewView context menus.
 *
 * iOS equivalent: DocumentCardView.swift with contextMenu in VaultReviewView.swift.
 */
@Composable
fun DocumentReviewCard(
    document: Document,
    onOpenDocument: () -> Unit = {},
    // ── Context menu callbacks (Module 7) ───────────────────────────────────
    contextMenuState: DocumentContextMenuState = DocumentContextMenuState(),
    onEnterSelectionMode: (Document) -> Unit = {},
    onToggleSelection: (Document) -> Unit = {},
    onStartAadhaarPairing: (Document) -> Unit = {},
    onConfirmAadhaarPair: (Document) -> Unit = {},
    onStartPassportPairing: (Document) -> Unit = {},
    onConfirmPassportPair: (Document) -> Unit = {},
    onStartGenericGrouping: (Document) -> Unit = {},
    onToggleGenericCandidate: (Document) -> Unit = {},
    onCancelGroupingModes: () -> Unit = {},
    onShowMoveSheet: (String) -> Unit = {},
    onShowRenameGroupDialog: (Document) -> Unit = {},
    onShowPageReorderSheet: (Document) -> Unit = {},
    onExportAsPdf: (String) -> Unit = {},
    onUngroupDocuments: (String) -> Unit = {},
    onDeleteDocument: (String) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    onUnmergePdf: (String) -> Unit = {},
    onSharePdf: (String) -> Unit = {},
    onOpenPdfViewer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val classColor     = document.documentClass.badgeColor
    val isSelected     = document.id in contextMenuState.selectedIds
    val badgeIndex     = contextMenuState.multiSelectBadgeIndex(document.id)
    val groupMemberCount = contextMenuState.groupMemberCountFor(document.id)
    val inMultiSelect = contextMenuState.isInMultiSelect(document.id)

    val showHighlight   = inMultiSelect
    val isInGroupingMode = contextMenuState.isAadhaarPairingMode
            || contextMenuState.isPassportPairingMode
            || contextMenuState.isGenericGroupingMode

    Box {
        Card(
            modifier  = modifier
                .then(
                    if (showHighlight) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.medium
                    ) else Modifier
                )
                .combinedClickable(
                    enabled     = document.processingStatus == ProcessingStatus.COMPLETE
                        || contextMenuState.isSelectionMode
                        || contextMenuState.isAadhaarPairingMode
                        || contextMenuState.isPassportPairingMode
                        || contextMenuState.isGenericGroupingMode,
                    onClick     = {
                        when {
                            contextMenuState.isSelectionMode -> onToggleSelection(document)
                            contextMenuState.isAadhaarPairingMode ->
                                onConfirmAadhaarPair(document)
                            contextMenuState.isPassportPairingMode ->
                                onConfirmPassportPair(document)
                            contextMenuState.isGenericGroupingMode ->
                                onToggleGenericCandidate(document)
                            else -> onOpenDocument()
                        }
                    },
                    onLongClick = {
                        when {
                            contextMenuState.isSelectionMode ->
                                onToggleSelection(document)
                            isInGroupingMode -> { }
                            else -> onEnterSelectionMode(document)
                        }
                    }
                ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 2.dp
            )
        ) {
            Box {
                Column {
                    // ── Thumbnail area ────────────────────────────────────────
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(classColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (document.processingStatus) {
                            ProcessingStatus.QUEUED,
                            ProcessingStatus.ANALYSING -> {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(28.dp),
                                    color       = classColor,
                                    strokeWidth = 2.5.dp
                                )
                            }
                            ProcessingStatus.COMPLETE -> {
                                val thumbPath = document.thumbnailRelativePath
                                    ?: document.maskedRelativePath
                                    ?: document.relativePath

                                if (thumbPath.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(context.filesDir, "NeoDocs/$thumbPath"))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = document.displayTitle,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector        = Icons.Default.Image,
                                        contentDescription = null,
                                        tint               = classColor,
                                        modifier           = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }

                        // Classification badge
                        DocumentClassBadge(
                            documentClass = document.documentClass,
                            modifier      = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        )
                    }

                    // ── Metadata ──────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text     = document.displayTitle,
                            style    = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text  = document.formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Select / Pair / Group-with numbered badge (same as gallery)
                if (badgeIndex != null) {
                    Box(
                        modifier         = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = "$badgeIndex",
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Overflow → same dropdown actions as long-press used to open
                if (document.processingStatus == ProcessingStatus.COMPLETE
                    || contextMenuState.isSelectionMode
                    || contextMenuState.isAadhaarPairingMode
                    || contextMenuState.isPassportPairingMode
                    || contextMenuState.isGenericGroupingMode
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint               = Color.White.copy(alpha = 0.92f),
                            modifier           = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // ── Context menu ─────────────────────────────────────────────────────
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            buildContextMenuItems(
                document              = document,
                contextMenuState      = contextMenuState,
                groupMemberCount      = groupMemberCount,
                onDismiss             = { showMenu = false },
                onEnterSelectionMode  = onEnterSelectionMode,
                onStartAadhaarPairing = onStartAadhaarPairing,
                onStartPassportPairing= onStartPassportPairing,
                onStartGenericGrouping= onStartGenericGrouping,
                onShowMoveSheet       = onShowMoveSheet,
                onShowRenameGroup     = onShowRenameGroupDialog,
                onReorderPages        = onShowPageReorderSheet,
                onExportPdf           = onExportAsPdf,
                onUngroup             = onUngroupDocuments,
                onDelete              = onDeleteDocument,
                onDeleteGroup         = onDeleteGroup,
                onUnmerge             = onUnmergePdf,
                onSharePdf            = onSharePdf,
                onOpenPdfViewer       = onOpenPdfViewer
            )
        }
    }
}
