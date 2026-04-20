package com.example.neodocscanner.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Settings screen.
 *
 * iOS equivalent: SettingsView.swift — sheet presented from ApplicationHubView toolbar.
 *
 * Sections:
 *  1. Smart Naming  — autoRenameEnabled toggle + description
 *  2. Aadhaar Privacy — keepOriginalAfterMask toggle + description + warning
 *  3. About         — app name, version, build, platform
 *
 * UX adaptations:
 *  - TopAppBar with back navigation instead of iOS "Done" toolbar button
 *  - Material 3 Cards replace iOS inset grouped List
 *  - Switch uses M3 styling (primary tint automatically matches theme)
 *  - Warning banner uses M3 error container colour instead of iOS orange label
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title           = { Text("Settings") },
                navigationIcon  = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            content        = {
                // ── Smart Naming ───────────────────────────────────────────────
                item {
                    SettingsSectionHeader(
                        title = "Smart Naming",
                        icon  = Icons.Default.AutoAwesome
                    )
                    SettingsCard {
                        ToggleRow(
                            label       = "Auto-rename scanned documents",
                            description = "Files are renamed after scan using document type, extracted name, and scan date — e.g. Aadhaar_Ravi_Kumar_20260320_1430",
                            checked     = prefs.autoRenameEnabled,
                            onToggle    = viewModel::toggleAutoRename
                        )
                        if (!prefs.autoRenameEnabled) {
                            HorizontalDivider()
                            InfoBanner(
                                text = "You can still rename any document manually from its detail screen."
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ── Aadhaar Privacy ────────────────────────────────────────────
                item {
                    SettingsSectionHeader(
                        title = "Aadhaar Privacy",
                        icon  = Icons.Outlined.Shield
                    )
                    SettingsCard {
                        ToggleRow(
                            label       = "Keep original after masking",
                            description = if (prefs.keepOriginalAfterMask)
                                "A separate masked copy is saved alongside the original — the unmasked original is preserved."
                            else
                                "The original file is overwritten with the masked version — the unmasked copy is permanently removed.",
                            checked     = prefs.keepOriginalAfterMask,
                            onToggle    = viewModel::toggleKeepOriginalAfterMask
                        )
                        if (!prefs.keepOriginalAfterMask) {
                            HorizontalDivider()
                            WarningBanner(
                                text = "Turning this off permanently removes the unmasked Aadhaar original. This cannot be undone for documents already scanned."
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = "Masking hides the first 8 digits of every Aadhaar number detected in a scanned image using on-device ML. No data leaves the device.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        )
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier          = Modifier.padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Card container ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

// ── Toggle row ─────────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

// ── Info banner ────────────────────────────────────────────────────────────────

@Composable
private fun InfoBanner(text: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector        = Icons.Default.Info,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(15.dp).padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Warning banner ─────────────────────────────────────────────────────────────

@Composable
private fun WarningBanner(text: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(
                Modifier.padding(0.dp) // allows card bg to show
            ),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector        = Icons.Outlined.Shield,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.error,
            modifier           = Modifier.size(15.dp).padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

