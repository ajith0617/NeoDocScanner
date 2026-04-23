package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.neodocscanner.ui.theme.GreenAccent

/**
 * Expandable section card showing documents in a configurable-column gallery grid.
 *
 * iOS equivalent: SectionBlock in VaultChecklistView.swift — 2-column LazyVGrid
 * of DocumentCard composables, with a camera-placeholder when empty and an
 * "Add more" dashed card appended at the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionCard(
    sectionWithDocs: SectionWithDocs,
    isCollapsed: Boolean,
    isPulsing: Boolean = false,
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
    onScanToSection: (String) -> Unit = {},
    onLongPressSection: (String) -> Unit = {},
    onToggleSectionSelection: (String) -> Unit = {},
    /** Gallery columns per row (2–3). */
    gridColumns: Int = 2,
    modifier: Modifier = Modifier
) {
    val section   = sectionWithDocs.section
    val documents = sectionWithDocs.documents
    val allDocumentsInSection = sectionWithDocs.allDocumentsInSection

    val showSectionBulkHeader =
        contextMenuState.isSelectionMode && documents.isNotEmpty()

    val allSectionDocsSelected = documents.isNotEmpty() &&
        documents.all { it.id in contextMenuState.selectedIds }

    val headerInteractionSource = remember { MutableInteractionSource() }

    val accentColor = section.acceptedClasses.firstOrNull()?.let {
        DocumentClass.fromRaw(it)
    }?.badgeColor ?: MaterialTheme.colorScheme.primary

    val chevronAngle by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 90f,
        label       = "chevron_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isPulsing) Modifier.background(accentColor.copy(alpha = 0.07f))
                else Modifier
            )
    ) {
        // ── Header row (ripple / long-press on full strip except section circle) ─
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = headerInteractionSource,
                        indication        = ripple(bounded = true),
                        onClick             = onToggleCollapse,
                        onLongClick         = { onLongPressSection(section.id) }
                    )
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(14.dp).rotate(chevronAngle)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                    if (documents.isNotEmpty()) {
                        Text(
                            text       = "${documents.size}",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
            if (showSectionBulkHeader) {
                Icon(
                    imageVector        = if (allSectionDocsSelected) Icons.Default.CheckCircle
                                         else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (allSectionDocsSelected) "Section fully selected"
                                         else "Select or clear section",
                    tint               = if (allSectionDocsSelected) GreenAccent
                                         else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    modifier           = Modifier
                        .padding(end = 14.dp)
                        .size(22.dp)
                        .clickable { onToggleSectionSelection(section.id) }
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // ── Expandable gallery grid (2–4 columns) ─────────────────────────────
        AnimatedVisibility(
            visible = !isCollapsed,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            if (documents.isEmpty()) {
                // Dashed camera placeholder — iOS style "Tap to scan"
                EmptySectionPlaceholder(
                    onClick = { onScanToSection(section.id) },
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                // Non-scrollable grid (parent LazyColumn handles scrolling)
                Column(modifier = Modifier.padding(12.dp)) {
                    val showAddMoreInGrid = !contextMenuState.isSelectionMode &&
                        !contextMenuState.isAadhaarPairingMode &&
                        !contextMenuState.isPassportPairingMode &&
                        !contextMenuState.isGenericGroupingMode
                    val n = gridColumns.coerceIn(2, 3)
                    val chunks = documents.chunked(n)
                    chunks.forEachIndexed { rowIndex, rowDocs ->
                        Row(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowDocs.forEach { doc ->
                                DocumentGalleryCard(
                                    document                  = doc,
                                    allDocumentsInScope       = allDocumentsInSection,
                                    enableTypeSwitch          = false,
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
                            val missing = n - rowDocs.size
                            if (missing > 0) {
                                val isLastRow = rowIndex == chunks.lastIndex
                                if (showAddMoreInGrid && isLastRow) {
                                    AddMoreCard(
                                        onClick = { onScanToSection(section.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    repeat(missing - 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                } else {
                                    repeat(missing) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (showAddMoreInGrid && documents.isNotEmpty() && documents.size % n == 0) {
                        Row(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AddMoreCard(
                                onClick = { onScanToSection(section.id) },
                                modifier = Modifier.weight(1f)
                            )
                            repeat(n - 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Empty section placeholder ─────────────────────────────────────────────────

@Composable
private fun EmptySectionPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
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
private fun AddMoreCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
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
