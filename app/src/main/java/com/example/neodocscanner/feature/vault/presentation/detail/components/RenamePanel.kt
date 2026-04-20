package com.example.neodocscanner.feature.vault.presentation.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Rename section shown inside the DocumentDetailSheet.
 *
 * iOS equivalent: DocumentDetailView.renameSection — text field + "Auto" button + Save.
 *
 * Behaviour matches iOS exactly:
 *  - Inline feedback clears when user edits the text field
 *  - Save is disabled when text is empty or unchanged
 *  - "Auto" calls DocumentNamingService on the ViewModel
 *  - Success badge auto-dismisses after 2 s (handled in ViewModel)
 */
@Composable
fun RenamePanel(
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    renameSuccess: Boolean,
    renameError: String?,
    isRenameEnabled: Boolean,
    isOcrProcessed: Boolean,
    onAutoGenerate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.DriveFileRenameOutline,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Rename",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Input row: text field + Auto button ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = renameText,
                    onValueChange = onRenameTextChange,
                    singleLine    = true,
                    placeholder   = { Text("File name", style = MaterialTheme.typography.bodySmall) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(8.dp),
                    textStyle     = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction      = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (isRenameEnabled) onSave() })
                )

                // ✦ Auto-generate button (matches iOS "✦ Auto")
                FilledTonalButton(
                    onClick = onAutoGenerate,
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    )
                ) {
                    Text("✦ ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Auto", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── OCR hint (shown when OCR not yet run) ─────────────────────────
            if (!isOcrProcessed) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text  = "Run text extraction first for a smarter Auto name.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Footer: inline feedback + Save button ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inline feedback (error or success)
                AnimatedVisibility(visible = renameError != null, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text  = renameError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                AnimatedVisibility(
                    visible = renameSuccess && renameError == null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text  = "Renamed successfully",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                FilledTonalButton(
                    onClick  = onSave,
                    enabled  = isRenameEnabled,
                    shape    = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 18.dp, vertical = 8.dp
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
