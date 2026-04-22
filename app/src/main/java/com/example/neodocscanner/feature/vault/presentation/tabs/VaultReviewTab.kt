package com.example.neodocscanner.feature.vault.presentation.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.presentation.components.DocumentContextMenuState
import com.example.neodocscanner.feature.vault.presentation.components.DocumentGalleryCard

/**
 * Uncategorised tab — photo-style grid (column count from vault settings).
 */
@Composable
fun VaultReviewTab(
    documents: List<Document>,
    allDocumentsInScope: List<Document> = documents,
    contextMenuState: DocumentContextMenuState = DocumentContextMenuState(),
    onDeleteDocument: (String) -> Unit = {},
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
    onCancelGroupingModes: () -> Unit = {},
    onShowMoveSheet: (String) -> Unit = {},
    onShowRenameGroupDialog: (Document) -> Unit = {},
    onShowPageReorderSheet: (Document) -> Unit = {},
    onExportAsPdf: (String) -> Unit = {},
    onUngroupDocuments: (String) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    onUnmergePdf: (String) -> Unit = {},
    onSharePdf: (String) -> Unit = {},
    onOpenPdfViewer: (String) -> Unit = {},
    gridColumns: Int = 2,
    modifier: Modifier = Modifier
) {
    if (documents.isEmpty()) {
        EmptyUncategorisedState(modifier = modifier)
        return
    }

    val cols = gridColumns.coerceIn(2, 4)
    val gridSpacing = when (cols) {
        4 -> 4.dp
        3 -> 6.dp
        else -> 10.dp
    }
    val horizontalPadding = when (cols) {
        4 -> 6.dp
        3 -> 8.dp
        else -> 12.dp
    }
    LazyVerticalGrid(
        columns               = GridCells.Fixed(cols),
        modifier              = modifier.fillMaxSize(),
        contentPadding        = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = gridSpacing,
            bottom = 96.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        verticalArrangement   = Arrangement.spacedBy(gridSpacing)
    ) {
        items(documents, key = { it.id }) { document ->
            DocumentGalleryCard(
                document                  = document,
                allDocumentsInScope       = allDocumentsInScope,
                gridColumns               = cols,
                enableTypeSwitch          = false,
                onTap                     = { onOpenDocument(document.id) },
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
                onOpenPdfViewer           = onOpenPdfViewer
            )
        }
    }
}

@Composable
private fun EmptyUncategorisedState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                modifier           = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text       = "Nothing here yet",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text      = "Scans that are not placed in a category appear here. Use the camera to add documents.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
