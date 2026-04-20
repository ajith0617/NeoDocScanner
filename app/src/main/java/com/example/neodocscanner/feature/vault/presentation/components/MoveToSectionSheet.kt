package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Folder
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.feature.vault.presentation.SectionWithDocs

/**
 * Bottom sheet that lets the user move a document to a different section
 * or to the Review Inbox (sectionId = null).
 *
 * iOS equivalent: MoveToSectionSheet.swift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToSectionSheet(
    document: Document?,
    sectionsWithDocs: List<SectionWithDocs>,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onMove: (sectionId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (document == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text       = "Move to Category",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            HorizontalDivider()

            // ── Review Inbox option ──────────────────────────────────────────
            SectionRow(
                icon      = Icons.Default.Inbox,
                iconTint  = MaterialTheme.colorScheme.secondary,
                title     = "Uncategorised",
                subtitle  = "Move to review inbox",
                isCurrent = document.sectionId == null,
                onClick   = { onMove(null) }
            )

            HorizontalDivider()

            // ── Sections ─────────────────────────────────────────────────────
            sectionsWithDocs.forEach { swd ->
                val sec = swd.section
                SectionRow(
                    icon      = Icons.Default.Folder,
                    iconTint  = MaterialTheme.colorScheme.primary,
                    title     = sec.title,
                    subtitle  = "${swd.documents.size}/${if (sec.maxDocuments > 0) sec.maxDocuments.toString() else "∞"} docs",
                    isCurrent = document.sectionId == sec.id,
                    onClick   = { onMove(sec.id) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun SectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = "Current",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}
