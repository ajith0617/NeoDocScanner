package com.example.neodocscanner.feature.vault.presentation.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.data.file.FileManagerRepository
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class PdfViewerUiState(
    val document: Document? = null,
    val pages: List<Bitmap> = emptyList(),
    val isLoading: Boolean  = true,
    val error: String?      = null
) {
    val pageCount: Int get() = pages.size
    val title: String get() = document?.groupName
        ?: document?.fileName?.substringBeforeLast(".")
        ?: "PDF"
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val fileManager: FileManagerRepository
) : ViewModel() {

    private val documentId: String = savedState[Screen.PdfViewer.ARG_DOCUMENT_ID] ?: ""

    private val _state = MutableStateFlow(PdfViewerUiState())
    val state: StateFlow<PdfViewerUiState> = _state.asStateFlow()

    init {
        loadPdf()
    }

    private fun loadPdf() {
        viewModelScope.launch {
            val doc = documentRepository.getById(documentId)
            if (doc == null) {
                _state.update { it.copy(isLoading = false, error = "Document not found") }
                return@launch
            }
            _state.update { it.copy(document = doc) }
            renderPages(doc)
        }
    }

    private suspend fun renderPages(doc: Document) {
        if (doc.relativePath.isBlank()) {
            _state.update { it.copy(isLoading = false, error = "PDF path is empty") }
            return
        }
        val file = resolvePdfFileForViewer(doc.relativePath)
        if (file == null || !file.exists()) {
            _state.update { it.copy(isLoading = false, error = "PDF file not found on disk") }
            return
        }
        try {
            val bitmaps = withContext(Dispatchers.IO) {
                renderPdfToBitmaps(file)
            }
            if (bitmaps.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "This PDF has no readable pages."
                    )
                }
            } else {
                _state.update { it.copy(pages = bitmaps, isLoading = false, error = null) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to render PDF") }
        }
    }

    /**
     * Resolves DB relative path (under NeoDocs/) plus common mistaken double-prefix storage.
     */
    private fun resolvePdfFileForViewer(rawPath: String): File? {
        val trimmed = rawPath.trim().removePrefix("/")
        if (trimmed.isBlank()) return null
        val direct = fileManager.resolveAbsolute(trimmed)
        if (direct.exists() && direct.isFile) return direct
        val withoutNeoDocs = trimmed
            .removePrefix("NeoDocs/")
            .removePrefix("neodocs/")
        if (withoutNeoDocs != trimmed) {
            val nested = fileManager.resolveAbsolute(withoutNeoDocs)
            if (nested.exists() && nested.isFile) return nested
        }
        val absolute = File(trimmed)
        if (absolute.exists() && absolute.isFile) return absolute
        // Path stored as full path under app files (defensive)
        val underFiles = File(fileManager.appDocumentsDir.parentFile, trimmed)
        return underFiles.takeIf { it.exists() && it.isFile }
    }

    /**
     * Renders each PDF page to a bitmap. Uses **bitmap size == page size** with a **null** transform
     * (platform-recommended); scaled-down copies only when the page is huge to avoid OOM / GPU limits.
     * Compose scales to screen width in the list.
     */
    private fun renderPdfToBitmaps(file: File): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount <= 0) return@use
                val maxEdge = 4096
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    try {
                        val pw = page.width.coerceAtLeast(1)
                        val ph = page.height.coerceAtLeast(1)
                        val scaleDown = minOf(
                            1f,
                            maxEdge / pw.toFloat(),
                            maxEdge / ph.toFloat()
                        )
                        val bw = (pw * scaleDown).toInt().coerceAtLeast(1)
                        val bh = (ph * scaleDown).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        val matrix = if (scaleDown < 1f) {
                            Matrix().apply { setScale(scaleDown, scaleDown) }
                        } else {
                            null
                        }
                        page.render(
                            bmp,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        result.add(bmp)
                    } finally {
                        page.close()
                    }
                }
            }
        }
        return result
    }

    fun getShareUri(context: Context): android.net.Uri? {
        val doc = _state.value.document ?: return null
        if (doc.relativePath.isBlank()) return null
        val file = resolvePdfFileForViewer(doc.relativePath) ?: return null
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * In-app PDF viewer for merged group PDFs.
 *
 * iOS equivalent: PDFDocumentViewer.swift — wraps PDFKit in a NavigationStack
 * with Done + Share toolbar buttons and a page count pill.
 *
 * Android implementation: renders all pages with PdfRenderer into a LazyColumn
 * (vertical scroll, page-by-page), with a top AppBar and Share action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = state.title,
                        maxLines = 1,
                        style    = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val uri = viewModel.getShareUri(context)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type    = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                state.pages.isEmpty() -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No pages to display", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    // Full-bleed list (no parent Center — avoids wrong constraints for LazyColumn + Image).
                    Box(modifier = Modifier.fillMaxSize()) {
                        PdfPageList(pages = state.pages)
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text       = "${state.pageCount} page${if (state.pageCount == 1) "" else "s"}",
                                color      = Color.White,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.45f),
                                        MaterialTheme.shapes.extraLarge
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageList(pages: List<Bitmap>) {
    val listState = rememberLazyListState()
    LazyColumn(
        state    = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            pages,
            key = { index, _ -> index }
        ) { index, bmp ->
            val iw = bmp.width.coerceAtLeast(1)
            val ih = bmp.height.coerceAtLeast(1)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val pageHeight = maxWidth * (ih.toFloat() / iw.toFloat())
                val imageBitmap = remember(index, System.identityHashCode(bmp)) {
                    bmp.asImageBitmap()
                }
                Image(
                    bitmap             = imageBitmap,
                    contentDescription = null,
                    contentScale       = ContentScale.FillWidth,
                    modifier           = Modifier
                        .width(maxWidth)
                        .height(pageHeight)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
