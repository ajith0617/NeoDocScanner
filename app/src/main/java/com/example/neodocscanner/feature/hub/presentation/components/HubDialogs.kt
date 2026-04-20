package com.example.neodocscanner.feature.hub.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.ApplicationInstance

/**
 * Rename dialog for an application instance.
 *
 * iOS equivalent: The TextFieldAlert .alert modifier in ApplicationHubView.swift.
 */
@Composable
fun RenameInstanceDialog(
    instance: ApplicationInstance,
    currentInput: String,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = {
            Icon(
                imageVector        = Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Rename Application", fontWeight = FontWeight.SemiBold)
        },
        text = {
            OutlinedTextField(
                value         = currentInput,
                onValueChange = onInputChange,
                label         = { Text("Application name") },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = currentInput.isNotBlank()
            ) {
                Text("Rename", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Delete confirmation dialog.
 *
 * iOS equivalent: The destructive .alert in ApplicationHubView.swift
 * that warns "This will delete all documents in the vault."
 */
@Composable
fun DeleteInstanceDialog(
    instance: ApplicationInstance,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = {
            Icon(
                imageVector        = Icons.Default.Delete,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Delete Application?", fontWeight = FontWeight.SemiBold)
        },
        text  = {
            Text(
                text  = "\"${instance.customName}\" and all its documents will be permanently deleted. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text       = "Delete",
                    color      = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
