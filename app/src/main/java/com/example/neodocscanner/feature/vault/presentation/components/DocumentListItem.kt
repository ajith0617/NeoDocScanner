package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import java.io.File

/**
 * Compact row showing a single document inside an expanded SectionCard.
 * Supports long-press context menus matching iOS VaultChecklistView context menus.
 *
 * iOS equivalent: The inline document rows in VaultChecklistView.swift
 * with contextMenu modifier.
 */
@Composable
fun DocumentListItem(
    document: Document,
    onTap: () -> Unit = {},
    // ── Context menu callbacks (Module 7) ────────────────────────────────────
    contextMenuState: DocumentContextMenuState = DocumentContextMenuState(),
    onEnterSelectionMode: (Document) -> Unit = {},
    onToggleSelection: (Document) -> Unit = {},
    onStartAadhaarPairing: (Document) -> Unit = {},
    onConfirmAadhaarPair: (Document) -> Unit = {},
    onStartPassportPairing: (Document) -> Unit = {},
    onConfirmPassportPair: (Document) -> Unit = {},
    onStartGenericGrouping: (Document) -> Unit = {},
    onToggleGenericCandidate: (Document) -> Unit = {},
    onRequestGenericGroupName: () -> Unit = {},
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

    val isSelected       = document.id in contextMenuState.selectedIds
    val badgeIndex       = contextMenuState.multiSelectBadgeIndex(document.id)
    val isInAnyGroupingMode = contextMenuState.isAadhaarPairingMode
            || contextMenuState.isPassportPairingMode
            || contextMenuState.isGenericGroupingMode
    val groupMemberCount = contextMenuState.groupMemberCountFor(document.id)
    val inMultiSelect = contextMenuState.isInMultiSelect(document.id)

    val showHighlight = inMultiSelect

    val clickEnabled = when {
        contextMenuState.isSelectionMode -> true
        contextMenuState.isAadhaarPairingMode ->
            document.documentClass == DocumentClass.AADHAAR && document.groupId == null
        contextMenuState.isPassportPairingMode ->
            document.documentClass == DocumentClass.PASSPORT && document.groupId == null
        contextMenuState.isGenericGroupingMode ->
            document.documentClass != DocumentClass.AADHAAR
                && document.documentClass != DocumentClass.PASSPORT
        else -> document.processingStatus == ProcessingStatus.COMPLETE
    }

    Box {
        Row(
            modifier          = modifier
                .fillMaxWidth()
                .then(
                    if (showHighlight) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    ) else Modifier
                )
                .combinedClickable(
                    enabled      = clickEnabled,
                    onClick      = {
                        when {
                            contextMenuState.isSelectionMode -> onToggleSelection(document)
                            contextMenuState.isAadhaarPairingMode ->
                                onConfirmAadhaarPair(document)
                            contextMenuState.isPassportPairingMode ->
                                onConfirmPassportPair(document)
                            contextMenuState.isGenericGroupingMode ->
                                onToggleGenericCandidate(document)
                            else -> onTap()
                        }
                    },
                    onLongClick  = {
                        when {
                            contextMenuState.isSelectionMode ->
                                onToggleSelection(document)
                            isInAnyGroupingMode -> { }
                            else -> onEnterSelectionMode(document)
                        }
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Selection indicator / thumbnail ───────────────────────────────
            if (badgeIndex != null) {
                // Selected in Select / Pair / Group-with: show numbered circle (same order rules)
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = "$badgeIndex",
                        color     = Color.White,
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Thumbnail
                val thumbPath = document.thumbnailRelativePath ?: document.relativePath
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbPath.isNotBlank() && document.processingStatus == ProcessingStatus.COMPLETE) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(context.filesDir, "NeoDocs/$thumbPath"))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Default.Image,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = document.displayTitle,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                val duplicateClusterSize = contextMenuState.duplicateClusterSizeFor(document.id)
                val subtitle = buildString {
                    append(document.formattedDate)
                    if (document.groupName != null) append(" · ${document.groupName}")
                    else append(" · ${document.formattedSize}")
                    if (duplicateClusterSize >= 2) {
                        if (duplicateClusterSize >= 3) append(" · Dup ×$duplicateClusterSize")
                        else append(" · Dup")
                    }
                }
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Badge / progress / selection check + overflow (⋮) for actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    contextMenuState.isSelectionMode && isSelected -> {
                        Icon(
                            imageVector        = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                    document.processingStatus == ProcessingStatus.QUEUED ||
                    document.processingStatus == ProcessingStatus.ANALYSING -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    document.processingStatus == ProcessingStatus.COMPLETE -> {
                        DocumentClassBadge(documentClass = document.documentClass)
                    }
                }
                if (clickEnabled) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Context menu (long-press, normal mode only) ────────────────────
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            buildContextMenuItems(
                document           = document,
                contextMenuState   = contextMenuState,
                groupMemberCount   = groupMemberCount,
                onDismiss          = { showMenu = false },
                onEnterSelectionMode  = onEnterSelectionMode,
                onStartAadhaarPairing = onStartAadhaarPairing,
                onStartPassportPairing= onStartPassportPairing,
                onStartGenericGrouping= onStartGenericGrouping,
                onShowMoveSheet    = onShowMoveSheet,
                onShowRenameGroup  = onShowRenameGroupDialog,
                onReorderPages     = onShowPageReorderSheet,
                onExportPdf        = onExportAsPdf,
                onUngroup          = onUngroupDocuments,
                onDelete           = onDeleteDocument,
                onDeleteGroup      = onDeleteGroup,
                onUnmerge          = onUnmergePdf,
                onSharePdf         = onSharePdf,
                onOpenPdfViewer    = onOpenPdfViewer
            )
        }
    }
}

/** Context menu builder shared by DocumentListItem and DocumentReviewCard. */
@Composable
fun buildContextMenuItems(
    document: Document,
    contextMenuState: DocumentContextMenuState,
    groupMemberCount: Int,
    onDismiss: () -> Unit,
    onEnterSelectionMode: (Document) -> Unit,
    onStartAadhaarPairing: (Document) -> Unit,
    onStartPassportPairing: (Document) -> Unit,
    onStartGenericGrouping: (Document) -> Unit,
    onShowMoveSheet: (String) -> Unit,
    onShowRenameGroup: (Document) -> Unit,
    onReorderPages: (Document) -> Unit,
    onExportPdf: (String) -> Unit,
    onUngroup: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUnmerge: (String) -> Unit,
    onSharePdf: (String) -> Unit,
    onOpenPdfViewer: (String) -> Unit
) {
    // ── Merged PDF card ────────────────────────────────────────────────────
    if (document.exportedFromGroupId != null) {
        DropdownMenuItem(
            text    = { Text("View PDF") },
            onClick = { onOpenPdfViewer(document.id); onDismiss() }
        )
        DropdownMenuItem(
            text    = { Text("Share PDF") },
            onClick = { onSharePdf(document.id); onDismiss() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text    = { Text("Unmerge") },
            onClick = { onUnmerge(document.id); onDismiss() }
        )
        DropdownMenuItem(
            text    = { Text("Delete PDF & Originals", color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(document.id); onDismiss() }
        )
        return
    }

    // ── Normal mode — full menu (iOS exact parity) ─────────────────────────
    // Select: use long-press on the row to enter selection mode.
    // DropdownMenuItem(
    //     text    = { Text("Select") },
    //     onClick = { onEnterSelectionMode(document); onDismiss() }
    // )

    // Aadhaar pair option (only for Aadhaar docs not already grouped)
    if (document.documentClass == DocumentClass.AADHAAR && document.groupId == null) {
        DropdownMenuItem(
            text    = { Text("Pair with…") },
            onClick = { onStartAadhaarPairing(document); onDismiss() }
        )
    }

    // Passport pair option (only for Passport docs not already grouped)
    if (document.documentClass == DocumentClass.PASSPORT && document.groupId == null) {
        DropdownMenuItem(
            text    = { Text("Pair with…") },
            onClick = { onStartPassportPairing(document); onDismiss() }
        )
    }

    // Group with... / Add to this group... for non-Aadhaar/Passport documents.
    if (document.documentClass != DocumentClass.AADHAAR &&
        document.documentClass != DocumentClass.PASSPORT
    ) {
        DropdownMenuItem(
            text    = { Text(if (document.groupId != null) "Add to this group…" else "Group with…") },
            onClick = { onStartGenericGrouping(document); onDismiss() }
        )
    }

    DropdownMenuItem(
        text    = { Text("Move to Category…") },
        onClick = { onShowMoveSheet(document.id); onDismiss() }
    )

    // Group-specific operations
    if (document.groupId != null) {
        HorizontalDivider()
        DropdownMenuItem(
            text    = { Text("Rename Group") },
            onClick = { onShowRenameGroup(document); onDismiss() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text    = { Text("Ungroup") },
            onClick = { onUngroup(document.id); onDismiss() }
        )
    }

    HorizontalDivider()
    DropdownMenuItem(
        text    = {
            Text(
                if (document.groupId != null) "Delete Group" else "Delete",
                color = MaterialTheme.colorScheme.error
            )
        },
        onClick = {
            if (document.groupId != null) onDeleteGroup(document.id)
            else onDelete(document.id)
            onDismiss()
        }
    )
}

/**
 * Immutable snapshot of grouping/selection state needed by document cards
 * to render their context menus correctly.
 *
 * iOS equivalent: The combination of isSelectionMode, isAadhaarPairingMode,
 * selectedDocumentIDs, etc. observed directly from the ViewModel in iOS.
 */
data class DocumentContextMenuState(
    val isSelectionMode: Boolean         = false,
    val selectedIds: Set<String>         = emptySet(),
    val selectionOrder: List<String>     = emptyList(),
    /** Vault selection scope: null = all categories, section id = that section only, "__inbox__" = uncategorised. */
    val selectionScopeSectionId: String? = null,
    val isAadhaarPairingMode: Boolean    = false,
    val aadhaarPairingOrder: List<String> = emptyList(),
    val isPassportPairingMode: Boolean   = false,
    val passportPairingOrder: List<String> = emptyList(),
    val isGenericGroupingMode: Boolean   = false,
    val genericGroupingOrder: List<String> = emptyList(),
    // Maps doc.id → total group member count (including self)
    val groupMemberCounts: Map<String, Int> = emptyMap(),
    // Maps doc.id -> duplicate cluster size in the same category.
    val duplicateClusterSizes: Map<String, Int> = emptyMap()
) {
    fun groupMemberCountFor(docId: String): Int = groupMemberCounts[docId] ?: 1
    fun duplicateClusterSizeFor(docId: String): Int = duplicateClusterSizes[docId] ?: 0

    /** Same numbering rules as Select: order in list → 1-based badge; toggle removes and reindexes. */
    fun multiSelectBadgeIndex(docId: String): Int? {
        val order = when {
            isSelectionMode -> selectionOrder
            isGenericGroupingMode -> genericGroupingOrder
            isAadhaarPairingMode -> aadhaarPairingOrder
            isPassportPairingMode -> passportPairingOrder
            else -> return null
        }
        val idx = order.indexOf(docId)
        return if (idx >= 0) idx + 1 else null
    }

    fun isInMultiSelect(docId: String): Boolean =
        if (isSelectionMode) docId in selectedIds
        else when {
            isGenericGroupingMode -> docId in genericGroupingOrder
            isAadhaarPairingMode -> docId in aadhaarPairingOrder
            isPassportPairingMode -> docId in passportPairingOrder
            else -> false
        }
}
