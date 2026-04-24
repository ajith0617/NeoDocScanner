package com.example.neodocscanner.feature.vault.presentation.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.presentation.VaultUiState
import com.example.neodocscanner.feature.vault.presentation.components.SectionCardAddMoreRow
import com.example.neodocscanner.feature.vault.presentation.components.SectionCardDocumentRow
import com.example.neodocscanner.feature.vault.presentation.components.SectionCardEmptyState
import com.example.neodocscanner.feature.vault.presentation.components.SectionCardHeader
import com.example.neodocscanner.feature.vault.presentation.components.DocumentContextMenuState
import com.example.neodocscanner.feature.vault.presentation.components.buildGroupedDocumentsByGroupId

/**
 * The Categories tab — shows all document categories (sections).
 * Passes context menu state down to SectionCard → DocumentListItem.
 *
 * iOS equivalent: VaultChecklistView.swift
 */
@Composable
fun VaultChecklistTab(
    uiState: VaultUiState,
    contextMenuState: DocumentContextMenuState,
    onToggleCollapse: (String) -> Unit,
    onOpenDocument: (String) -> Unit = {},
    onReclassify: (String, DocumentClass) -> Unit = { _, _ -> },
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
    modifier: Modifier = Modifier
) {
    if (uiState.sectionsWithDocs.isEmpty()) {
        EmptyChecklistState(modifier = modifier)
        return
    }

    val gridColumns = uiState.galleryGridColumns.coerceIn(2, 3)
    val showAddMoreInGrid = !contextMenuState.isSelectionMode &&
        !contextMenuState.isAadhaarPairingMode &&
        !contextMenuState.isPassportPairingMode &&
        !contextMenuState.isGenericGroupingMode
    val groupedDocumentsBySectionId = remember(uiState.sectionsWithDocs) {
        uiState.sectionsWithDocs.associate { swd ->
            swd.section.id to buildGroupedDocumentsByGroupId(swd.allDocumentsInSection)
        }
    }

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
//            CategoriesHeader(
//                completedSections = uiState.completedSectionCount,
//                totalSections     = uiState.sectionsWithDocs.size
//            )
        }

        uiState.sectionsWithDocs.forEach { swd ->
            val sectionId = swd.section.id
            val isCollapsed = sectionId in uiState.collapsedSectionIds
            val isPulsing = sectionId in uiState.pulsingSectionIds
            val groupDocumentsByGroupId = groupedDocumentsBySectionId[sectionId].orEmpty()

            item(
                key = "section-header-$sectionId",
                contentType = "section-header"
            ) {
                SectionCardHeader(
                    sectionWithDocs = swd,
                    isCollapsed = isCollapsed,
                    isPulsing = isPulsing,
                    contextMenuState = contextMenuState,
                    onToggleCollapse = { onToggleCollapse(sectionId) },
                    onLongPressSection = onLongPressSection,
                    onToggleSectionSelection = onToggleSectionSelection
                )
            }

            if (!isCollapsed) {
                if (swd.documents.isEmpty()) {
                    item(
                        key = "section-empty-$sectionId",
                        contentType = "section-empty"
                    ) {
                        SectionCardEmptyState(
                            onClick = { onScanToSection(sectionId) },
                            section = swd.section,
                            isPulsing = isPulsing,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    val rows = swd.documents.chunked(gridColumns)
                    rows.forEachIndexed { rowIndex, rowDocs ->
                        val showTrailingAddMoreSlot =
                            showAddMoreInGrid &&
                                rowIndex == rows.lastIndex &&
                                rowDocs.size < gridColumns

                        item(
                            key = "section-row-$sectionId-$rowIndex",
                            contentType = "section-row"
                        ) {
                            SectionCardDocumentRow(
                                rowDocuments = rowDocs,
                                gridColumns = gridColumns,
                                groupDocumentsByGroupId = groupDocumentsByGroupId,
                                contextMenuState = contextMenuState,
                                onOpenDocument = onOpenDocument,
                                onReclassify = onReclassify,
                                onEnterSelectionMode = onEnterSelectionMode,
                                onToggleSelection = onToggleSelection,
                                onStartAadhaarPairing = onStartAadhaarPairing,
                                onConfirmAadhaarPair = onConfirmAadhaarPair,
                                onStartPassportPairing = onStartPassportPairing,
                                onConfirmPassportPair = onConfirmPassportPair,
                                onStartGenericGrouping = onStartGenericGrouping,
                                onToggleGenericCandidate = onToggleGenericCandidate,
                                onRequestGenericGroupName = onRequestGenericGroupName,
                                onCancelGroupingModes = onCancelGroupingModes,
                                onShowMoveSheet = onShowMoveSheet,
                                onShowRenameGroupDialog = onShowRenameGroupDialog,
                                onShowPageReorderSheet = onShowPageReorderSheet,
                                onExportAsPdf = onExportAsPdf,
                                onUngroupDocuments = onUngroupDocuments,
                                onDeleteDocument = onDeleteDocument,
                                onDeleteGroup = onDeleteGroup,
                                onUnmergePdf = onUnmergePdf,
                                onSharePdf = onSharePdf,
                                onOpenPdfViewer = onOpenPdfViewer,
                                onScanToSection = { onScanToSection(sectionId) },
                                showAddMoreSlot = showTrailingAddMoreSlot,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = if (rowIndex == 0) 12.dp else 0.dp,
                                    bottom = if (rowIndex == rows.lastIndex &&
                                        !(showAddMoreInGrid && swd.documents.size % gridColumns == 0)
                                    ) {
                                        12.dp
                                    } else {
                                        10.dp
                                    }
                                )
                            )
                        }
                    }

                    if (showAddMoreInGrid && swd.documents.size % gridColumns == 0) {
                        item(
                            key = "section-add-more-$sectionId",
                            contentType = "section-row"
                        ) {
                            SectionCardAddMoreRow(
                                gridColumns = gridColumns,
                                onScanToSection = { onScanToSection(sectionId) },
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}


@Composable
private fun EmptyChecklistState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Default.Category,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.outline,
                modifier           = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text  = "No Categories Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "Tap the camera button to scan your first document",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
