package com.google.ai.edge.gallery.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val helper = OcrHelper()
    private var latestBitmap: Bitmap? = null
    
    companion object {
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1337
        private const val FIRST_FRAME_TIMEOUT_MS = 2_000L
        private const val FRAME_POLL_INTERVAL_MS = 50L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Listen for requests retained while MediaProjection consent is being granted.
        scope.launch {
            ScreenExplainManager.captureRequest.filterNotNull().collect { request ->
                val bitmap = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                    while (latestBitmap == null) delay(FRAME_POLL_INTERVAL_MS)
                    latestBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                }
                bitmap?.let { bmp ->
                    try {
                        val result = helper.recognizeText(bmp)
                        ScreenExplainManager.emitResult(
                            ScreenExplainResult(
                                question = request.question,
                                ocrText = result.rawText,
                                bitmap = bmp,
                                speakResponse = request.speakResponse,
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("ScreenCaptureService", "OCR failed", e)
                        ScreenExplainManager.emitResult(
                            ScreenExplainResult(request.question, "", bmp, request.speakResponse)
                        )
                    }
                } ?: run {
                    ScreenExplainManager.emitResult(
                        ScreenExplainResult(request.question, "", null, request.speakResponse)
                    )
                }
                ScreenExplainManager.finishCapture(request)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Agent is analyzing your screen...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != 0 && resultData != null) {
            // Only start projection if not already started
            if (mediaProjection == null) {
                startCapture(resultCode, resultData)
            }
            ScreenExplainManager.isServiceRunning = true
        } else {
            // Stop service if started without valid projection data
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i("ScreenCaptureService", "MediaProjection stopped by the system or user")
                    stopSelf()
                }
            },
            Handler(mainLooper),
        )

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop out the padding
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()
                
                val oldBitmap = latestBitmap
                latestBitmap = croppedBitmap
                oldBitmap?.recycle()
                
            } catch (e: Exception) {
                Log.e("ScreenCaptureService", "Image extraction failed", e)
            } finally {
                image.close()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenExplainManager.isServiceRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        latestBitmap?.recycle()
        latestBitmap = null
        helper.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
