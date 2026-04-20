package com.example.neodocscanner.feature.hub.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.ApplicationInstance
import com.example.neodocscanner.feature.hub.presentation.ApplicationInstanceUi

/**
 * Card representing a single application vault in the hub list.
 *
 * iOS equivalent: The NavigationLink row inside ApplicationHubView's List,
 * with swipe-to-delete and context menu actions.
 *
 * Android UX adaptation:
 * - Uses ElevatedCard (Material 3) instead of a plain list row
 * - Long-press reveals a DropdownMenu for Rename / Archive / Delete
 * - Document count shown as a badge chip
 * - Archived vaults rendered with reduced opacity + "Archived" label
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApplicationInstanceCard(
    instanceUi: ApplicationInstanceUi,
    onTap: () -> Unit,
    onRename: (ApplicationInstance) -> Unit,
    onArchive: (ApplicationInstance) -> Unit,
    onDelete: (ApplicationInstance) -> Unit
) {
    val instance = instanceUi.instance
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick      = onTap,
                onLongClick  = { menuExpanded = true }
            ),
        shape      = RoundedCornerShape(16.dp),
        elevation  = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors     = CardDefaults.elevatedCardColors(
            containerColor = if (instance.isArchived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // ── Icon ──────────────────────────────────────────────────────────
            VaultIconBadge(iconName = instance.iconName)

            Spacer(modifier = Modifier.width(14.dp))

            // ── Text block ────────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text       = instance.customName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1
                    )
                    if (instance.isArchived) {
                        ArchivedChip()
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text  = instance.templateName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    DocCountChip(count = instanceUi.documentCount)
                    Text(
                        text  = instance.formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // ── Context menu ──────────────────────────────────────────────────
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector         = Icons.Default.MoreVert,
                    contentDescription  = "Options",
                    tint                = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text        = { Text("Rename") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DriveFileRenameOutline,
                            contentDescription = null
                        )
                    },
                    onClick     = { menuExpanded = false; onRename(instance) }
                )
                DropdownMenuItem(
                    text        = {
                        Text(if (instance.isArchived) "Restore" else "Archive")
                    },
                    leadingIcon = {
                        Icon(
                            if (instance.isArchived) Icons.Default.Unarchive
                            else Icons.Default.Archive,
                            contentDescription = null
                        )
                    },
                    onClick     = { menuExpanded = false; onArchive(instance) }
                )
                DropdownMenuItem(
                    text        = {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick     = { menuExpanded = false; onDelete(instance) }
                )
            }
        }
    }
}

// ── Small reusable sub-composables ────────────────────────────────────────────

@Composable
private fun VaultIconBadge(iconName: String) {
    Surface(
        modifier  = Modifier
            .size(48.dp)
            .clip(CircleShape),
        color     = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Material icons by name aren't available at runtime; render
            // initials from iconName as a graceful fallback until we add
            // the imageVector lookup map in the theming module.
            Text(
                text  = iconName.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DocCountChip(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text      = if (count == 0) "No docs" else "$count doc${if (count == 1) "" else "s"}",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier  = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ArchivedChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text     = "Archived",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
