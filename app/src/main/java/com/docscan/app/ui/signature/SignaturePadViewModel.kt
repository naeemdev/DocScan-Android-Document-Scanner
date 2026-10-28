package com.docscan.app.ui.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.Point
import com.docscan.app.domain.usecase.SaveSignatureUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SignatureEvent {
    data object Saved : SignatureEvent
    data object Failed : SignatureEvent
}

class SignaturePadViewModel(private val saveSignature: SaveSignatureUseCase) : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _events = Channel<SignatureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onSave(strokes: List<List<Point>>, aspectRatio: Float) = viewModelScope.launch {
        if (_saving.value) return@launch
        _saving.value = true
        attempt { saveSignature(strokes, aspectRatio) }
            .onSuccess { _events.send(SignatureEvent.Saved) }
            .onFailure { _events.send(SignatureEvent.Failed) }
        _saving.value = false
    }
}
