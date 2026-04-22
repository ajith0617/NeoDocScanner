package com.example.neodocscanner.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import com.example.neodocscanner.feature.vault.data.service.text.DocumentFieldExtractorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FieldExtractorDebugState(
    val selectedClass: DocumentClass = DocumentClass.AADHAAR,
    val frontText: String = "",
    val backText: String = "",
    val extractedFields: List<DocumentField> = emptyList()
)

@HiltViewModel
class FieldExtractorDebugViewModel @Inject constructor(
    private val extractor: DocumentFieldExtractorService
) : ViewModel() {

    private val _state = MutableStateFlow(FieldExtractorDebugState())
    val state: StateFlow<FieldExtractorDebugState> = _state.asStateFlow()

    fun selectClass(documentClass: DocumentClass) {
        _state.update { it.copy(selectedClass = documentClass) }
    }

    fun updateFrontText(text: String) {
        _state.update { it.copy(frontText = text) }
    }

    fun updateBackText(text: String) {
        _state.update { it.copy(backText = text) }
    }

    fun runExtraction() {
        val current = _state.value
        val fields = extractor.extract(
            documentClass = current.selectedClass,
            text = current.frontText,
            backText = current.backText
        )
        _state.update { it.copy(extractedFields = fields) }
    }
}

