package com.google.ai.edge.gallery.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Language not supported")
            } else {
                tts?.setSpeechRate(1.25f)

                // Scan for the highest quality offline voice
                try {
                    val voices = tts?.voices
                    if (voices != null) {
                        // Filter for local (offline) voices matching the selected language (English)
                        val localEnVoices = voices.filter { 
                            !it.isNetworkConnectionRequired && 
                            it.locale.language == "en" 
                        }

                        if (localEnVoices.isNotEmpty()) {
                            // Sort by quality (QUALITY_VERY_HIGH = 500, QUALITY_HIGH = 400, NORMAL = 300...)
                            val bestVoice = localEnVoices.maxByOrNull { it.quality }
                            
                            if (bestVoice != null) {
                                tts?.voice = bestVoice
                                val qualityStr = when(bestVoice.quality) {
                                    android.speech.tts.Voice.QUALITY_VERY_HIGH -> "VERY_HIGH"
                                    android.speech.tts.Voice.QUALITY_HIGH -> "HIGH"
                                    android.speech.tts.Voice.QUALITY_NORMAL -> "NORMAL"
                                    android.speech.tts.Voice.QUALITY_LOW -> "LOW"
                                    android.speech.tts.Voice.QUALITY_VERY_LOW -> "VERY_LOW"
                                    else -> "UNKNOWN (${bestVoice.quality})"
                                }
                                Log.d("TtsManager", "Selected local offline voice: ${bestVoice.name} (Quality: $qualityStr)")
                                
                                if (bestVoice.quality < android.speech.tts.Voice.QUALITY_HIGH) {
                                    Log.w("TtsManager", "WARNING: No high-quality offline voice pack installed! Device is using $qualityStr. Go to Android TTS Settings -> Install Voice Data to download a high-quality pack for the demo.")
                                }
                            }
                        } else {
                            Log.w("TtsManager", "No local English voices found!")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TtsManager", "Failed to scan voices", e)
                }

                isInitialized = true
            }
        } else {
            Log.e("TtsManager", "Initialization failed")
        }
    }

    private fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*|__"), "") // Bold
            .replace(Regex("\\*|_"), "") // Italic
            .replace(Regex("###|##|#"), "") // Headers
            .replace(Regex("`"), "") // Inline code
            .replace(Regex("~~"), "") // Strikethrough
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Links: [text](url) -> text
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (isInitialized) {
            val cleanText = stripMarkdown(text)
            tts?.speak(cleanText, queueMode, null, "TraceTTS")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
