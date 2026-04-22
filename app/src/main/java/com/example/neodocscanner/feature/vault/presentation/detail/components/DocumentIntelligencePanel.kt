package com.example.neodocscanner.feature.vault.presentation.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import com.example.neodocscanner.feature.vault.presentation.components.documentClassVectorIcon

/**
 * Shows the auto-detected document class badge and extracted key fields.
 *
 * iOS equivalent: DocumentIntelligencePanel.swift
 * Shown in DocumentDetailSheet between metadata and rename sections
 * when document.isIntelligenceProcessed == true.
 */
@Composable
fun DocumentIntelligencePanel(
    document: Document,
    modifier: Modifier = Modifier
) {
    if (!document.isIntelligenceProcessed) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector  = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint         = MaterialTheme.colorScheme.primary,
                    modifier     = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = "Document Intelligence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Classification badge ──────────────────────────────────────────
            ClassificationBadge(docClass = document.documentClass)

            // ── Extracted fields grid ─────────────────────────────────────────
            val fields = document.decodedFields
            if (fields.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val visibleFields = fields
                        .filter { it.label.isNotBlank() && it.value.isNotBlank() }
                    visibleFields.forEach { field ->
                        IntelligenceFieldCard(field = field)
                    }
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No extracted fields found yet for this document.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntelligenceFieldCard(field: DocumentField) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Classification Badge ──────────────────────────────────────────────────────

@Composable
internal fun ClassificationBadge(docClass: DocumentClass) {
    val color = docClass.badgeColor
    val icon  = documentClassVectorIcon(docClass)

    Surface(
        shape = CircleShape,
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(14.dp)
            )
            Text(
                text       = docClass.displayName,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }
    }
}

