package com.example.neodocscanner.feature.vault.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.feature.vault.presentation.detail.components.ClassificationPickerSheet
import com.example.neodocscanner.feature.vault.presentation.detail.components.DocumentIntelligencePanel
import com.example.neodocscanner.feature.vault.presentation.detail.components.OcrResultPanel
import com.example.neodocscanner.feature.vault.presentation.detail.components.RenamePanel

/**
 * Full document detail sheet — metadata, intelligence panel, rename, OCR text.
 *
 * iOS equivalent: DocumentDetailView.swift — presented as a `.sheet(.large)` from
 * DocumentFullscreenView.
 *
 * Android adaptation: ModalBottomSheet with scroll content (Material 3 bottom sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailSheet(
    document: Document,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    // Seed ViewModel with this document on first composition
    LaunchedEffect(document.id) { viewModel.init(document) }

    val state by viewModel.state.collectAsState()

    // Class picker sheet (nested)
    val classPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showDeleteDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header row (title + Done) ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Document information",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text       = document.displayName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── Metadata section ──────────────────────────────────────────────
            MetadataSection(document = document)

            // ── Document Intelligence Panel (if classification complete) ──────
            if (document.isIntelligenceProcessed) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                DocumentIntelligencePanel(document = document)
            }

            // ── Manual reclassification (only when document is classified) ────
            if (document.documentClassRaw != null || document.isManuallyClassified) {
                TextButton(
                    onClick = viewModel::showClassPicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text  = "Change Document Type",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── Rename section ────────────────────────────────────────────────
            RenamePanel(
                renameText         = state.renameText,
                onRenameTextChange = viewModel::onRenameTextChange,
                renameSuccess      = state.renameSuccess,
                renameError        = state.renameError,
                isRenameEnabled    = viewModel.isRenameEnabled,
                isOcrProcessed     = document.isOcrProcessed,
                onAutoGenerate     = viewModel::autoGenerateName,
                onSave             = viewModel::applyRename
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── OCR Result Panel ──────────────────────────────────────────────
            OcrResultPanel(
                document     = document,
                isExtracting = false,  // OCR from viewer; detail only shows persisted result
                ocrError     = null,
                onExtract    = {},     // from detail sheet, extraction is viewer-only
                onReExtract  = {}
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── Delete ────────────────────────────────────────────────────────
            TextButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Delete Document", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete Document?", fontWeight = FontWeight.SemiBold) },
            text    = {
                Text(
                    "This will permanently delete the file. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument { onDeleted() }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Classification Picker ─────────────────────────────────────────────────
    if (state.showClassPicker) {
        ClassificationPickerSheet(
            currentClass = document.documentClass,
            sheetState   = classPickerState,
            onSelect     = { cls ->
                viewModel.reclassify(cls)
            },
            onDismiss    = viewModel::dismissClassPicker
        )
    }
}

// ── Metadata Section ──────────────────────────────────────────────────────────

@Composable
private fun MetadataSection(document: Document) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = "Details",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                MetadataRow("Name", document.displayName)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                MetadataRow("Size", document.formattedSize)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                MetadataRow("Added", document.formattedDate)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                MetadataRow("Category", document.categoryName)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                MetadataRow("Type", document.documentType.rawValue)
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.width(88.dp)
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
