package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Half-height sheet shown before confirming Aadhaar pairs, Passport pairs,
 * generic N-way groups, and selection-mode groups.
 *
 * iOS equivalent: GroupNameSheet.swift
 *
 * Lets the user optionally name the group before committing.
 * Tapping "Create without name" creates the group with no name.
 * The auto-generated name is pre-filled from OCR / classification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupNameSheet(
    name: String,
    onNameChange: (String) -> Unit,
    isAadhaar: Boolean = false,
    isPassport: Boolean = false,
    docCount: Int = 2,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Headline ─────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text       = when {
                    isAadhaar  -> "Name this Aadhaar pair"
                    isPassport -> "Name this Passport pair"
                    else       -> "Name this group"
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text  = when {
                    isAadhaar  -> "Give this front + back pair a name so you can identify it later."
                    isPassport -> "Give this data + address pair a name so you can identify it later."
                    else       -> "$docCount documents will be grouped into one card."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Text field ────────────────────────────────────────────────────
            OutlinedTextField(
                value          = name,
                onValueChange  = onNameChange,
                label          = { Text("Group name") },
                leadingIcon    = { Icon(Icons.Default.Tag, null) },
                trailingIcon   = if (name.isNotEmpty()) {
                    {
                        IconButton(onClick = { onNameChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                placeholder    = {
                    Text(
                        when {
                            isAadhaar  -> "e.g. Customer A Aadhaar"
                            isPassport -> "e.g. Customer A Passport"
                            else       -> "e.g. Customer A Income Docs"
                        }
                    )
                },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(name, onConfirm) }),
                modifier       = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Button(
                onClick  = { commit(name, onConfirm) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (name.trim().isEmpty()) "Create without name" else "Save & Group",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
}

private fun commit(name: String, onConfirm: (String?) -> Unit) {
    val trimmed = name.trim()
    onConfirm(trimmed.ifEmpty { null })
}
