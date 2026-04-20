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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.neodocscanner.feature.vault.presentation.components.GroupNameSheet
import com.example.neodocscanner.feature.vault.presentation.components.GroupPageReorderSheet
import com.example.neodocscanner.feature.vault.presentation.components.MoveToSectionSheet
import com.example.neodocscanner.feature.vault.presentation.tabs.VaultChecklistTab
import com.example.neodocscanner.feature.vault.presentation.tabs.VaultReviewTab
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
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
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope        = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activity     = LocalContext.current as Activity
    val context      = LocalContext.current

    // ── ML Kit Document Scanner ───────────────────────────────────────────────

    val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(SCANNER_MODE_FULL)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setPageLimit(20)
        .setGalleryImportAllowed(true)
        .build()

    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) viewModel.onScanResult(uris)
        }
    }

    fun launchScanner() {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
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

    val contextMenuState = buildContextMenuState(uiState)

    // ── Pager ─────────────────────────────────────────────────────────────────

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTabIndex,
        pageCount   = { VAULT_TABS.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }

    // ── Sheets ────────────────────────────────────────────────────────────────

    val moveSheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groupNameSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageReorderSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Main scaffold ─────────────────────────────────────────────────────────

    Scaffold(
        modifier     = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },

        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (uiState.isSelectionMode) {
                            Text("${uiState.selectedCount} selected", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(
                                text       = uiState.instanceName,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
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
                    actions = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = {
                                viewModel.selectAll(
                                    allDocs = uiState.allDocuments,
                                    inboxDocs = uiState.inboxDocuments,
                                    sectionsWithDocs = uiState.sectionsWithDocs
                                )
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                ScanProgressBar(phase = uiState.scanPhase)

                TabRow(selectedTabIndex = pagerState.currentPage) {
                    VAULT_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                val badge = when (index) {
                                    0 -> if (uiState.activeDocumentCount > 0) "$title (${uiState.activeDocumentCount})" else title
                                    1 -> if (uiState.inboxCount > 0) "$title (${uiState.inboxCount})" else title
                                    else -> title
                                }
                                Text(
                                    text       = badge,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        },

        floatingActionButton = {
            if (uiState.scanPhase is ScanProcessingPhase.Idle && !uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick        = { launchScanner() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan documents")
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
                        if (uiState.canGroupSelected) {
                            FilledTonalButton(
                                onClick  = { viewModel.requestSelectionGroupName(andMove = false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.GroupWork, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Group")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick  = { viewModel.deleteSelected() },
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
                            onConfirmAadhaarPair     = { viewModel.confirmAadhaarPair(it, uiState.allDocuments) },
                            onStartPassportPairing   = viewModel::startPassportPairing,
                            onConfirmPassportPair    = { viewModel.confirmPassportPair(it, uiState.allDocuments) },
                            onStartGenericGrouping   = viewModel::startGenericGrouping,
                            onToggleGenericCandidate = { viewModel.toggleGenericGroupingCandidate(it, uiState.allDocuments) },
                            onRequestGenericGroupName = viewModel::requestGenericGroupName,
                            onCancelGroupingModes    = viewModel::cancelAllGroupingModes,
                            onShowMoveSheet          = viewModel::showMoveSheet,
                            onShowRenameGroupDialog  = viewModel::showRenameGroupDialog,
                            onShowPageReorderSheet   = viewModel::showPageReorderSheet,
                            onExportAsPdf            = viewModel::exportGroupAsPDF,
                            onUngroupDocuments       = viewModel::ungroupDocuments,
                            onDeleteDocument         = viewModel::deleteDocument,
                            onDeleteGroup            = viewModel::deleteGroup,
                            onUnmergePdf             = viewModel::unmergePDF,
                            onSharePdf               = viewModel::sharePDF,
                            onOpenPdfViewer          = viewModel::openPdfViewer
                        )
                        1 -> VaultReviewTab(
                            documents                = uiState.inboxDocuments,
                            contextMenuState         = contextMenuState,
                            onDeleteDocument         = viewModel::deleteDocument,
                            onOpenDocument           = onOpenDocument,
                            onReclassify             = viewModel::reclassifyAndReroute,
                            onEnterSelectionMode     = { viewModel.enterSelectionMode(it, "__inbox__") },
                            onToggleSelection        = viewModel::toggleSelection,
                            onStartAadhaarPairing    = viewModel::startAadhaarPairing,
                            onConfirmAadhaarPair     = { viewModel.confirmAadhaarPair(it, uiState.allDocuments) },
                            onStartPassportPairing   = viewModel::startPassportPairing,
                            onConfirmPassportPair    = { viewModel.confirmPassportPair(it, uiState.allDocuments) },
                            onStartGenericGrouping   = viewModel::startGenericGrouping,
                            onToggleGenericCandidate = { viewModel.toggleGenericGroupingCandidate(it, uiState.allDocuments) },
                            onCancelGroupingModes    = viewModel::cancelAllGroupingModes,
                            onShowMoveSheet          = viewModel::showMoveSheet,
                            onShowRenameGroupDialog  = viewModel::showRenameGroupDialog,
                            onShowPageReorderSheet   = viewModel::showPageReorderSheet,
                            onExportAsPdf            = viewModel::exportGroupAsPDF,
                            onUngroupDocuments       = viewModel::ungroupDocuments,
                            onDeleteGroup            = viewModel::deleteGroup,
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

    // ── Group name sheet ──────────────────────────────────────────────────────

    if (uiState.showGroupNameSheet) {
        GroupNameSheet(
            name         = uiState.pendingGroupNameText,
            onNameChange = viewModel::onGroupNameTextChange,
            isAadhaar    = uiState.pendingGroupIsAadhaar,
            isPassport   = uiState.pendingGroupIsPassport,
            docCount     = if (uiState.pendingGroupIsSelection) uiState.selectedCount else 2,
            sheetState   = groupNameSheetState,
            onConfirm    = { name -> viewModel.finalizeGroup(name) },
            onDismiss    = viewModel::dismissGroupNameSheet
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
        isAadhaarPairingMode    = uiState.isAadhaarPairingMode,
        aadhaarAnchorId         = uiState.aadhaarPairingAnchorId,
        isPassportPairingMode   = uiState.isPassportPairingMode,
        passportAnchorId        = uiState.passportPairingAnchorId,
        isGenericGroupingMode   = uiState.isGenericGroupingMode,
        genericAnchorId         = uiState.genericGroupingAnchorId,
        genericCandidateIds     = uiState.genericGroupingCandidateIds.toSet(),
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
