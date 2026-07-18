package com.google.ai.edge.gallery.ocr

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenExplainManager {
    private val _ocrResults = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ocrResults = _ocrResults.asSharedFlow()

    fun emitResult(text: String) {
        _ocrResults.tryEmit(text)
    }
}
