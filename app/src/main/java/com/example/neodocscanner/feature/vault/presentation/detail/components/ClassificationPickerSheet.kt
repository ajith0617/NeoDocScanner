package com.example.neodocscanner.feature.vault.presentation.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.presentation.components.documentClassVectorIcon

/**
 * ModalBottomSheet for manual document reclassification.
 *
 * iOS equivalent: ClassificationPickerSheet inside DocumentCard.swift.
 *
 * Choices match iOS exactly:
 *   Aadhaar, PAN Card, Passport, Voter ID, Other (→ Unknown / Clear)
 *
 * On select:
 *   - Other → clears class, routes to Uncategorised (sectionId = null)
 *   - Any other → sets class, re-runs section routing automatically
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationPickerSheet(
    currentClass: DocumentClass,
    sheetState: SheetState,
    onSelect: (DocumentClass) -> Unit,
    onDismiss: () -> Unit
) {
    // Matches iOS choices list exactly (Driving Licence excluded until model trained)
    val choices = listOf(
        DocumentClass.AADHAAR,
        DocumentClass.PAN,
        DocumentClass.PASSPORT,
        DocumentClass.VOTER_ID,
        DocumentClass.OTHER
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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

            // ── Class options ─────────────────────────────────────────────────
            choices.forEachIndexed { i, cls ->
                val isCurrent = currentClass == cls
                val label     = if (cls == DocumentClass.OTHER) "Unknown / Clear" else cls.displayName
                val color     = cls.badgeColor

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cls) }
                        .then(
                            if (isCurrent)
                                Modifier.padding(0.dp) // background handled via Surface tint
                            else Modifier
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color dot
                    Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}

                    Spacer(Modifier.width(14.dp))

                    // Icon
                    Icon(
                        imageVector        = documentClassVectorIcon(cls),
                        contentDescription = null,
                        tint               = color,
                        modifier           = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(14.dp))

                    // Label
                    Text(
                        text       = label,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        modifier   = Modifier.weight(1f)
                    )

                    // Checkmark on current
                    if (isCurrent) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = "Current",
                            tint               = color,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }

                if (i < choices.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

