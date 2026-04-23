package com.example.neodocscanner.feature.hub.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.ApplicationTemplate

/**
 * Bottom sheet for creating a new application vault.
 *
 * iOS equivalent: The .sheet() modal in ApplicationHubView.swift that shows
 * a template picker and custom name field.
 *
 * Two creation paths:
 * 1. Pick a template → enter custom name → Create
 * 2. Scan QR code    → auto-populates template and name (Module 5)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVaultBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (template: ApplicationTemplate, customName: String) -> Unit,
    onScanQR: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTemplate by remember { mutableStateOf<ApplicationTemplate?>(null) }
    var customName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Text(
                text       = "New Application",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "Choose a template or scan a QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onScanQR,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR code", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Templates",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ── Template grid ─────────────────────────────────────────────────
            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding      = PaddingValues(bottom = 4.dp),
                modifier            = Modifier.height(280.dp)
            ) {
                items(ApplicationTemplate.all) { template ->
                    TemplateCard(
                        template   = template,
                        isSelected = selectedTemplate?.id == template.id,
                        onSelect   = {
                            selectedTemplate = template
                            if (customName.isBlank()) customName = template.name
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Custom name field ─────────────────────────────────────────────
            OutlinedTextField(
                value         = customName,
                onValueChange = { customName = it },
                label         = { Text("Application name (optional)") },
                placeholder   = { Text(selectedTemplate?.name ?: "Enter a name") },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth()
            )
            selectedTemplate?.let { template ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Button(
                onClick  = {
                    selectedTemplate?.let { t ->
                        onCreate(t, customName)
                    }
                },
                enabled  = selectedTemplate != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text("Create application", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TemplateCard(
    template: ApplicationTemplate,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val icon = templateIconFor(template.iconName)
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    else
        MaterialTheme.colorScheme.surface

    OutlinedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(onClick = onSelect),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                }
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint      = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text      = template.name,
                style     = MaterialTheme.typography.labelMedium,
                color     = if (isSelected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines  = 2
            )
        }
    }
}

private fun templateIconFor(iconName: String): ImageVector {
    return when (iconName) {
        "account_balance" -> Icons.Default.AccountBalance
        "home" -> Icons.Default.Home
        "currency_rupee" -> Icons.Default.CurrencyRupee
        "language" -> Icons.Default.Language
        "flight" -> Icons.Default.FlightTakeoff
        "medical_services" -> Icons.Default.MedicalServices
        else -> Icons.Default.FolderOpen
    }
}
