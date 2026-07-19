package com.google.ai.edge.gallery.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenExplainRequest(
    val question: String,
    val speakResponse: Boolean,
)

data class ScreenExplainResult(
    val question: String,
    val ocrText: String,
    val bitmap: Bitmap?,
    val speakResponse: Boolean,
)

object ScreenExplainManager {
    private val _results = MutableSharedFlow<ScreenExplainResult>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    // StateFlow retains a request while Android shows the MediaProjection consent dialog and the
    // foreground service is starting. A SharedFlow without replay lost that first request.
    private val _captureRequest = MutableStateFlow<ScreenExplainRequest?>(null)
    val captureRequest = _captureRequest.asStateFlow()

    fun emitResult(result: ScreenExplainResult) {
        _results.tryEmit(result)
    }

    fun requestCapture(request: ScreenExplainRequest) {
        _captureRequest.value = request
    }

    fun finishCapture(request: ScreenExplainRequest) {
        if (_captureRequest.value == request) {
            _captureRequest.value = null
        }
    }

    @Volatile
    var isServiceRunning: Boolean = false
}
