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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.presentation.VaultUiState
import com.example.neodocscanner.feature.vault.presentation.components.DocumentContextMenuState
import com.example.neodocscanner.feature.vault.presentation.components.SectionCard

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

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            CategoriesHeader(
                completedSections = uiState.completedSectionCount,
                totalSections     = uiState.sectionsWithDocs.size
            )
        }

        items(uiState.sectionsWithDocs, key = { it.section.id }) { swd ->
            SectionCard(
                sectionWithDocs          = swd,
                isCollapsed              = swd.section.id in uiState.collapsedSectionIds,
                isPulsing                = swd.section.id in uiState.pulsingSectionIds,
                onToggleCollapse         = { onToggleCollapse(swd.section.id) },
                onOpenDocument           = onOpenDocument,
                onReclassify             = onReclassify,
                contextMenuState         = contextMenuState,
                onEnterSelectionMode     = onEnterSelectionMode,
                onToggleSelection        = onToggleSelection,
                onStartAadhaarPairing    = onStartAadhaarPairing,
                onConfirmAadhaarPair     = onConfirmAadhaarPair,
                onStartPassportPairing   = onStartPassportPairing,
                onConfirmPassportPair    = onConfirmPassportPair,
                onStartGenericGrouping   = onStartGenericGrouping,
                onToggleGenericCandidate = onToggleGenericCandidate,
                onRequestGenericGroupName = onRequestGenericGroupName,
                onCancelGroupingModes    = onCancelGroupingModes,
                onShowMoveSheet          = onShowMoveSheet,
                onShowRenameGroupDialog  = onShowRenameGroupDialog,
                onShowPageReorderSheet   = onShowPageReorderSheet,
                onExportAsPdf            = onExportAsPdf,
                onUngroupDocuments       = onUngroupDocuments,
                onDeleteDocument         = onDeleteDocument,
                onDeleteGroup            = onDeleteGroup,
                onUnmergePdf             = onUnmergePdf,
                onSharePdf               = onSharePdf,
                onOpenPdfViewer          = onOpenPdfViewer,
                onScanToSection          = onScanToSection,
                onLongPressSection       = onLongPressSection,
                onToggleSectionSelection = onToggleSectionSelection,
                gridColumns              = uiState.galleryGridColumns
            )
        }

        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun CategoriesHeader(completedSections: Int, totalSections: Int) {
    if (totalSections == 0) return
    Row(
        modifier          = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = "Categories",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.weight(1f)
        )
        // Text(
        //     text  = "$completedSections / $totalSections complete",
        //     style = MaterialTheme.typography.labelMedium,
        //     color = if (completedSections == totalSections)
        //         MaterialTheme.colorScheme.primary
        //     else
        //         MaterialTheme.colorScheme.onSurfaceVariant
        // )
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
