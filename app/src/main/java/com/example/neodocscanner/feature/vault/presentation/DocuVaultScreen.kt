package com.example.neodocscanner.feature.vault.presentation

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.ScanProcessingPhase
import com.example.neodocscanner.feature.vault.presentation.components.DocumentContextMenuState
import com.example.neodocscanner.feature.vault.presentation.components.GroupPageReorderSheet
import com.example.neodocscanner.feature.vault.presentation.components.MoveToSectionSheet
import com.example.neodocscanner.feature.vault.presentation.components.RoutingConflictReviewSheet
import com.example.neodocscanner.feature.vault.presentation.tabs.VaultChecklistTab
import com.example.neodocscanner.feature.vault.presentation.tabs.VaultReviewTab
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

private val VAULT_TABS = listOf("Categories", "Uncategorised")

/**
 * Document Vault screen — the per-vault container with ML Kit scanner integration.
 * Hosts all Module 7 sheets: MoveToSection, GroupName, GroupPageReorder,
 * selection action bar, rename group dialog.
 *
 * iOS equivalent: DocuVaultView.swift
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocuVaultScreen(
    onNavigateBack: () -> Unit,
    onOpenDocument: (documentId: String) -> Unit = {},
    onOpenPdfViewer: (documentId: String) -> Unit = {},
    viewModel: DocuVaultViewModel = hiltViewModel()
) {
    data class PendingDelete(val id: String, val isGroup: Boolean)
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope        = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activity     = LocalContext.current as Activity
    val context      = LocalContext.current

    // ── ML Kit Document Scanner ───────────────────────────────────────────────

    val scannerOptions = GmsDocumentScannerOptions.Builder()
        // BASE mode avoids the full auto-enhancement/filter flow; keep processing manual.
        .setScannerMode(SCANNER_MODE_BASE)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setPageLimit(20)
        // Lets users pick existing photos from the device inside the ML Kit scanner UI (same flow as camera).
        .setGalleryImportAllowed(true)
        .build()

    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }
    var pendingSectionHint by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) {
                viewModel.onScanResult(
                    imageUris = uris,
                    preferredSectionId = pendingSectionHint
                )
            }
        }
        pendingSectionHint = null
    }

    fun launchScanner(sectionIdHint: String? = null) {
        pendingSectionHint = sectionIdHint
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                pendingSectionHint = null
                Log.e("DocuVaultScreen", "Scanner launch failed", e)
                scope.launch { snackbarHost.showSnackbar("Scanner unavailable: ${e.localizedMessage}") }
            }
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // ── Share PDF side-effect ──────────────────────────────────────────────────

    LaunchedEffect(uiState.sharePdfDocId) {
        val docId = uiState.sharePdfDocId ?: return@LaunchedEffect
        val doc = uiState.allDocuments.firstOrNull { it.id == docId } ?: return@LaunchedEffect
        if (doc.relativePath.isBlank()) { viewModel.clearSharePdf(); return@LaunchedEffect }
        try {
            val file = context.filesDir.resolve("NeoDocs/${doc.relativePath}")
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share PDF"))
            }
        } catch (e: Exception) {
            Log.e("DocuVaultScreen", "Share PDF failed", e)
        }
        viewModel.clearSharePdf()
    }

    // ── Open PDF viewer side-effect ────────────────────────────────────────────

    LaunchedEffect(uiState.showPdfViewerDocId) {
        uiState.showPdfViewerDocId?.let { docId ->
            onOpenPdfViewer(docId)
            viewModel.closePdfViewer()
        }
    }

    // ── Group & Move side-effect ───────────────────────────────────────────────

    LaunchedEffect(uiState.groupAndMoveTargetDocId) {
        uiState.groupAndMoveTargetDocId?.let { docId ->
            viewModel.showMoveSheet(docId)
        }
    }

    // ── Build context menu state ───────────────────────────────────────────────

    val contextMenuState by remember(
        uiState.allDocuments,
        uiState.isSelectionMode,
        uiState.selectedDocumentIds,
        uiState.selectionSectionId,
        uiState.isAadhaarPairingMode,
        uiState.aadhaarPairingOrderedIds,
        uiState.isPassportPairingMode,
        uiState.passportPairingOrderedIds,
        uiState.isGenericGroupingMode,
        uiState.genericGroupingOrderedIds
    ) {
        derivedStateOf { buildContextMenuState(uiState) }
    }

    // ── Pager ─────────────────────────────────────────────────────────────────

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTabIndex,
        pageCount   = { VAULT_TABS.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }

    // ── Sheets ────────────────────────────────────────────────────────────────

    val moveSheetState           = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageReorderSheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val routingConflictSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var vaultOverflowMenuExpanded by remember { mutableStateOf(false) }

    // ── Main scaffold ─────────────────────────────────────────────────────────

    Scaffold(
        modifier     = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        snackbarHost = { SnackbarHost(snackbarHost) },

        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    title = {
                        if (uiState.isSelectionMode) {
                            val categoriesTab = pagerState.currentPage == 0
                            val selectAllTargetIds = remember(
                                categoriesTab,
                                uiState.sectionsWithDocs,
                                uiState.inboxDocuments
                            ) {
                                if (categoriesTab) {
                                    buildSet {
                                        uiState.sectionsWithDocs.forEach { swd ->
                                            swd.documents.forEach { add(it.id) }
                                        }
                                        uiState.inboxDocuments.forEach { add(it.id) }
                                    }
                                } else {
                                    uiState.inboxDocuments.map { it.id }.toSet()
                                }
                            }
                            val allVaultTabTargetsSelected = remember(
                                selectAllTargetIds,
                                uiState.selectedDocumentIds
                            ) {
                                selectAllTargetIds.isNotEmpty() &&
                                    selectAllTargetIds == uiState.selectedDocumentIds.toSet()
                            }
                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                verticalAlignment   = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text       = "${uiState.selectedCount} selected",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.toggleSelectAllInCurrentVaultTab(
                                            categoriesTab = categoriesTab,
                                            sectionsWithDocs = uiState.sectionsWithDocs,
                                            inboxDocs = uiState.inboxDocuments
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (allVaultTabTargetsSelected) {
                                            Icons.Default.ClearAll
                                        } else {
                                            Icons.Default.SelectAll
                                        },
                                        contentDescription = if (allVaultTabTargetsSelected) {
                                            "Deselect all"
                                        } else {
                                            "Select all"
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (allVaultTabTargetsSelected) {
                                            "Deselect all"
                                        } else {
                                            "Select all"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text       = uiState.instanceName,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    actions = {
                        if (!uiState.isSelectionMode) {
                            Box {
                                IconButton(
                                    onClick = { vaultOverflowMenuExpanded = true }
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = vaultOverflowMenuExpanded,
                                    onDismissRequest = { vaultOverflowMenuExpanded = false }
                                ) {
                                    listOf(
                                        2 to "2 per row",
                                        3 to "3 per row"
                                    ).forEach { (cols, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (uiState.galleryGridColumns == cols) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                vaultOverflowMenuExpanded = false
                                                viewModel.setGalleryGridColumns(cols)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit selection")
                            }
                        } else {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                ScanProgressBar(phase = uiState.scanPhase)
            }
        },

        floatingActionButton = {
            if (uiState.scanPhase is ScanProcessingPhase.Idle && !uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick            = { launchScanner(null) },
                    containerColor     = MaterialTheme.colorScheme.primary,
                    contentColor       = MaterialTheme.colorScheme.onPrimary,
                    shape              = CircleShape,
                    elevation          = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan documents",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },

        bottomBar = {
            // ── Selection action bar ──────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedCount > 0,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.canShowSelectionGroupActions) {
                            FilledTonalButton(
                                onClick  = { viewModel.requestSelectionGroupName(andMove = false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.GroupWork, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Group")
                            }
                            if (uiState.canGroupAndMoveSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick  = { viewModel.requestSelectionGroupName(andMove = true) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.GroupWork, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Group & Move")
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick  = { showDeleteSelectedConfirm = true },
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete (${uiState.selectedCount})")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.isSelectionMode && uiState.isGenericGroupingMode,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.genericGroupingOrderedIds.size <= 1) {
                                "Tap documents to add in group"
                            } else {
                                "${uiState.genericGroupingOrderedIds.size} selected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::cancelAllGroupingModes) {
                            Text("Cancel")
                        }
                        if (uiState.canConfirmGenericGroup) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(onClick = viewModel::requestGenericGroupName) {
                                Icon(Icons.Default.GroupWork, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Group")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.isSelectionMode && uiState.isAadhaarPairingMode,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.canConfirmAadhaarPair) "2 selected" else "Tap another Aadhaar card",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::cancelAllGroupingModes) { Text("Cancel") }
                        if (uiState.canConfirmAadhaarPair) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(onClick = { viewModel.confirmAadhaarPairSelection(uiState.allDocuments) }) {
                                Icon(Icons.Default.GroupWork, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pair")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.isSelectionMode && uiState.isPassportPairingMode,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.canConfirmPassportPair) "2 selected" else "Tap another Passport page",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::cancelAllGroupingModes) { Text("Cancel") }
                        if (uiState.canConfirmPassportPair) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(onClick = { viewModel.confirmPassportPairSelection(uiState.allDocuments) }) {
                                Icon(Icons.Default.GroupWork, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pair")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.isSelectionMode &&
                    !uiState.isGenericGroupingMode &&
                    !uiState.isAadhaarPairingMode &&
                    !uiState.isPassportPairingMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar {
                    VAULT_TABS.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        val badge = when (index) {
                            0 -> if (uiState.categorizedDocumentCount > 0) uiState.categorizedDocumentCount.toString() else null
                            1 -> if (uiState.inboxCount > 0) uiState.inboxCount.toString() else null
                            else -> null
                        }
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick  = { scope.launch { pagerState.scrollToPage(index) } },
                            icon = {
                                Icon(
                                    imageVector = if (index == 0) Icons.Default.Folder else Icons.Default.MoveToInbox,
                                    contentDescription = title
                                )
                            },
                            label = {
                                Text(
                                    text = badge?.let { "$title ($it)" } ?: title,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state    = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> VaultChecklistTab(
                            uiState                  = uiState,
                            contextMenuState         = contextMenuState,
                            onToggleCollapse         = viewModel::toggleSection,
                            onOpenDocument           = onOpenDocument,
                            onReclassify             = viewModel::reclassifyAndReroute,
                            onEnterSelectionMode     = { viewModel.enterSelectionMode(it) },
                            onToggleSelection        = viewModel::toggleSelection,
                            onStartAadhaarPairing    = viewModel::startAadhaarPairing,
                            onConfirmAadhaarPair     = { viewModel.toggleAadhaarPairingSelection(it, uiState.allDocuments) },
                            onStartPassportPairing   = viewModel::startPassportPairing,
                            onConfirmPassportPair    = { viewModel.togglePassportPairingSelection(it, uiState.allDocuments) },
                            onStartGenericGrouping   = viewModel::startGenericGrouping,
                            onToggleGenericCandidate = { viewModel.toggleGenericGroupingSelection(it, uiState.allDocuments) },
                            onRequestGenericGroupName = viewModel::requestGenericGroupName,
                            onCancelGroupingModes    = viewModel::cancelAllGroupingModes,
                            onShowMoveSheet          = viewModel::showMoveSheet,
                            onShowRenameGroupDialog  = viewModel::showRenameGroupDialog,
                            onShowPageReorderSheet   = viewModel::showPageReorderSheet,
                            onExportAsPdf            = viewModel::exportGroupAsPDF,
                            onUngroupDocuments       = viewModel::ungroupDocuments,
                            onDeleteDocument         = { id -> pendingDelete = PendingDelete(id, isGroup = false) },
                            onDeleteGroup            = { id -> pendingDelete = PendingDelete(id, isGroup = true) },
                            onUnmergePdf             = viewModel::unmergePDF,
                            onSharePdf               = viewModel::sharePDF,
                            onOpenPdfViewer          = viewModel::openPdfViewer,
                            onScanToSection          = { sectionId -> launchScanner(sectionId) },
                            onLongPressSection       = viewModel::enterSectionSelectionAll,
                            onToggleSectionSelection = viewModel::toggleSectionSelection
                        )
                        1 -> VaultReviewTab(
                            documents                = uiState.inboxDocuments,
                            gridColumns              = uiState.galleryGridColumns,
                            allDocumentsInScope      = uiState.allDocuments.filter { it.sectionId == null },
                            contextMenuState         = contextMenuState,
                            onOpenDocument           = onOpenDocument,
                            onReclassify             = viewModel::reclassifyAndReroute,
                            onEnterSelectionMode     = { viewModel.enterSelectionMode(it, "__inbox__") },
                            onToggleSelection        = viewModel::toggleSelection,
                            onStartAadhaarPairing    = viewModel::startAadhaarPairing,
                            onConfirmAadhaarPair     = { viewModel.toggleAadhaarPairingSelection(it, uiState.allDocuments) },
                            onStartPassportPairing   = viewModel::startPassportPairing,
                            onConfirmPassportPair    = { viewModel.togglePassportPairingSelection(it, uiState.allDocuments) },
                            onStartGenericGrouping   = viewModel::startGenericGrouping,
                            onToggleGenericCandidate = { viewModel.toggleGenericGroupingSelection(it, uiState.allDocuments) },
                            onCancelGroupingModes    = viewModel::cancelAllGroupingModes,
                            onShowMoveSheet          = viewModel::showMoveSheet,
                            onShowRenameGroupDialog  = viewModel::showRenameGroupDialog,
                            onShowPageReorderSheet   = viewModel::showPageReorderSheet,
                            onExportAsPdf            = viewModel::exportGroupAsPDF,
                            onUngroupDocuments       = viewModel::ungroupDocuments,
                            onDeleteDocument         = { id -> pendingDelete = PendingDelete(id, isGroup = false) },
                            onDeleteGroup            = { id -> pendingDelete = PendingDelete(id, isGroup = true) },
                            onUnmergePdf             = viewModel::unmergePDF,
                            onSharePdf               = viewModel::sharePDF,
                            onOpenPdfViewer          = viewModel::openPdfViewer
                        )
                    }
                }
            }
        }
    }

    // ── Move to section sheet ─────────────────────────────────────────────────

    if (uiState.showMoveSheet) {
        val moveDoc = uiState.moveTargetDocId?.let { id -> uiState.allDocuments.firstOrNull { it.id == id } }
        MoveToSectionSheet(
            document         = moveDoc,
            sectionsWithDocs = uiState.sectionsWithDocs,
            sheetState       = moveSheetState,
            onMove           = { sectionId ->
                uiState.moveTargetDocId?.let { viewModel.routeToSection(it, sectionId) }
                viewModel.dismissGroupAndMoveSheet()
            },
            onDismiss = viewModel::dismissMoveSheet
        )
    }

    // ── Group name modal ──────────────────────────────────────────────────────

    if (uiState.showGroupNameSheet) {
        val docCount = when {
            uiState.pendingGroupIsSelection -> uiState.selectedCount
            uiState.pendingGroupIsAadhaar || uiState.pendingGroupIsPassport -> 2
            else -> uiState.genericGroupingOrderedIds.size
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissGroupNameSheet,
            title = { Text("Name group") },
            text = {
                OutlinedTextField(
                    value = uiState.pendingGroupNameText,
                    onValueChange = viewModel::onGroupNameTextChange,
                    label = { Text("Group name") },
                    supportingText = { Text("$docCount document(s)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.finalizeGroup(uiState.pendingGroupNameText) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissGroupNameSheet) { Text("Cancel") }
            }
        )
    }

    // ── Group page reorder sheet ──────────────────────────────────────────────

    if (uiState.showPageReorderSheet) {
        val groupId = uiState.pageReorderTargetGroupId
        val groupDocs = if (groupId != null) {
            uiState.allDocuments
                .filter { it.groupId == groupId }
                .sortedWith(compareBy { it.groupPageIndex ?: (it.pageIndex ?: Int.MAX_VALUE) })
        } else emptyList()

        if (groupDocs.isNotEmpty()) {
            GroupPageReorderSheet(
                initialOrder = groupDocs,
                sheetState   = pageReorderSheetState,
                onDone       = { orderedIds -> viewModel.applyGroupPageReorder(orderedIds) },
                onDismiss    = viewModel::dismissPageReorderSheet
            )
        }
    }

    // ── Rename group dialog ───────────────────────────────────────────────────

    if (uiState.showRenameGroupDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameGroupDialog,
            title   = { Text("Rename Group") },
            text    = {
                OutlinedTextField(
                    value         = uiState.renameGroupText,
                    onValueChange = viewModel::onRenameGroupTextChange,
                    label         = { Text("Group name") },
                    singleLine    = true
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRenameGroup) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameGroupDialog) { Text("Cancel") }
            }
        )
    }

    // ── Routing conflict review (hint vs ML, one or many documents) ───────────
    if (uiState.pendingRoutingConflicts.isNotEmpty()) {
        RoutingConflictReviewSheet(
            conflicts = uiState.pendingRoutingConflicts,
            documentById = uiState.allDocuments.associateBy { it.id },
            sheetState = routingConflictSheetState,
            onDismiss = viewModel::dismissRoutingConflictsKeepAllHinted,
            onResolveOne = viewModel::resolveRoutingConflict,
            onKeepAllHinted = viewModel::dismissRoutingConflictsKeepAllHinted,
            onMoveAllToDetected = viewModel::applyAllRoutingConflictsUseDetected
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (pending.isGroup) "Delete group?" else "Delete file?") },
            text = { Text(if (pending.isGroup) "This will delete all files in the group." else "This file will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (pending.isGroup) viewModel.deleteGroup(pending.id) else viewModel.deleteDocument(pending.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Delete ${uiState.selectedCount} file(s)?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectedConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Context menu state builder ─────────────────────────────────────────────────

private fun buildContextMenuState(uiState: VaultUiState): DocumentContextMenuState {
    // Build group member count map for all docs
    val groupMemberCounts = uiState.allDocuments
        .filter { it.groupId != null }
        .groupBy { it.groupId!! }
        .mapValues { it.value.size }
    val docGroupCounts = uiState.allDocuments
        .filter { it.groupId != null }
        .associate { it.id to (groupMemberCounts[it.groupId!!] ?: 1) }

    return DocumentContextMenuState(
        isSelectionMode         = uiState.isSelectionMode,
        selectedIds             = uiState.selectedDocumentIds.toSet(),
        selectionOrder          = uiState.selectedDocumentIds,
        selectionScopeSectionId = uiState.selectionSectionId,
        isAadhaarPairingMode    = uiState.isAadhaarPairingMode,
        aadhaarPairingOrder     = uiState.aadhaarPairingOrderedIds,
        isPassportPairingMode   = uiState.isPassportPairingMode,
        passportPairingOrder    = uiState.passportPairingOrderedIds,
        isGenericGroupingMode   = uiState.isGenericGroupingMode,
        genericGroupingOrder    = uiState.genericGroupingOrderedIds,
        groupMemberCounts       = docGroupCounts
    )
}

// ── Scan progress indicator ────────────────────────────────────────────────────

@Composable
private fun ScanProgressBar(phase: ScanProcessingPhase) {
    AnimatedVisibility(
        visible = phase !is ScanProcessingPhase.Idle,
        enter   = fadeIn(),
        exit    = fadeOut()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val (progress, label) = when (phase) {
                is ScanProcessingPhase.Saving    -> phase.progress to phase.message
                is ScanProcessingPhase.Analysing -> phase.progress to phase.message
                is ScanProcessingPhase.Routing   -> null to ScanProcessingPhase.Routing.message
                is ScanProcessingPhase.Done      -> 1f to phase.message
                else                             -> null to ""
            }

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color    = MaterialTheme.colorScheme.primary
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (label.isNotBlank()) {
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}
