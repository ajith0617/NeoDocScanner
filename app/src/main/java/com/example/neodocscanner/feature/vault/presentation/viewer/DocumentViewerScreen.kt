package com.example.neodocscanner.feature.vault.presentation.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.feature.vault.presentation.detail.DocumentDetailSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import android.graphics.Bitmap
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
    val scope = rememberCoroutineScope()

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

            // ── Page indicator (bottom-centre for grouped docs) ───────────────
            if (state.isGrouped) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                ) {
                    PageIndicatorPill(
                        state    = state,
                        onPrev   = { viewModel.goToPage(state.currentPageIndex - 1) },
                        onNext   = { viewModel.goToPage(state.currentPageIndex + 1) }
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
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex,
        pageCount   = { allPages.size }
    )

    // Sync pager ↔ ViewModel
    LaunchedEffect(state.currentPageIndex) {
        if (pagerState.currentPage != state.currentPageIndex) {
            pagerState.animateScrollToPage(state.currentPageIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != state.currentPageIndex) {
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
    var scale  by remember { mutableFloatStateOf(1f) }
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

// ── Page indicator pill ───────────────────────────────────────────────────────

@Composable
private fun PageIndicatorPill(
    state: ViewerUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick  = onPrev,
                enabled  = state.currentPageIndex > 0,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous",
                    tint   = if (state.currentPageIndex > 0) Color.White else Color.White.copy(0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text  = "${state.currentPageIndex + 1} of ${state.pageCount} — " +
                        state.pageLabel(state.currentPageIndex),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            IconButton(
                onClick  = onNext,
                enabled  = state.currentPageIndex < state.pageCount - 1,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next",
                    tint   = if (state.currentPageIndex < state.pageCount - 1)
                        Color.White else Color.White.copy(0.25f),
                    modifier = Modifier.size(16.dp)
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
