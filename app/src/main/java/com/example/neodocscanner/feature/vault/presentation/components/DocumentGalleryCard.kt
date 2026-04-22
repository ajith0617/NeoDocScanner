package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.neodocscanner.ui.theme.Coral
import com.example.neodocscanner.ui.theme.Ink
import com.example.neodocscanner.ui.theme.PdfBadgeBg
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * Square gallery card — used in both the Categories (sections) grid and the Uncategorised grid.
 *
 * iOS equivalent: DocumentCard.swift with 2-column LazyVGrid layout in
 * VaultChecklistView.swift and VaultReviewView.swift.
 *
 * Key behaviours matching iOS:
 *  - 1:1 aspect ratio, thumbnail fills the card (ContentScale.Crop)
 *  - Semi-transparent gradient at the bottom: file name + date
 *  - Tappable classification badge (top-right) → ClassificationChangeSheet
 *    After picking → rerouteAfterReclassification auto-moves the document
 *  - Processing state badges: Queued / Analysing spinner
 *  - Selection mode: numbered orange circle replaces badge
 *  - Group badges: ×N (generic group), F+B (Aadhaar pair), Merged PDF
 *  - Aadhaar side label ("Front only" / "Back only") for unpaired docs
 *  - Selected-ring border when selected or highlighted as pairing anchor/candidate
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentGalleryCard(
    document: Document,
    allDocumentsInScope: List<Document> = emptyList(),
    gridColumns: Int = 2,
    onTap: () -> Unit = {},
    // ── Classification reroute ────────────────────────────────────────────────
    onReclassify: (String, DocumentClass) -> Unit = { _, _ -> },
    // ── Context menu callbacks ────────────────────────────────────────────────
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
    enableTypeSwitch: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showClassPicker by remember { mutableStateOf(false) }
    val safeGridColumns = gridColumns.coerceIn(2, 4)
    val compactGrid = safeGridColumns >= 3
    val denseGrid = safeGridColumns >= 4
    val cardCorner = if (compactGrid) 10.dp else 12.dp
    val topBadgePadding = if (compactGrid) 5.dp else 7.dp
    val bottomTextPaddingHorizontal = if (compactGrid) 6.dp else 9.dp
    val bottomTextPaddingVertical = if (compactGrid) 6.dp else 9.dp
    val titleFontSize = when {
        denseGrid -> 9.sp
        compactGrid -> 10.sp
        else -> 12.sp
    }
    val metaFontSize = when {
        denseGrid -> 0.sp
        compactGrid -> 8.sp
        else -> 9.sp
    }
    val metadataText = document.cardMetaLabel(safeGridColumns)
    val overflowButtonSize = when {
        denseGrid -> 22.dp
        compactGrid -> 24.dp
        else -> 26.dp
    }
    val overflowIconSize = when {
        denseGrid -> 12.dp
        compactGrid -> 13.dp
        else -> 16.dp
    }
    val overflowButtonShape = RoundedCornerShape(if (compactGrid) 7.dp else 9.dp)

    val isSelected     = document.id in contextMenuState.selectedIds
    val badgeIndex     = contextMenuState.multiSelectBadgeIndex(document.id)
    val inMultiSelect  = contextMenuState.isInMultiSelect(document.id)
    val groupMemberCount = contextMenuState.groupMemberCountFor(document.id)

    val showHighlight = inMultiSelect
    val isInGroupingMode = contextMenuState.isAadhaarPairingMode
        || contextMenuState.isPassportPairingMode
        || contextMenuState.isGenericGroupingMode

    val isMergedPdf  = document.exportedFromGroupId != null
    val isPaired     = document.aadhaarSide != null && document.groupId != null
    val isGroupStack = document.groupId != null && !isPaired && !isMergedPdf
    val groupMembers = if (document.groupId != null) {
        allDocumentsInScope
            .filter { it.groupId == document.groupId }
            .sortedWith(compareBy({ it.groupPageIndex ?: Int.MAX_VALUE }, { it.pageIndex ?: Int.MAX_VALUE }))
    } else {
        emptyList()
    }
    val partnerDocument = if (isPaired) groupMembers.firstOrNull { it.id != document.id } else null
    val showsSideHintChip = document.groupId == null && (
        document.aadhaarSide != null ||
            (document.documentClass == DocumentClass.PASSPORT && document.passportSide != null)
        )
    val passportMissingSideLabel = when {
        document.documentClass != DocumentClass.PASSPORT || document.groupId == null || groupMembers.isEmpty() -> null
        groupMembers.any { it.passportSide == "data" } && groupMembers.any { it.passportSide == "address" } -> null
        groupMembers.any { it.passportSide == "data" } -> "Need back page"
        groupMembers.any { it.passportSide == "address" } -> "Need data page"
        else -> "Incomplete pair"
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    val cardInteractionsEnabled = document.processingStatus == ProcessingStatus.COMPLETE
        || contextMenuState.isSelectionMode
        || contextMenuState.isAadhaarPairingMode
        || contextMenuState.isPassportPairingMode
        || contextMenuState.isGenericGroupingMode

    /** Overflow (⋮) opens actions; long-press only enters selection (not this sheet). */
    val showOverflowMenu = cardInteractionsEnabled

    val overflowMenuBottomPadding = if (!contextMenuState.isSelectionMode &&
        (showsSideHintChip || passportMissingSideLabel != null)
    ) {
        if (compactGrid) 28.dp else 34.dp
    } else {
        if (compactGrid) 5.dp else 7.dp
    }

    // ── Classification picker sheet ───────────────────────────────────────────
    if (enableTypeSwitch && showClassPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showClassPicker = false },
            sheetState       = sheetState
        ) {
            ClassificationChangeSheetContent(
                currentClass = document.documentClass,
                onPick       = { newClass ->
                    showClassPicker = false
                    onReclassify(document.id, newClass)
                },
                onDismiss    = { showClassPicker = false }
            )
        }
    }

    if (showContextMenu) {
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showContextMenu = false },
            sheetState = menuSheetState
        ) {
            DocumentActionSheetContent(
                document = document,
                contextMenuState = contextMenuState,
                groupMemberCount = groupMemberCount,
                onDismiss = { showContextMenu = false },
                onStartAadhaarPairing = onStartAadhaarPairing,
                onStartPassportPairing = onStartPassportPairing,
                onStartGenericGrouping = onStartGenericGrouping,
                onShowMoveSheet = onShowMoveSheet,
                onShowRenameGroup = onShowRenameGroupDialog,
                onReorderPages = onShowPageReorderSheet,
                onExportPdf = onExportAsPdf,
                onUngroup = onUngroupDocuments,
                onDelete = onDeleteDocument,
                onDeleteGroup = onDeleteGroup,
                onUnmerge = onUnmergePdf,
                onSharePdf = onSharePdf,
                onOpenPdfViewer = onOpenPdfViewer
            )
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cardCorner))
                .border(
                    width = if (isSelected || showHighlight) {
                        if (compactGrid) 2.dp else 2.5.dp
                    } else {
                        1.dp
                    },
                    color = when {
                        isSelected      -> primaryColor
                        showHighlight   -> primaryColor
                        else            -> Color.Black.copy(alpha = 0.05f)
                    },
                    shape = RoundedCornerShape(cardCorner)
                )
                .combinedClickable(
                    enabled     = cardInteractionsEnabled,
                    onClick     = {
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
                    onLongClick = {
                        when {
                            contextMenuState.isSelectionMode ->
                                onToggleSelection(document)
                            isInGroupingMode -> { /* long-press does not open menu during pair/group flows */ }
                            else -> onEnterSelectionMode(document)
                        }
                    }
                )
        ) {
            // ── Thumbnail ─────────────────────────────────────────────────────
            ThumbnailLayer(
                document           = document,
                context            = context,
                partnerDocument    = partnerDocument,
                orderedGroupMembers = groupMembers
            )

            // ── Constant dim so bottom text stays readable ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
            )

            // ── Selection / multi-select dim overlay (same as Select for pair & group-with)
            if ((contextMenuState.isSelectionMode && !isSelected) ||
                ((contextMenuState.isAadhaarPairingMode || contextMenuState.isPassportPairingMode ||
                    contextMenuState.isGenericGroupingMode) && !inMultiSelect)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                )
            }

            // ── Bottom gradient + name/date text ──────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (compactGrid) 0.52f else 0.6f)
                            )
                        )
                    )
                    .padding(
                        horizontal = bottomTextPaddingHorizontal,
                        vertical = bottomTextPaddingVertical
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text       = document.cardLabel,
                        fontSize   = titleFontSize,
                        fontWeight = if (compactGrid) FontWeight.SemiBold else FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (metadataText != null) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text     = metadataText,
                            fontSize = metaFontSize,
                            color    = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Side status hint (Aadhaar/Passport) ───────────────────────────
            if (!contextMenuState.isSelectionMode && (showsSideHintChip || passportMissingSideLabel != null)) {
                val statusLabel = when {
                    showsSideHintChip && document.aadhaarSide != null ->
                        if (document.aadhaarSide == "front") "Front only" else "Back only"
                    showsSideHintChip && document.documentClass == DocumentClass.PASSPORT ->
                        if (document.passportSide == "data") "Data only" else "Back only"
                    else -> passportMissingSideLabel.orEmpty()
                }
                val statusBackground = when {
                    passportMissingSideLabel != null -> Color(0xAA7C3AED)
                    else -> Color.Gray.copy(alpha = 0.55f)
                }
                Text(
                    text     = statusLabel,
                    fontSize = if (compactGrid) 8.sp else 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            bottom = if (compactGrid) 28.dp else 34.dp,
                            end = if (compactGrid) 5.dp else 7.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBackground)
                        .padding(
                            horizontal = if (compactGrid) 4.dp else 5.dp,
                            vertical = 2.dp
                        )
                )
            }
        }

        // ── Bottom-right overflow → action sheet (outside clip, above card taps) ─
        if (showOverflowMenu) {
            Surface(
                onClick         = { showContextMenu = true },
                shape           = overflowButtonShape,
                color           = Color.Black.copy(alpha = if (compactGrid) 0.22f else 0.30f),
                modifier        = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = if (compactGrid) 5.dp else 7.dp,
                        bottom = overflowMenuBottomPadding
                    )
                    .size(overflowButtonSize),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp
            ) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint               = Color.White.copy(alpha = 0.92f),
                        modifier           = Modifier.size(overflowIconSize)
                    )
                }
            }
        }

        // ── Top-right badge overlay (outside clip) ────────────────────────────
        Box(
            modifier         = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topBadgePadding, end = topBadgePadding)
        ) {
            when {
                // ── Select / Pair with / Group with → same numbered badge rules ──
                contextMenuState.isSelectionMode ||
                    contextMenuState.isGenericGroupingMode ||
                    contextMenuState.isAadhaarPairingMode ||
                    contextMenuState.isPassportPairingMode -> {
                    SelectionBadge(
                        isSelected = inMultiSelect,
                        index = badgeIndex,
                        compact = compactGrid
                    )
                }
                // ── Merged PDF badge ──────────────────────────────────────────
                isMergedPdf -> {
                    GroupBadge(
                        label = if (compactGrid) "PDF" else "Merged PDF",
                        icon  = Icons.Default.PictureAsPdf,
                        backgroundColor = PdfBadgeBg,
                        compact = compactGrid
                    )
                }
                // ── Aadhaar F+B paired badge ──────────────────────────────────
                isPaired -> {
                    GroupBadge(
                        label = "F+B",
                        icon = Icons.Default.PersonOutline,
                        compact = compactGrid
                    )
                }
                // ── Generic group stack badge ─────────────────────────────────
                isGroupStack -> {
                    GroupBadge(
                        label = "×${groupMemberCount}",
                        icon  = Icons.Default.Image,
                        compact = compactGrid
                    )
                }
                // ── Classification badge (read-only when type switch is disabled) ───────────
                else -> {
                    ClassificationBadge(
                        document      = document,
                        onOpenPicker  = if (enableTypeSwitch) ({ showClassPicker = true }) else null,
                        compact       = compactGrid
                    )
                }
            }
        }

    }
}

@Composable
private fun DocumentActionSheetContent(
    document: Document,
    contextMenuState: DocumentContextMenuState,
    groupMemberCount: Int,
    onDismiss: () -> Unit,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = document.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Document actions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        if (document.exportedFromGroupId != null) {
            ActionSheetButton("View PDF") { onOpenPdfViewer(document.id); onDismiss() }
            ActionSheetButton("Share PDF") { onSharePdf(document.id); onDismiss() }
            HorizontalDivider()
            ActionSheetButton("Unmerge") { onUnmerge(document.id); onDismiss() }
            ActionSheetButton("Delete PDF & Originals", isDestructive = true) {
                onDelete(document.id); onDismiss()
            }
            return
        }

        // Select: use long-press on the card to enter selection mode.
        // ActionSheetButton("Select") { onEnterSelectionMode(document); onDismiss() }

        if (document.documentClass == DocumentClass.AADHAAR && document.groupId == null) {
            ActionSheetButton("Pair with…") { onStartAadhaarPairing(document); onDismiss() }
        }
        if (document.documentClass == DocumentClass.PASSPORT && document.groupId == null) {
            ActionSheetButton("Pair with…") { onStartPassportPairing(document); onDismiss() }
        }
        if (document.groupId == null &&
            document.documentClass != DocumentClass.AADHAAR &&
            document.documentClass != DocumentClass.PASSPORT
        ) {
            ActionSheetButton("Group with…") { onStartGenericGrouping(document); onDismiss() }
        }

        ActionSheetButton("Move to Category…") { onShowMoveSheet(document.id); onDismiss() }

        if (document.groupId != null) {
            HorizontalDivider()
            ActionSheetButton("Rename Group") { onShowRenameGroup(document); onDismiss() }
            if (groupMemberCount >= 2) {
                ActionSheetButton("Reorder Pages") { onReorderPages(document); onDismiss() }
            }
            ActionSheetButton("Export as PDF") { onExportPdf(document.id); onDismiss() }
            HorizontalDivider()
            ActionSheetButton("Ungroup") { onUngroup(document.id); onDismiss() }
        }

        HorizontalDivider()
        ActionSheetButton(
            text = if (document.groupId != null) "Delete Group" else "Delete",
            isDestructive = true
        ) {
            if (document.groupId != null) onDeleteGroup(document.id) else onDelete(document.id)
            onDismiss()
        }

        // Cancel: dismiss by tapping outside the sheet or using the system back gesture.
        // Spacer(modifier = Modifier.height(6.dp))
        // ActionSheetButton("Cancel") { onDismiss() }
    }
}

@Composable
private fun ActionSheetButton(
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = text,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .combinedClickable(onClick = onClick)
    )
}

// ── Thumbnail layer ────────────────────────────────────────────────────────────

@Composable
private fun ThumbnailLayer(
    document: Document,
    context: android.content.Context,
    partnerDocument: Document?,
    orderedGroupMembers: List<Document>
) {
    val thumbPath = document.maskedRelativePath
        ?: document.thumbnailRelativePath
        ?: document.relativePath

    when (document.processingStatus) {
        ProcessingStatus.QUEUED -> {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(document.documentClass.badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color       = document.documentClass.badgeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Queued", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
        ProcessingStatus.ANALYSING -> {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Analysing", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
        ProcessingStatus.COMPLETE -> {
            val shouldShowAadhaarSplit = document.aadhaarSide != null
                && document.groupId != null
                && partnerDocument != null
            val shouldShowStack = document.groupId != null
                && document.exportedFromGroupId == null
                && document.aadhaarSide == null
                && orderedGroupMembers.size >= 2

            if (shouldShowAadhaarSplit) {
                AadhaarSplitThumbnail(
                    primary = document,
                    partner = partnerDocument,
                    context = context
                )
            } else if (shouldShowStack) {
                GroupStripThumbnail(
                    members = orderedGroupMembers,
                    context = context
                )
            } else if (thumbPath.isNotBlank()) {
                ThumbnailImage(
                    context = context,
                    doc = document,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(document.documentClass.badgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = docClassIcon(document.documentClass),
                        contentDescription = null,
                        tint               = document.documentClass.badgeColor,
                        modifier           = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AadhaarSplitThumbnail(
    primary: Document,
    partner: Document,
    context: android.content.Context
) {
    val frontDoc = if (primary.aadhaarSide == "front") primary else partner
    val backDoc = if (frontDoc.id == primary.id) partner else primary

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbnailImage(
            context = context,
            doc = frontDoc,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.38f))
        )
        ThumbnailImage(
            context = context,
            doc = backDoc,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
        )
    }
}

/**
 * Generic multi-page group: ordered horizontal “filmstrip” with step numbers,
 * subtle borders, and a +N overlay when more than three pages exist.
 */
@Composable
private fun GroupStripThumbnail(
    members: List<Document>,
    context: android.content.Context
) {
    if (members.isEmpty()) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )
        return
    }
    if (members.size == 1) {
        ThumbnailImage(
            context  = context,
            doc      = members.first(),
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val maxSlots   = 3
    val extraCount = (members.size - maxSlots).coerceAtLeast(0)
    val display    = if (members.size <= maxSlots) members else members.take(maxSlots)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.06f))
    ) {
        Row(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            display.forEachIndexed { index, doc ->
                val showMoreOverlay = index == maxSlots - 1 && extraCount > 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    ThumbnailImage(
                        context  = context,
                        doc      = doc,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showMoreOverlay) {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = "+$extraCount",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White
                            )
                        }
                    }
                    Text(
                        text       = "${index + 1}",
                        fontSize   = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White,
                        modifier   = Modifier
                            .zIndex(2f)
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailImage(
    context: android.content.Context,
    doc: Document,
    modifier: Modifier = Modifier
) {
    val path = doc.maskedRelativePath ?: doc.thumbnailRelativePath ?: doc.relativePath
    val resolvedFile = resolveThumbnailFile(context, path)

    if (resolvedFile == null) {
        Box(
            modifier = modifier.background(doc.documentClass.badgeColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = docClassIcon(doc.documentClass),
                contentDescription = null,
                tint = doc.documentClass.badgeColor,
                modifier = Modifier.size(30.dp)
            )
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedFile)
            .crossfade(true)
            .build(),
        contentDescription = doc.displayTitle,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

private fun resolveThumbnailFile(context: android.content.Context, rawPath: String?): File? {
    if (rawPath.isNullOrBlank()) return null
    val trimmed = rawPath.trim().removePrefix("/")

    val candidates = listOf(
        File(context.filesDir, "NeoDocs/$trimmed"),
        File(context.filesDir, trimmed),
        File(rawPath)
    )
    return candidates.firstOrNull { it.exists() && it.isFile }
}

// ── Classification badge ───────────────────────────────────────────────────────

@Composable
private fun ClassificationBadge(
    document: Document,
    onOpenPicker: (() -> Unit)?,
    compact: Boolean = false
) {
    val cls      = document.documentClass
    val isOther  = cls == DocumentClass.OTHER
    val isManual = document.isManuallyClassified
    val showText = !compact

    val bg    = if (isOther) Color.Gray.copy(alpha = 0.12f) else cls.badgeColor.copy(alpha = 0.9f)
    val fg    = if (isOther) Color.Gray else Color.White

    val badgeShape = RoundedCornerShape(4.dp)
    if (onOpenPicker != null) {
        Surface(
            onClick      = onOpenPicker,
            shape        = badgeShape,
            color        = bg,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier          = Modifier.padding(
                    horizontal = if (compact) 5.dp else 6.dp,
                    vertical = if (compact) 2.dp else 3.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector        = docClassIcon(cls),
                    contentDescription = null,
                    tint               = fg,
                    modifier           = Modifier.size(if (compact) 10.dp else 9.dp)
                )
                if (showText) {
                    Text(
                        text       = if (isOther) "Unknown" else cls.displayName,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color      = fg,
                        maxLines   = 1
                    )
                }
                if (isManual && showText) {
                    Text("✎", fontSize = 8.sp, color = fg.copy(alpha = 0.75f))
                }
            }
        }
    } else {
        Surface(
            shape        = badgeShape,
            color        = bg,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier          = Modifier.padding(
                    horizontal = if (compact) 5.dp else 6.dp,
                    vertical = if (compact) 2.dp else 3.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector        = docClassIcon(cls),
                    contentDescription = null,
                    tint               = fg,
                    modifier           = Modifier.size(if (compact) 10.dp else 9.dp)
                )
                if (showText) {
                    Text(
                        text       = if (isOther) "Unknown" else cls.displayName,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color      = fg,
                        maxLines   = 1
                    )
                }
                if (isManual && showText) {
                    Text("✎", fontSize = 8.sp, color = fg.copy(alpha = 0.75f))
                }
            }
        }
    }
}

// ── Group/Paired/PDF badge ─────────────────────────────────────────────────────

@Composable
private fun GroupBadge(
    label: String,
    icon: ImageVector,
    backgroundColor: Color = Coral,
    compact: Boolean = false
) {
    Surface(
        shape        = RoundedCornerShape(4.dp),
        color        = backgroundColor.copy(alpha = 0.92f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier.padding(
                horizontal = if (compact) 5.dp else 6.dp,
                vertical = if (compact) 2.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(if (compact) 10.dp else 9.dp)
            )
            Text(
                text = label,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ── Selection badge ────────────────────────────────────────────────────────────

@Composable
private fun SelectionBadge(
    isSelected: Boolean,
    index: Int?,
    compact: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val badgeSize = if (compact) 20.dp else 24.dp
    if (isSelected) {
        Box(
            modifier         = Modifier
                .size(badgeSize)
                .background(primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (index != null) {
                Text(
                    text       = "$index",
                    fontSize   = if (compact) {
                        if (index > 9) 8.sp else 10.sp
                    } else {
                        if (index > 9) 9.sp else 11.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(if (compact) 11.dp else 13.dp)
                )
            }
        }
    } else {
        // Empty circle (unselected in selection mode)
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .border(if (compact) 1.5.dp else 2.dp, Color.White, CircleShape)
        )
    }
}

// ── Classification change sheet ────────────────────────────────────────────────

/**
 * Sheet content for picking a new document classification.
 *
 * iOS equivalent: ClassificationPickerSheet.swift — tapping a class calls
 * rerouteAfterReclassification() and saves immediately.
 */
@Composable
fun ClassificationChangeSheetContent(
    currentClass: DocumentClass,
    onPick: (DocumentClass) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = DocumentClass.entries.toList()   // Aadhaar, PAN, VoterID, DrivingLicence, Passport, Other

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
        ) {
            Text(
                text       = "Set Document Type",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "Override AI classification",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()

        // Class options
        choices.forEach { cls ->
            val isCurrent = currentClass == cls
            val clsColor  = cls.badgeColor

            Surface(
                onClick = { onPick(cls) },
                color   = if (isCurrent) clsColor.copy(alpha = 0.06f) else Color.Transparent
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Color dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(clsColor, CircleShape)
                    )
                    // Icon
                    Icon(
                        imageVector        = docClassIcon(cls),
                        contentDescription = null,
                        tint               = clsColor,
                        modifier           = Modifier.size(22.dp)
                    )
                    // Label
                    Text(
                        text       = if (cls == DocumentClass.OTHER) "Unknown / Clear" else cls.displayName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color      = MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.weight(1f)
                    )
                    // Checkmark on current
                    if (isCurrent) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = "Current",
                            tint               = clsColor,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (cls != choices.last()) {
                HorizontalDivider(modifier = Modifier.padding(start = 66.dp))
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Extension: label shown on the gallery card bottom strip */
private val Document.cardLabel: String
    get() = displayTitle

private fun Document.cardMetaLabel(gridColumns: Int): String? = when (gridColumns.coerceIn(2, 4)) {
    4 -> null
    3 -> compactFormattedDate
    else -> "${formattedSize} · ${formattedDate}"
}

private val Document.compactFormattedDate: String
    get() {
        val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
        return sdf.format(Date(dateAdded))
    }

private fun docClassIcon(cls: DocumentClass): ImageVector = when (cls) {
    DocumentClass.AADHAAR         -> Icons.Default.PersonOutline
    DocumentClass.PAN             -> Icons.Default.CreditCard
    DocumentClass.VOTER_ID        -> Icons.Default.HowToVote
    DocumentClass.DRIVING_LICENCE -> Icons.Default.DirectionsCar
    DocumentClass.PASSPORT        -> Icons.Default.Language
    DocumentClass.OTHER           -> Icons.AutoMirrored.Filled.HelpOutline
}
