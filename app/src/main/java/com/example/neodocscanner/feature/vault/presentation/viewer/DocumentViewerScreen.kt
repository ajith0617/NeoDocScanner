package com.example.neodocscanner.feature.vault.presentation.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.feature.vault.presentation.detail.DocumentDetailSheet
import androidx.compose.ui.graphics.drawscope.Stroke
import android.graphics.Bitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen document image viewer with:
 *  - Pinch-to-zoom (1x–4x) + double-tap reset
 *  - Multi-page horizontal swipe for grouped docs
 *  - OCR highlight overlay (toggle)
 *  - Tap on highlighted region → tooltip
 *  - Top bar: back, doc name, remove-from-group, move-to-category, info
 *  - Bottom page indicator pill for multi-page groups
 *  - Info button → DocumentDetailSheet (ModalBottomSheet)
 *
 * iOS equivalent: DocumentFullscreenView.swift (.fullScreenCover presentation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val removeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val document = state.document ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // ── Main content area ─────────────────────────────────────────────
            if (state.isGrouped) {
                GroupedPageViewer(state = state, viewModel = viewModel)
            } else {
                SinglePageViewer(
                    document       = document,
                    bitmap         = state.imageBitmaps[document.id],
                    showHighlights = state.showHighlights,
                    isOcrRunning   = state.isOcrRunning,
                    viewModel      = viewModel
                )
            }

            // ── Top bar ───────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                ViewerTopBar(
                    state          = state,
                    onNavigateBack = onNavigateBack,
                    onShowInfo     = { viewModel.showDetailSheet() },
                    onShowRemoveDialog = { viewModel.showRemoveFromGroupDialog() },
                    onShowMoveSheet    = { viewModel.showMoveSheet() }
                )
            }

            // ── Bottom group reorder strip (grouped docs only) ─────────────────
            if (state.isGrouped) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 12.dp)
                ) {
                    GroupReorderThumbnailStrip(
                        pages = state.allPages,
                        activeDocumentId = state.activeDocument?.id,
                        thumbnails = state.imageBitmaps,
                        onSelectPage = viewModel::goToPageByDocumentId,
                        onReorder = { orderedIds -> viewModel.previewGroupReorder(orderedIds) }
                    )
                }
            }

            if (state.isGrouped && state.hasPendingReorder) {
                Button(
                    onClick = viewModel::saveGroupReorder,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 116.dp)
                ) {
                    Text("Save")
                }
            }

            // ── Bottom-right OCR button (temporarily disabled) ───────────────
            // Box(
            //     modifier = Modifier
            //         .align(Alignment.BottomEnd)
            //         .padding(end = 20.dp, bottom = 40.dp)
            // ) {
            //     OcrActionButton(state = state, viewModel = viewModel)
            // }
        }
    }

    // ── Remove-from-group confirmation modal ─────────────────────────────────
    if (state.showRemoveFromGroupDialog) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissRemoveFromGroupDialog,
            sheetState = removeSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Remove page from group?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "This removes only this page from the current group. The file itself will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.removeFromGroup(sendToUncategorised = true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove & move to Uncategorised")
                }

                OutlinedButton(
                    onClick = { viewModel.removeFromGroup(sendToUncategorised = false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove & keep in current category")
                }

                TextButton(
                    onClick = viewModel::dismissRemoveFromGroupDialog,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // ── Detail sheet ──────────────────────────────────────────────────────────
    if (state.showDetailSheet) {
        val activeDoc = state.activeDocument ?: document
        DocumentDetailSheet(
            document   = activeDoc,
            onDismiss  = {
                viewModel.dismissDetailSheet()
                viewModel.refreshActiveDocument()
            },
            onDeleted  = {
                viewModel.dismissDetailSheet()
                onNavigateBack()
            },
            sheetState = detailSheetState
        )
    }
}

// ── Single-page viewer ────────────────────────────────────────────────────────

@Composable
private fun SinglePageViewer(
    document: Document,
    bitmap: Bitmap?,
    showHighlights: Boolean,
    isOcrRunning: Boolean,
    viewModel: DocumentViewerViewModel
) {
    var tappedRegion by remember { mutableStateOf<TextRegion?>(null) }
    val regions = document.decodedRegions

    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            ZoomableImage(
                bitmap         = bitmap,
                regions        = if (showHighlights) regions else emptyList(),
                onRegionTapped = { region ->
                    tappedRegion = if (tappedRegion?.text == region?.text) null else region
                }
            )
        } else {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Region tooltip
        RegionTooltip(
            region    = tappedRegion,
            isGrouped = false,
            onDismiss = { tappedRegion = null }
        )
    }
}

// ── Multi-page viewer ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupedPageViewer(
    state: ViewerUiState,
    viewModel: DocumentViewerViewModel
) {
    var tappedRegion by remember { mutableStateOf<TextRegion?>(null) }
    val allPages = state.allPages
    val latestPageIndex by rememberUpdatedState(state.currentPageIndex)
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex,
        pageCount   = { allPages.size }
    )

    // Sync pager ↔ ViewModel
    LaunchedEffect(state.currentPageIndex) {
        if (pagerState.currentPage != state.currentPageIndex) {
            pagerState.scrollToPage(state.currentPageIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != latestPageIndex) {
                viewModel.goToPage(page)
                tappedRegion = null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { pageIdx ->
            val doc    = allPages.getOrNull(pageIdx)
            val bitmap = doc?.let { state.imageBitmaps[it.id] }
            val regions = if (state.showHighlights && pageIdx == pagerState.currentPage)
                doc?.decodedRegions ?: emptyList() else emptyList()

            if (bitmap != null) {
                ZoomableImage(
                    bitmap         = bitmap,
                    regions        = regions,
                    onSwipePrevious = {
                        if (pageIdx > 0) viewModel.goToPage(pageIdx - 1)
                    },
                    onSwipeNext = {
                        if (pageIdx < allPages.lastIndex) viewModel.goToPage(pageIdx + 1)
                    },
                    onRegionTapped = { region ->
                        tappedRegion = if (tappedRegion?.text == region?.text) null else region
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Region tooltip
        RegionTooltip(
            region    = tappedRegion,
            isGrouped = true,
            onDismiss = { tappedRegion = null }
        )
    }
}

// ── Zoomable image with OCR highlight overlay ─────────────────────────────────

@Composable
private fun ZoomableImage(
    bitmap: Bitmap,
    regions: List<TextRegion>,
    onSwipePrevious: (() -> Unit)? = null,
    onSwipeNext: (() -> Unit)? = null,
    onRegionTapped: (TextRegion?) -> Unit
) {
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        // Avoid pan-vs-swipe conflict at base zoom; allow pan only when zoomed in.
        if (newScale > 1.02f || scale > 1.02f) {
            offset += panChange
        }
        scale = newScale
        if (scale <= 1.02f) offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                    onTap = { tapOffset ->
                        if (regions.isEmpty()) { onRegionTapped(null); return@detectTapGestures }
                        // Hit-test against normalised regions (0–1) flipped per Vision coords
                        val vw = size.width.toFloat()
                        val vh = size.height.toFloat()
                        val imgAspect  = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val viewAspect = vw / vh
                        val (dispW, dispH, dispX, dispY) = if (imgAspect > viewAspect) {
                            val h = vw / imgAspect
                            val y = (vh - h) / 2f
                            listOf(vw, h, 0f, y)
                        } else {
                            val w = vh * imgAspect
                            val x = (vw - w) / 2f
                            listOf(w, vh, x, 0f)
                        }

                        for (region in regions) {
                            val rx = dispX + region.x * dispW
                            // Vision Y-origin is bottom-left; flip to top-left
                            val ry = dispY + (1f - region.y - region.h) * dispH
                            val rw = region.w * dispW
                            val rh = region.h * dispH
                            if (tapOffset.x in rx..(rx + rw) && tapOffset.y in ry..(ry + rh)) {
                                onRegionTapped(region)
                                return@detectTapGestures
                            }
                        }
                        onRegionTapped(null)
                    }
                )
            }
            .pointerInput(onSwipePrevious, onSwipeNext, scale) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (!down.pressed) continue

                        var totalX = 0f
                        var totalY = 0f
                        var pointerActive = true

                        while (pointerActive) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            if (changes.isEmpty()) break

                            val activeChange = changes.first()
                            if (!activeChange.pressed) {
                                pointerActive = false
                                break
                            }

                            val delta = activeChange.position - activeChange.previousPosition
                            totalX += delta.x
                            totalY += delta.y
                        }

                        if (scale <= 1.02f && abs(totalX) > 80f && abs(totalX) > abs(totalY) * 1.4f) {
                            if (totalX > 0f) onSwipePrevious?.invoke() else onSwipeNext?.invoke()
                        }
                    }
                }
            }
    ) {
        // Image layer
        Image(
            bitmap           = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale     = ContentScale.Fit,
            modifier         = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX        = scale,
                    scaleY        = scale,
                    translationX  = offset.x,
                    translationY  = offset.y
                )
        )

        // OCR highlight overlay (Canvas drawing)
        if (regions.isNotEmpty()) {
            OcrHighlightOverlay(
                bitmap  = bitmap,
                regions = regions,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}

// ── OCR highlight overlay ─────────────────────────────────────────────────────

@Composable
private fun OcrHighlightOverlay(
    bitmap: Bitmap,
    regions: List<TextRegion>,
    modifier: Modifier = Modifier
) {
    val fillColor   = Color(1f, 0.55f, 0f, 0.25f)
    val strokeColor = Color(1f, 0.55f, 0f, 0.85f)
    val dimColor    = Color.Black.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val vw = size.width
        val vh = size.height
        val imgAspect  = bitmap.width.toFloat() / bitmap.height.toFloat()
        val viewAspect = vw / vh

        val (dispW, dispH, dispX, dispY) = if (imgAspect > viewAspect) {
            val h = vw / imgAspect
            val y = (vh - h) / 2f
            listOf(vw, h, 0f, y)
        } else {
            val w = vh * imgAspect
            val x = (vw - w) / 2f
            listOf(w, vh, x, 0f)
        }

        // Dim whole image
        drawRect(color = dimColor)

        for (region in regions) {
            val rx = dispX + region.x * dispW
            val ry = dispY + (1f - region.y - region.h) * dispH
            val rw = region.w * dispW
            val rh = region.h * dispH

            drawRect(
                color    = fillColor,
                topLeft  = Offset(rx, ry),
                size     = androidx.compose.ui.geometry.Size(rw, rh)
            )
            drawRect(
                color    = strokeColor,
                topLeft  = Offset(rx, ry),
                size     = androidx.compose.ui.geometry.Size(rw, rh),
                style    = Stroke(width = 1.5f)
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun ViewerTopBar(
    state: ViewerUiState,
    onNavigateBack: () -> Unit,
    onShowInfo: () -> Unit,
    onShowRemoveDialog: () -> Unit,
    onShowMoveSheet: () -> Unit
) {
    val document  = state.document ?: return
    val activeDoc = state.activeDocument ?: document
    val allPages  = state.allPages

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back
        IconButton(onClick = onNavigateBack) {
            Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = Color.White
            )
        }

        Spacer(Modifier.weight(1f))

        // Centre: doc name + subtitle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(3f)
        ) {
            val titleText = if (state.isGrouped)
                document.groupName ?: activeDoc.displayName
            else document.displayName

            Text(
                text      = titleText,
                style     = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color     = Color.White,
                maxLines  = 1,
                overflow  = TextOverflow.MiddleEllipsis
            )

            val subtitle = when {
                state.isGrouped && activeDoc.aadhaarSide != null ->
                    "Aadhaar · ${if (activeDoc.aadhaarSide == "front") "Front" else "Back"}"
                state.isGrouped ->
                    "${state.currentPageIndex + 1} of ${allPages.size}"
                document.decodedRegions.isNotEmpty() ->
                    "${document.decodedRegions.size} text regions"
                else -> null
            }
            if (subtitle != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Remove from group (grouped docs only)
        if (state.isGrouped) {
            IconButton(onClick = onShowRemoveDialog) {
                Icon(
                    imageVector        = Icons.Default.LinkOff,
                    contentDescription = "Remove from group",
                    tint               = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // Move to category (uncategorised docs only)
        if (state.isUncategorised) {
            IconButton(onClick = onShowMoveSheet) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Outlined.DriveFileMove,
                    contentDescription = "Move to category",
                    tint               = Color.White
                )
            }
        }

        // Info button
        IconButton(onClick = onShowInfo) {
            Icon(
                imageVector        = Icons.Default.Info,
                contentDescription = "Document info",
                tint               = Color.White
            )
        }
    }
}

// ── Group thumbnail reorder strip ─────────────────────────────────────────────

@Composable
private fun GroupReorderThumbnailStrip(
    pages: List<Document>,
    activeDocumentId: String?,
    thumbnails: Map<String, Bitmap>,
    onSelectPage: (String) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val orderedDocs = remember(pages) { mutableStateListOf<Document>().apply { addAll(pages) } }
    val previewDocs = remember { mutableStateListOf<Document>() }
    var draggingDocId by remember { mutableStateOf<String?>(null) }
    var draggingPreviewIndex by remember { mutableStateOf(-1) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val itemWidthPx = with(LocalDensity.current) { 82.dp.toPx() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Keep local row order aligned when ViewModel state updates externally.
    LaunchedEffect(pages) {
        orderedDocs.clear()
        orderedDocs.addAll(pages)
        if (draggingDocId == null) {
            previewDocs.clear()
            previewDocs.addAll(pages)
        }
    }

    val displayedDocs = if (draggingDocId != null) previewDocs else orderedDocs

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(displayedDocs, key = { _, doc -> doc.id }) { index, doc ->
                val isActive = activeDocumentId == doc.id
                val isDragging = doc.id == draggingDocId
                val scale = if (isDragging) 1.08f else 1f
                val thumbAlpha = if (isDragging) 0.94f else 1f

                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 92.dp)
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffsetX else 0f
                            scaleX = scale
                            scaleY = scale
                            alpha = thumbAlpha
                            shadowElevation = if (isDragging) 14f else 0f
                        }
                        .pointerInput(doc.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingDocId = doc.id
                                    draggingPreviewIndex = index
                                    dragOffsetX = 0f
                                    previewDocs.clear()
                                    previewDocs.addAll(orderedDocs)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingDocId == null) return@detectDragGesturesAfterLongPress
                                    dragOffsetX += dragAmount.x

                                    val visible = listState.layoutInfo.visibleItemsInfo
                                    val firstVisible = visible.firstOrNull()?.index ?: 0
                                    val lastVisible = visible.lastOrNull()?.index ?: -1
                                    val atStartEdge = draggingPreviewIndex <= firstVisible
                                    val atEndEdge = draggingPreviewIndex >= lastVisible
                                    when {
                                        dragAmount.x < 0 && atStartEdge && firstVisible > 0 -> {
                                            if (autoScrollJob?.isActive != true) {
                                                autoScrollJob = scope.launch {
                                                    listState.animateScrollToItem((firstVisible - 1).coerceAtLeast(0))
                                                }
                                            }
                                        }
                                        dragAmount.x > 0 && atEndEdge && lastVisible < previewDocs.lastIndex -> {
                                            if (autoScrollJob?.isActive != true) {
                                                autoScrollJob = scope.launch {
                                                    listState.animateScrollToItem((lastVisible + 1).coerceAtMost(previewDocs.lastIndex))
                                                }
                                            }
                                        }
                                        else -> autoScrollJob?.cancel()
                                    }

                                    val from = draggingPreviewIndex
                                    val step = (dragOffsetX / itemWidthPx).toInt()
                                    if (step != 0 && from in previewDocs.indices) {
                                        val to = (from + step).coerceIn(0, previewDocs.lastIndex)
                                        if (to != from) {
                                            val moved = previewDocs.removeAt(from)
                                            previewDocs.add(to, moved)
                                            draggingPreviewIndex = to
                                            dragOffsetX -= (to - from) * itemWidthPx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    autoScrollJob?.cancel()
                                    val oldIds = orderedDocs.map { it.id }
                                    val newIds = previewDocs.map { it.id }
                                    if (newIds != oldIds) {
                                        orderedDocs.clear()
                                        orderedDocs.addAll(previewDocs)
                                        onReorder(newIds)
                                    }
                                    draggingDocId = null
                                    draggingPreviewIndex = -1
                                    dragOffsetX = 0f
                                },
                                onDragCancel = {
                                    autoScrollJob?.cancel()
                                    draggingDocId = null
                                    draggingPreviewIndex = -1
                                    dragOffsetX = 0f
                                }
                            )
                        }
                        .pointerInput(doc.id) {
                            detectTapGestures {
                                onSelectPage(doc.id)
                            }
                        }
                ) {
                    ViewerThumbnailItem(
                        bitmap = thumbnails[doc.id],
                        index = index + 1,
                        isActive = isActive
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerThumbnailItem(
    bitmap: Bitmap?,
    index: Int,
    isActive: Boolean
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.36f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 66.dp, height = 74.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

// ── OCR action button (bottom-right) ─────────────────────────────────────────

@Composable
private fun OcrActionButton(
    state: ViewerUiState,
    viewModel: DocumentViewerViewModel
) {
    val activeDoc = state.activeDocument ?: return

    Surface(
        shape     = CircleShape,
        color     = Color.White.copy(alpha = 0.15f),
        modifier  = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                state.isOcrRunning -> {
                    CircularProgressIndicator(
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                activeDoc.isOcrProcessed -> {
                    // Toggle highlights
                    IconButton(onClick = viewModel::toggleHighlights) {
                        Icon(
                            imageVector        = Icons.Default.TextFields,
                            contentDescription = "Toggle OCR highlights",
                            tint = if (state.showHighlights)
                                MaterialTheme.colorScheme.primary else Color.White.copy(0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                else -> {
                    // Run OCR
                    IconButton(onClick = viewModel::extractOcr) {
                        Icon(
                            imageVector        = Icons.Default.TextFields,
                            contentDescription = "Extract text",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Region tooltip ────────────────────────────────────────────────────────────

@Composable
private fun RegionTooltip(
    region: TextRegion?,
    isGrouped: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = region != null,
        enter   = fadeIn() + scaleIn(initialScale = 0.95f),
        exit    = fadeOut()
    ) {
        if (region == null) return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start   = 16.dp,
                    end     = 16.dp,
                    bottom  = if (isGrouped) 140.dp else 60.dp
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                    )
                    Text(
                        text     = region.text,
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick  = onDismiss,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
