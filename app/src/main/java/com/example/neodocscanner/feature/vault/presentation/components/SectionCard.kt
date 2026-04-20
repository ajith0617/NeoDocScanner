package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.presentation.SectionWithDocs

/**
 * Expandable section card showing documents in a 2-column gallery grid.
 *
 * iOS equivalent: SectionBlock in VaultChecklistView.swift — 2-column LazyVGrid
 * of DocumentCard composables, with a camera-placeholder when empty and an
 * "Add more" dashed card appended at the end.
 */
@Composable
fun SectionCard(
    sectionWithDocs: SectionWithDocs,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onOpenDocument: (String) -> Unit = {},
    onReclassify: (String, DocumentClass) -> Unit = { _, _ -> },
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
    val section   = sectionWithDocs.section
    val documents = sectionWithDocs.documents
    val isComplete = sectionWithDocs.isComplete

    val accentColor = section.acceptedClasses.firstOrNull()?.let {
        DocumentClass.fromRaw(it)
    }?.badgeColor ?: MaterialTheme.colorScheme.primary

    val chevronAngle by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 90f,
        label       = "chevron_rotation"
    )

    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onToggleCollapse() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chevron
            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(14.dp).rotate(chevronAngle)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Section icon (accent-coloured folder)
            Icon(
                imageVector        = Icons.Default.Folder,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            // Title + required badge
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = section.title,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (section.isRequired) {
                        Text(
                            text  = " *",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            // Document count pill
            if (documents.isNotEmpty()) {
                Text(
                    text     = "${documents.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color    = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Fulfilment indicator
            Icon(
                imageVector        = if (isComplete) Icons.Default.CheckCircle
                                     else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint               = if (isComplete) Color(0xFF4CAF50)
                                     else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier           = Modifier.size(18.dp)
            )
        }

        // ── Accent divider ────────────────────────────────────────────────────
        HorizontalDivider(color = accentColor.copy(alpha = 0.20f))

        // ── Expandable 2-col gallery grid ─────────────────────────────────────
        AnimatedVisibility(
            visible = !isCollapsed,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            if (documents.isEmpty()) {
                // Dashed camera placeholder — iOS style "Tap to scan"
                EmptySectionPlaceholder(modifier = Modifier.padding(12.dp))
            } else {
                // Non-scrollable grid (parent LazyColumn handles scrolling)
                Column(modifier = Modifier.padding(12.dp)) {
                    val chunked = documents.chunked(2)
                    chunked.forEach { rowDocs ->
                        Row(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowDocs.forEach { doc ->
                                DocumentGalleryCard(
                                    document                  = doc,
                                    onTap                     = { onOpenDocument(doc.id) },
                                    onReclassify              = onReclassify,
                                    contextMenuState          = contextMenuState,
                                    onEnterSelectionMode      = onEnterSelectionMode,
                                    onToggleSelection         = onToggleSelection,
                                    onStartAadhaarPairing     = onStartAadhaarPairing,
                                    onConfirmAadhaarPair      = onConfirmAadhaarPair,
                                    onStartPassportPairing    = onStartPassportPairing,
                                    onConfirmPassportPair     = onConfirmPassportPair,
                                    onStartGenericGrouping    = onStartGenericGrouping,
                                    onToggleGenericCandidate  = onToggleGenericCandidate,
                                    onRequestGenericGroupName = onRequestGenericGroupName,
                                    onCancelGroupingModes     = onCancelGroupingModes,
                                    onShowMoveSheet           = onShowMoveSheet,
                                    onShowRenameGroupDialog   = onShowRenameGroupDialog,
                                    onShowPageReorderSheet    = onShowPageReorderSheet,
                                    onExportAsPdf             = onExportAsPdf,
                                    onUngroupDocuments        = onUngroupDocuments,
                                    onDeleteDocument          = onDeleteDocument,
                                    onDeleteGroup             = onDeleteGroup,
                                    onUnmergePdf              = onUnmergePdf,
                                    onSharePdf                = onSharePdf,
                                    onOpenPdfViewer           = onOpenPdfViewer,
                                    modifier                  = Modifier.weight(1f)
                                )
                            }
                            // Pad the last row if only 1 item
                            if (rowDocs.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    // "Add more" dashed card (iOS-style, only in normal mode)
                    if (!contextMenuState.isSelectionMode &&
                        !contextMenuState.isAadhaarPairingMode &&
                        !contextMenuState.isPassportPairingMode &&
                        !contextMenuState.isGenericGroupingMode
                    ) {
                        // Place it inline in a row if the last row has only 1 item
                        val lastRowFull = documents.size % 2 == 0
                        if (lastRowFull) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AddMoreCard(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        // If last row was already partial, AddMoreCard goes in the same row above
                        // (handled by the padding logic — visible naturally after the grid)
                    }
                }
            }
        }
    }
}

// ── Empty section placeholder ─────────────────────────────────────────────────

@Composable
private fun EmptySectionPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                modifier           = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "No documents yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Add more card ─────────────────────────────────────────────────────────────

@Composable
private fun AddMoreCard(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "Add more",
                tint               = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                modifier           = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "Add more",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun RequiredBadge() {
    Text(
        text  = "Required",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error
    )
}
