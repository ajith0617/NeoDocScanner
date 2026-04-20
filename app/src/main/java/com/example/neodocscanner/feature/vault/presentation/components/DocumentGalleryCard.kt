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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import java.io.File

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showClassPicker by remember { mutableStateOf(false) }

    val isSelected     = document.id in contextMenuState.selectedIds
    val selectionIndex = contextMenuState.selectionOrder.indexOf(document.id)
        .let { if (it >= 0) it + 1 else null }
    val isAnchor       = document.id == contextMenuState.genericAnchorId
    val isCandidate    = document.id in contextMenuState.genericCandidateIds
    val isAadhaarAnchor  = document.id == contextMenuState.aadhaarAnchorId
    val isPassportAnchor = document.id == contextMenuState.passportAnchorId
    val groupMemberCount = contextMenuState.groupMemberCountFor(document.id)

    val showHighlight = isAnchor || isCandidate || isAadhaarAnchor || isPassportAnchor
    val isInGroupingMode = contextMenuState.isAadhaarPairingMode
        || contextMenuState.isPassportPairingMode
        || contextMenuState.isGenericGroupingMode

    val isMergedPdf  = document.exportedFromGroupId != null
    val isPaired     = document.aadhaarSide != null && document.groupId != null
    val isGroupStack = document.groupId != null && !isPaired && !isMergedPdf

    val primaryColor = MaterialTheme.colorScheme.primary

    // ── Classification picker sheet ───────────────────────────────────────────
    if (showClassPicker) {
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

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected || showHighlight) 2.5.dp else 1.dp,
                    color = when {
                        isSelected      -> primaryColor
                        showHighlight   -> primaryColor
                        else            -> Color.Black.copy(alpha = 0.05f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .combinedClickable(
                    enabled     = document.processingStatus == ProcessingStatus.COMPLETE
                                  || contextMenuState.isSelectionMode,
                    onClick     = {
                        when {
                            contextMenuState.isSelectionMode -> onToggleSelection(document)
                            contextMenuState.isAadhaarPairingMode && !isAadhaarAnchor ->
                                onConfirmAadhaarPair(document)
                            contextMenuState.isPassportPairingMode && !isPassportAnchor ->
                                onConfirmPassportPair(document)
                            contextMenuState.isGenericGroupingMode && !isAnchor ->
                                onToggleGenericCandidate(document)
                            else -> onTap()
                        }
                    },
                    onLongClick = {
                        if (!contextMenuState.isSelectionMode && !isInGroupingMode) {
                            showContextMenu = true
                        } else if (contextMenuState.isSelectionMode) {
                            onToggleSelection(document)
                        }
                    }
                )
        ) {
            // ── Thumbnail ─────────────────────────────────────────────────────
            ThumbnailLayer(document = document, context = context)

            // ── Constant dim so bottom text stays readable ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
            )

            // ── Selection dim overlay ─────────────────────────────────────────
            if (contextMenuState.isSelectionMode && !isSelected) {
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
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text       = document.cardLabel,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text     = "${document.formattedSize} · ${document.formattedDate}",
                        fontSize = 9.sp,
                        color    = Color.White.copy(alpha = 0.82f),
                        maxLines = 1
                    )
                }
            }

            // ── Aadhaar unpaired side label (bottom-right, above gradient) ────
            if (!contextMenuState.isSelectionMode && document.aadhaarSide != null
                && document.groupId == null
            ) {
                Text(
                    text     = if (document.aadhaarSide == "front") "Front only" else "Back only",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 34.dp, end = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        // ── Top-right badge overlay (outside clip) ────────────────────────────
        Box(
            modifier         = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 7.dp, end = 7.dp)
        ) {
            when {
                // ── Selection mode → numbered circle ─────────────────────────
                contextMenuState.isSelectionMode -> {
                    SelectionBadge(isSelected = isSelected, index = selectionIndex)
                }
                // ── Merged PDF badge ──────────────────────────────────────────
                isMergedPdf -> {
                    GroupBadge(
                        label = "Merged PDF",
                        icon  = Icons.Default.PictureAsPdf
                    )
                }
                // ── Aadhaar F+B paired badge ──────────────────────────────────
                isPaired -> {
                    GroupBadge(label = "F + B", icon = Icons.Default.PersonOutline)
                }
                // ── Generic group stack badge ─────────────────────────────────
                isGroupStack -> {
                    GroupBadge(
                        label = "×${groupMemberCount}",
                        icon  = Icons.Default.Image
                    )
                }
                // ── Classification badge (tappable) ───────────────────────────
                else -> {
                    ClassificationBadge(
                        document      = document,
                        onOpenPicker  = { showClassPicker = true }
                    )
                }
            }
        }

        // ── Context menu ──────────────────────────────────────────────────────
        DropdownMenu(
            expanded         = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            buildContextMenuItems(
                document               = document,
                contextMenuState       = contextMenuState,
                groupMemberCount       = groupMemberCount,
                onDismiss              = { showContextMenu = false },
                onEnterSelectionMode   = onEnterSelectionMode,
                onStartAadhaarPairing  = onStartAadhaarPairing,
                onStartPassportPairing = onStartPassportPairing,
                onStartGenericGrouping = onStartGenericGrouping,
                onShowMoveSheet        = onShowMoveSheet,
                onShowRenameGroup      = onShowRenameGroupDialog,
                onReorderPages         = onShowPageReorderSheet,
                onExportPdf            = onExportAsPdf,
                onUngroup              = onUngroupDocuments,
                onDelete               = onDeleteDocument,
                onDeleteGroup          = onDeleteGroup,
                onUnmerge              = onUnmergePdf,
                onSharePdf             = onSharePdf,
                onOpenPdfViewer        = onOpenPdfViewer
            )
        }
    }
}

// ── Thumbnail layer ────────────────────────────────────────────────────────────

@Composable
private fun ThumbnailLayer(document: Document, context: android.content.Context) {
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
            if (thumbPath.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(context.filesDir, "NeoDocs/$thumbPath"))
                        .crossfade(true)
                        .build(),
                    contentDescription = document.displayName,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
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

// ── Classification badge ───────────────────────────────────────────────────────

@Composable
private fun ClassificationBadge(
    document: Document,
    onOpenPicker: () -> Unit
) {
    val cls      = document.documentClass
    val isOther  = cls == DocumentClass.OTHER
    val isManual = document.isManuallyClassified

    val bg    = if (isOther) Color.Gray.copy(alpha = 0.12f) else cls.badgeColor.copy(alpha = 0.9f)
    val fg    = if (isOther) Color.Gray else Color.White

    Surface(
        onClick      = onOpenPicker,
        shape        = RoundedCornerShape(4.dp),
        color        = bg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = docClassIcon(cls),
                contentDescription = null,
                tint               = fg,
                modifier           = Modifier.size(9.dp)
            )
            Text(
                text       = if (isOther) "Unknown" else cls.displayName,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = fg
            )
            if (isManual) {
                Text("✎", fontSize = 8.sp, color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

// ── Group/Paired/PDF badge ─────────────────────────────────────────────────────

@Composable
private fun GroupBadge(label: String, icon: ImageVector) {
    Surface(
        shape        = RoundedCornerShape(4.dp),
        color        = Color(0xFFFF6D00).copy(alpha = 0.9f),  // iOS PrimaryOrange equivalent
        tonalElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(9.dp)
            )
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── Selection badge ────────────────────────────────────────────────────────────

@Composable
private fun SelectionBadge(isSelected: Boolean, index: Int?) {
    val primaryColor = MaterialTheme.colorScheme.primary
    if (isSelected) {
        Box(
            modifier         = Modifier
                .size(24.dp)
                .background(primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (index != null) {
                Text(
                    text       = "$index",
                    fontSize   = if (index > 9) 9.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(13.dp)
                )
            }
        }
    } else {
        // Empty circle (unselected in selection mode)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .border(2.dp, Color.White, CircleShape)
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
    get() = groupName ?: displayName

private fun docClassIcon(cls: DocumentClass): ImageVector = when (cls) {
    DocumentClass.AADHAAR         -> Icons.Default.PersonOutline
    DocumentClass.PAN             -> Icons.Default.CreditCard
    DocumentClass.VOTER_ID        -> Icons.Default.HowToVote
    DocumentClass.DRIVING_LICENCE -> Icons.Default.DirectionsCar
    DocumentClass.PASSPORT        -> Icons.Default.Language
    DocumentClass.OTHER           -> Icons.AutoMirrored.Filled.HelpOutline
}
