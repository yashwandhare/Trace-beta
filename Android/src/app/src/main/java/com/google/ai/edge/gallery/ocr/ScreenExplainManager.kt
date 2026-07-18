package com.google.ai.edge.gallery.ocr

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenExplainManager {
    private val _ocrResults = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ocrResults = _ocrResults.asSharedFlow()

    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests = _captureRequests.asSharedFlow()

    fun emitResult(text: String) {
        _ocrResults.tryEmit(text)
    }

    fun requestCapture() {
        _captureRequests.tryEmit(Unit)
    }

    var isServiceRunning: Boolean = false
}
