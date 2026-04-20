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
 * Uncategorised tab — 2-column gallery grid, same layout as iOS VaultReviewView.
 *
 * iOS equivalent: VaultReviewView.swift — LazyVGrid with DocumentCard + context menus.
 */
@Composable
fun VaultReviewTab(
    documents: List<Document>,
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
    modifier: Modifier = Modifier
) {
    if (documents.isEmpty()) {
        EmptyUncategorisedState(modifier = modifier)
        return
    }

    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        modifier              = modifier.fillMaxSize(),
        contentPadding        = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Documents that couldn't be auto-routed",
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        items(documents, key = { it.id }) { document ->
            DocumentGalleryCard(
                document                  = document,
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
            modifier            = Modifier.padding(40.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier           = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text       = "All Categorised",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text      = "Every scanned document has been routed to a category.\nDocuments that can't be auto-classified will appear here.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
