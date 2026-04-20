package com.example.neodocscanner.feature.vault.presentation.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
        val file = fileManager.resolveAbsolute(doc.relativePath)
        if (!file.exists()) {
            _state.update { it.copy(isLoading = false, error = "PDF file not found on disk") }
            return
        }
        try {
            val bitmaps = withContext(Dispatchers.IO) {
                renderPdfToBitmaps(file)
            }
            _state.update { it.copy(pages = bitmaps, isLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to render PDF") }
        }
    }

    /** iOS: PDFViewRepresentable wraps PDFKit. Android uses PdfRenderer. */
    private fun renderPdfToBitmaps(file: File): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { fd ->
            val renderer = PdfRenderer(fd)
            renderer.use {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render at screen density (72 dpi base, scale 2× for readability)
                    val scale = 2
                    val bmp = Bitmap.createBitmap(
                        page.width  * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    // Fill white background (PDF pages default to transparent)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    result.add(bmp)
                }
            }
        }
        return result
    }

    fun getShareUri(context: Context): android.net.Uri? {
        val doc = _state.value.document ?: return null
        if (doc.relativePath.isBlank()) return null
        val file = fileManager.resolveAbsolute(doc.relativePath)
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
    val scope   = rememberCoroutineScope()

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
            modifier          = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment  = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }
                state.error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text  = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                state.pages.isEmpty() -> {
                    Text("No pages to display", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    PdfPageList(pages = state.pages)

                    // Page count pill (bottom-centre, same as iOS)
                    if (state.pageCount > 0) {
                        Box(
                            modifier          = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            contentAlignment  = Alignment.BottomCenter
                        ) {
                            Text(
                                text      = "${state.pageCount} page${if (state.pageCount == 1) "" else "s"}",
                                color     = Color.White,
                                fontSize  = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier  = Modifier
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
        items(pages) { bmp ->
            Image(
                bitmap           = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale     = ContentScale.FillWidth,
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
