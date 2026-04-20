package com.example.neodocscanner.feature.hub.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.neodocscanner.feature.auth.presentation.AuthViewModel
import com.example.neodocscanner.feature.hub.presentation.components.ApplicationInstanceCard
import com.example.neodocscanner.feature.hub.presentation.components.CreateVaultBottomSheet
import com.example.neodocscanner.feature.hub.presentation.components.DeleteInstanceDialog
import com.example.neodocscanner.feature.hub.presentation.components.RenameInstanceDialog

/**
 * Application Hub — the top-level vault list.
 *
 * iOS equivalent: ApplicationHubView.swift.
 *
 * UX adaptations:
 * - LargeTopAppBar (M3) with collapse-on-scroll instead of iOS navigation title
 * - FAB for create instead of toolbar "+" button
 * - Empty state with icon + CTA button (iOS had a similar empty state)
 * - Snackbar feedback instead of iOS toast / banner
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationHubScreen(
    onNavigateToVault: (instanceId: String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ApplicationHubViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUser  by authViewModel.loggedInUsername.collectAsStateWithLifecycle()
    val snackbarHost  = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Show snackbar messages (rename / delete / create confirmations)
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier       = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar         = {
            LargeTopAppBar(
                title          = {
                    Column {
                        Text(
                            text       = "Applications",
                            fontWeight = FontWeight.Bold
                        )
                        if (loggedInUser.isNotBlank()) {
                            Text(
                                text  = loggedInUser,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    // iOS: Circle with person.crop.circle icon in leading toolbar
                    ProfileAvatarButton(
                        username = loggedInUser,
                        onClick  = onNavigateToProfile
                    )
                },
                actions        = {
                    // iOS: gearshape button in trailing toolbar
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector        = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick            = viewModel::showCreateSheet,
                containerColor     = MaterialTheme.colorScheme.primary,
                contentColor       = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Create new vault"
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // ── Loading ────────────────────────────────────────────────────
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // ── Empty state ────────────────────────────────────────────────
                uiState.instances.isEmpty() -> {
                    EmptyHubState(
                        onCreateVault = viewModel::showCreateSheet,
                        modifier      = Modifier.align(Alignment.Center)
                    )
                }

                // ── Vault list ─────────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        contentPadding    = PaddingValues(
                            start  = 16.dp,
                            end    = 16.dp,
                            top    = 8.dp,
                            bottom = 96.dp   // space above FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier            = Modifier.fillMaxSize()
                    ) {
                        // Active vaults first, archived at the bottom
                        val sorted = uiState.instances.sortedWith(
                            compareBy { it.instance.isArchived }
                        )

                        items(
                            items = sorted,
                            key   = { it.instance.id }
                        ) { instanceUi ->
                            ApplicationInstanceCard(
                                instanceUi = instanceUi,
                                onTap      = {
                                    onNavigateToVault(instanceUi.instance.id)
                                },
                                onRename   = viewModel::startRename,
                                onArchive  = viewModel::archiveInstance,
                                onDelete   = viewModel::startDelete
                            )
                        }
                    }
                }
            }

            // ── Operation progress overlay ─────────────────────────────────────
            AnimatedVisibility(
                visible  = uiState.isOperationRunning,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // ── Create vault sheet ────────────────────────────────────────────────────
    if (uiState.showCreateSheet) {
        CreateVaultBottomSheet(
            onDismiss = viewModel::dismissCreateSheet,
            onCreate  = { template, name ->
                viewModel.createFromTemplate(template, name)
            },
            onScanQR  = {
                // QR scanner is wired in Module 5
                viewModel.dismissCreateSheet()
            }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    uiState.renameTarget?.let { instance ->
        RenameInstanceDialog(
            instance      = instance,
            currentInput  = uiState.renameInput,
            onInputChange = viewModel::onRenameInputChange,
            onConfirm     = viewModel::confirmRename,
            onDismiss     = viewModel::dismissRename
        )
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    uiState.deleteTarget?.let { instance ->
        DeleteInstanceDialog(
            instance  = instance,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

// ── Profile avatar button ─────────────────────────────────────────────────────

/**
 * Circular avatar button shown in the leading position of the Hub TopAppBar.
 *
 * iOS equivalent: Circle().fill(Color("PrimaryOrange").opacity(0.1)).overlay(
 *     Image(systemName: "person.crop.circle")…) in ApplicationHubView.swift.
 *
 * Shows initials if the user's display name has at least 2 words, otherwise shows
 * the person icon — mirrors iOS initials derivation in ProfileView.swift.
 */
@Composable
private fun ProfileAvatarButton(
    username: String,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val initials = username.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    IconButton(onClick = onClick) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isEmpty()) {
                Icon(
                    imageVector        = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint               = primaryColor,
                    modifier           = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text       = initials,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = primaryColor
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyHubState(
    onCreateVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text       = "No Applications Yet",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text      = "Tap + to create a new application and start scanning documents.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}
