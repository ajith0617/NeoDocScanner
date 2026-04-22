package com.example.neodocscanner.feature.hub.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * - Uses a clean minimal surface with reduced visual noise
 * - Long-press reveals a DropdownMenu for Rename / Archive / Delete
 * - Real template icons make cards easier to scan than generated-looking initials
 * - Linked / synced / archived states are visible at a glance
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
    val accent = vaultAccentFor(instance.iconName)
    var menuExpanded by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (instance.isArchived) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        },
        animationSpec = spring(),
        label = "instance_card_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (instance.isArchived) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        } else {
            accent.strokeColor
        },
        animationSpec = spring(),
        label = "instance_card_border"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                VaultIconBadge(accent = accent)

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.customName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = instance.templateName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRename(instance)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (instance.isArchived) "Restore" else "Archive")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (instance.isArchived) {
                                        Icons.Default.Unarchive
                                    } else {
                                        Icons.Default.Archive
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onArchive(instance)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete(instance)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            val hasStatusChip = instance.linkStatus != "unlinked" || instance.isArchived
            if (hasStatusChip) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (instance.linkStatus == "linked") {
                        StateChip(
                            text = "Linked",
                            icon = Icons.Default.Link,
                            containerColor = accent.containerColor,
                            contentColor = accent.tintColor
                        )
                    }
                    if (instance.linkStatus == "synced") {
                        StateChip(
                            text = "Synced",
                            icon = Icons.Default.Sync,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (instance.isArchived) {
                        StateChip(
                            text = "Archived",
                            icon = Icons.Default.Archive,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaPill(
                    text = if (instanceUi.documentCount == 0) {
                        "No documents yet"
                    } else {
                        "${instanceUi.documentCount} document${if (instanceUi.documentCount == 1) "" else "s"}"
                    },
                    containerColor = accent.containerColor,
                    contentColor = accent.tintColor
                )

                MetaPill(
                    text = instance.formattedDate,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun VaultIconBadge(accent: VaultAccent) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = accent.containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = accent.icon,
                contentDescription = null,
                tint = accent.tintColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun StateChip(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun MetaPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private data class VaultAccent(
    val icon: ImageVector,
    val tintColor: Color,
    val containerColor: Color,
    val strokeColor: Color
)

private fun vaultAccentFor(iconName: String): VaultAccent {
    return when (iconName) {
        "account_balance" -> VaultAccent(
            icon = Icons.Default.AccountBalance,
            tintColor = Color(0xFF86523D),
            containerColor = Color(0xFFF5E6DD),
            strokeColor = Color(0xFFE4C5B5)
        )
        "home" -> VaultAccent(
            icon = Icons.Default.Home,
            tintColor = Color(0xFF426858),
            containerColor = Color(0xFFE3F0EA),
            strokeColor = Color(0xFFC4DACF)
        )
        "currency_rupee" -> VaultAccent(
            icon = Icons.Default.CurrencyRupee,
            tintColor = Color(0xFF7A5A14),
            containerColor = Color(0xFFF6EBCB),
            strokeColor = Color(0xFFE6D49E)
        )
        "language" -> VaultAccent(
            icon = Icons.Default.Language,
            tintColor = Color(0xFF365C87),
            containerColor = Color(0xFFE5EDF8),
            strokeColor = Color(0xFFC2D2E8)
        )
        "flight" -> VaultAccent(
            icon = Icons.Default.FlightTakeoff,
            tintColor = Color(0xFF7A4863),
            containerColor = Color(0xFFF3E4ED),
            strokeColor = Color(0xFFE3BED0)
        )
        "medical_services" -> VaultAccent(
            icon = Icons.Default.MedicalServices,
            tintColor = Color(0xFF8B4141),
            containerColor = Color(0xFFF6E2E2),
            strokeColor = Color(0xFFE3BCBC)
        )
        else -> VaultAccent(
            icon = Icons.Default.FolderOpen,
            tintColor = Color(0xFF5F4B8B),
            containerColor = Color(0xFFEAE5F8),
            strokeColor = Color(0xFFD1C8EC)
        )
    }
}
