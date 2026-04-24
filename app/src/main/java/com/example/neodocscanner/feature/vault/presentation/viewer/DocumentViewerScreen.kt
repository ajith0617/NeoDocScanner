package com.example.neodocscanner.feature.vault.presentation.viewer

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.feature.vault.presentation.components.MoveToSectionSheet
import com.example.neodocscanner.feature.vault.presentation.detail.DocumentDetailSheet
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.hypot

/**
 * Full-screen document image viewer with:
 *  - Pinch-to-zoom (1x–4x) + double-tap reset (manual two-finger pinch so
 *    [HorizontalPager] is not blocked by transform detectors; one-finger pan only when zoomed)
 *  - Multi-page horizontal swipe for grouped docs ([HorizontalPager])
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
    val context = LocalContext.current

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val removeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val document = state.document ?: return
    LaunchedEffect(state.alertMessage) {
        val message = state.alertMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeAlert()
    }

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
                        onReorder = viewModel::reorderGroup
                    )
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

    // ── Move-to-category sheet ───────────────────────────────────────────────
    if (state.showMoveSheet) {
        val moveDoc = state.activeDocument ?: state.document
        MoveToSectionSheet(
            document = moveDoc,
            sectionsWithDocs = state.sectionsWithDocs,
            sheetState = moveSheetState,
            onMove = viewModel::routeToSection,
            onDismiss = viewModel::dismissMoveSheet
        )
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
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val regions = document.decodedRegions

    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            ZoomableImage(
                bitmap         = bitmap,
                regions        = if (showHighlights) regions else emptyList(),
                scale          = scale,
                offset         = offset,
                onScaleChange  = { scale = it },
                onOffsetChange = { offset = it },
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
    var isSyncingPagerFromState by remember { mutableStateOf(false) }
    val allPages = state.allPages
    val latestPageIndex by rememberUpdatedState(state.currentPageIndex)
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex,
        pageCount   = { allPages.size }
    )

    // Per-page zoom state. Hoisting it here lets the pager disable horizontal
    // scroll while a page is zoomed in (so pinch-pan wins over page-swipe),
    // and lets us reset zoom whenever the page changes.
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.02f

    // Sync pager → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!isSyncingPagerFromState && page != latestPageIndex) {
                viewModel.goToPage(page)
                tappedRegion = null
                scale  = 1f
                offset = Offset.Zero
            }
        }
    }

    // Sync ViewModel → pager (animated, so taps on the bottom strip glide
    // through the same swipe animation users get from a finger swipe).
    LaunchedEffect(state.currentPageIndex) {
        if (pagerState.currentPage != state.currentPageIndex) {
            isSyncingPagerFromState = true
            try {
                pagerState.animateScrollToPage(state.currentPageIndex)
            } finally {
                isSyncingPagerFromState = false
            }
            scale  = 1f
            offset = Offset.Zero
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state                 = pagerState,
            modifier              = Modifier.fillMaxSize(),
            userScrollEnabled     = !isZoomed,
            beyondViewportPageCount = 1,
            key                   = { idx -> allPages[idx].id }
        ) { pageIdx ->
            val doc    = allPages.getOrNull(pageIdx)
            val bitmap = doc?.let { state.imageBitmaps[it.id] }
            val isCurrentPage = pageIdx == pagerState.currentPage
            val regions = if (state.showHighlights && isCurrentPage)
                doc?.decodedRegions ?: emptyList() else emptyList()

            if (bitmap != null) {
                ZoomableImage(
                    bitmap         = bitmap,
                    regions        = regions,
                    scale          = if (isCurrentPage) scale else 1f,
                    offset         = if (isCurrentPage) offset else Offset.Zero,
                    onScaleChange  = { if (isCurrentPage) scale = it },
                    onOffsetChange = { if (isCurrentPage) offset = it },
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

/** Span between the first two pressed pointers and their midpoint; null if fewer than two. */
private fun spanAndCenterOfFirstTwoPressed(event: PointerEvent): Pair<Float, Offset>? {
    val pressed = event.changes.filter { it.pressed }.sortedBy { it.id.value }
    if (pressed.size < 2) return null
    val p0 = pressed[0].position
    val p1 = pressed[1].position
    val span = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat()
    val center = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
    return span to center
}

@Composable
private fun ZoomableImage(
    bitmap: Bitmap,
    regions: List<TextRegion>,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onRegionTapped: (TextRegion?) -> Unit
) {
    // Stable reads inside long-lived pointerInput coroutines (avoid stale closures).
    val latestScale  by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val onScale      by rememberUpdatedState(onScaleChange)
    val onOffset     by rememberUpdatedState(onOffsetChange)

    val zoomed = scale > 1.02f
    // One-finger pan only while zoomed. At 1× zoom we attach no drag detector so
    // HorizontalPager can own horizontal swipes.
    val panWhenZoomed = if (zoomed) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onOffset(latestOffset + dragAmount)
            }
        }
    } else {
        Modifier
    }

    // Pinch is implemented manually: only consume pointer deltas while ≥2 fingers.
    // detectTransformGestures / transformable still compete with HorizontalPager for
    // one-finger horizontal drags even when zoom == 1.
    val pinchModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var inPinch = false
            var prevSpan = 0f
            var prevCenter = Offset.Zero

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (!event.changes.any { it.pressed }) break

                val spanCenter = spanAndCenterOfFirstTwoPressed(event)
                if (spanCenter != null) {
                    val (span, center) = spanCenter
                    if (!inPinch) {
                        inPinch = true
                        prevSpan = span
                        prevCenter = center
                        event.changes.forEach { if (it.pressed) it.consume() }
                    } else {
                        val zoomFactor = if (prevSpan > 0f) span / prevSpan else 1f
                        val pan = center - prevCenter
                        val s = latestScale
                        val newScale = (s * zoomFactor).coerceIn(1f, 4f)
                        onScale(newScale)
                        if (newScale > 1.02f || s > 1.02f) {
                            onOffset(latestOffset + pan)
                        }
                        if (newScale <= 1.02f) {
                            onOffset(Offset.Zero)
                        }
                        prevSpan = span
                        prevCenter = center
                        event.changes.forEach { if (it.pressed) it.consume() }
                    }
                } else {
                    if (inPinch) {
                        inPinch = false
                        prevSpan = 0f
                    }
                    // Single-finger segment: do not consume — HorizontalPager / tap.
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap / double-tap first (inner); pinch last (outer) so pinch sees events
            // first and can abstain from consumption for one-finger drags.
            .pointerInput(regions, bitmap.width, bitmap.height) {
                detectTapGestures(
                    onDoubleTap = {
                        val target = if (latestScale > 1.5f) 1f else 2.5f
                        onScale(target)
                        onOffset(Offset.Zero)
                    },
                    onTap = { tapOffset ->
                        if (regions.isEmpty()) { onRegionTapped(null); return@detectTapGestures }
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
            .then(panWhenZoomed)
            .then(pinchModifier)
    ) {
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

        // Move to category (all viewer contexts)
        IconButton(onClick = onShowMoveSheet) {
            Icon(
                imageVector        = Icons.AutoMirrored.Outlined.DriveFileMove,
                contentDescription = "Move to category",
                tint               = Color.White
            )
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
    val haptic = LocalHapticFeedback.current
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestOnSelect  by rememberUpdatedState(onSelectPage)

    // Local mirror of the ordered pages. Mutated live during drag (so neighbours
    // slide out of the way smoothly) and pushed back to the ViewModel once the
    // finger lifts. We don't overwrite it from `pages` while a drag is in
    // flight, otherwise the parent's stale order would fight the local one.
    val orderedDocs = remember { mutableStateListOf<Document>() }
    var isDraggingStrip by remember { mutableStateOf(false) }

    LaunchedEffect(pages) {
        if (!isDraggingStrip && orderedDocs.map { it.id } != pages.map { it.id }) {
            orderedDocs.clear()
            orderedDocs.addAll(pages)
        }
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // Calvin's library calls onMove per-crossing during drag.
        orderedDocs.add(to.index, orderedDocs.removeAt(from.index))
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    // Auto-centre the active thumbnail when the page changes (from a pager
    // swipe or from an external selection). Skipped while the user is dragging
    // the strip so it doesn't yank under the finger.
    LaunchedEffect(activeDocumentId, pages) {
        if (isDraggingStrip || activeDocumentId == null) return@LaunchedEffect
        val idx = pages.indexOfFirst { it.id == activeDocumentId }
        if (idx < 0) return@LaunchedEffect
        val viewportWidth = listState.layoutInfo.viewportSize.width
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val itemWidth = itemInfo?.size ?: 82 * 3 // sensible fallback before first layout
        val centerOffset = -((viewportWidth - itemWidth) / 2)
        listState.animateScrollToItem(idx, scrollOffset = centerOffset)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        LazyRow(
            state                 = listState,
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding        = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(orderedDocs, key = { it.id }) { doc ->
                ReorderableItem(reorderableState, key = doc.id) { isDragging ->
                    val displayIndex = orderedDocs.indexOfFirst { it.id == doc.id } + 1
                    val isActive = if (isDraggingStrip) isDragging else activeDocumentId == doc.id

                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 92.dp)
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                    alpha  = 0.95f
                                    shadowElevation = 16f
                                }
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    isDraggingStrip = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    val finalOrder = orderedDocs.map { it.id }
                                    isDraggingStrip = false
                                    if (finalOrder != pages.map { it.id }) {
                                        latestOnReorder(finalOrder)
                                    }
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isDraggingStrip) latestOnSelect(doc.id)
                            }
                    ) {
                        ViewerThumbnailItem(
                            bitmap     = thumbnails[doc.id],
                            index      = displayIndex,
                            isActive   = isActive,
                            isDragging = isDragging
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerThumbnailItem(
    bitmap: Bitmap?,
    index: Int,
    isActive: Boolean,
    isDragging: Boolean
) {
    val borderColor = when {
        isDragging -> Color.White.copy(alpha = 0.92f)
        isActive   -> MaterialTheme.colorScheme.primary
        else       -> Color.White.copy(alpha = 0.36f)
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
                    width = if (isActive || isDragging) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color       = Color.White.copy(alpha = 0.9f)
                )
            }

            Surface(
                shape    = CircleShape,
                color    = if (isActive) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Text(
                    text       = "$index",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
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
