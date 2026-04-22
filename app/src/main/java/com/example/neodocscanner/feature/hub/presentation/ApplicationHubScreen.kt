package com.example.neodocscanner.feature.hub.presentation

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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationHubScreen(
    onNavigateToVault: (instanceId: String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToApplicationLink: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ApplicationHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUser by authViewModel.loggedInUsername.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val sortedInstances = remember(uiState.instances) {
        uiState.instances.sortedWith(
            compareBy<ApplicationInstanceUi> { it.instance.isArchived }
                .thenBy { it.instance.customName.lowercase() }
        )
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Applications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ProfileAvatarButton(
                        username = loggedInUser,
                        onClick = onNavigateToProfile
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::showCreateSheet,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 10.dp
                ),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                text = {
                    Text(
                        text = "New",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                sortedInstances.isEmpty() -> {
                    EmptyHubState(
                        onCreateApplication = viewModel::showCreateSheet,
                        onLinkFromQr = onNavigateToApplicationLink,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 104.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = sortedInstances,
                            key = { it.instance.id }
                        ) { instanceUi ->
                            ApplicationInstanceCard(
                                instanceUi = instanceUi,
                                onTap = { onNavigateToVault(instanceUi.instance.id) },
                                onRename = viewModel::startRename,
                                onArchive = viewModel::archiveInstance,
                                onDelete = viewModel::startDelete
                            )
                        }
                    }
                }
            }

            if (uiState.isOperationRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (uiState.showCreateSheet) {
        CreateVaultBottomSheet(
            onDismiss = viewModel::dismissCreateSheet,
            onCreate = { template, name ->
                viewModel.createFromTemplate(template, name)
            },
            onScanQR = {
                viewModel.dismissCreateSheet()
                onNavigateToApplicationLink()
            }
        )
    }

    uiState.renameTarget?.let { instance ->
        RenameInstanceDialog(
            instance = instance,
            currentInput = uiState.renameInput,
            onInputChange = viewModel::onRenameInputChange,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename
        )
    }

    uiState.deleteTarget?.let { instance ->
        DeleteInstanceDialog(
            instance = instance,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

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
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = initials,
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyHubState(
    onCreateApplication: () -> Unit,
    onLinkFromQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No applications yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a new application or link one from QR.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onCreateApplication) {
            Text("New application")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onLinkFromQr) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Link from QR")
        }
    }
}
