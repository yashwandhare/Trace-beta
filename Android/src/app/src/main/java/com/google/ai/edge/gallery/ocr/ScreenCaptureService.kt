package com.google.ai.edge.gallery.ocr

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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ScreenCaptureService"

/**
 * Foreground service that owns the MediaProjection lifecycle for screen explain.
 *
 * ### Lifecycle design (on-demand, not continuous)
 *
 * The service starts on the first "explain screen" query, processes exactly one
 * request per start, then calls [stopSelf] — so the VirtualDisplay, ImageReader,
 * and ML Kit recognizer are all released as soon as the answer is delivered.
 *
 * If the user asks again, the system shows the MediaProjection consent dialog
 * again (on Android 14+) or reuses a cached grant (13 and below), and the
 * service is restarted fresh for that query.
 *
 * ### Why not continuous?
 * A continuous VirtualDisplay renders at full display refresh rate (~60 Hz)
 * into the ImageReader buffer regardless of whether a query is pending.
 * At 1080×2400 ARGB_8888 that's a ~10 MB bitmap allocation every 500 ms plus
 * persistent GPU compositing pressure, measured to cause visible lag during
 * normal chat use. See profiling notes in the conversation log (2026-07-18).
 *
 * ### Scope leak fix
 * The previous implementation used a bare [CoroutineScope] that was never
 * cancelled — if [onDestroy] raced with a running coroutine the scope would
 * keep the IO thread alive indefinitely. The scope now uses [SupervisorJob]
 * and is explicitly cancelled in [onDestroy].
 */
class ScreenCaptureService : Service() {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    /**
     * Dedicated background thread for ImageReader frame callbacks. Without this, a null
     * Handler makes the callback run on the thread that created the ImageReader — here the
     * main thread (startCapture is invoked from onStartCommand) — so the ~5 MB
     * createBitmap/copyPixelsFromBuffer would execute on the UI thread and jank it.
     */
    private var imageReaderThread: android.os.HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    /**
     * Properly-scoped coroutine scope — cancelled in [onDestroy] so no
     * coroutine can outlive the service.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val helper = OcrHelper()

    /**
     * The most recently decoded frame from the VirtualDisplay.
     * Written by the ImageReader callback; read by the request-processing coroutine.
     * Access is safe because both run on [Dispatchers.IO] which is a thread pool,
     * but the write happens from the ImageReader handler (null handler = caller's
     * thread — here the ImageReader handler thread). We use @Volatile to guarantee
     * visibility across threads.
     */
    @Volatile private var latestBitmap: Bitmap? = null

    companion object {
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1337

        /** Max time to wait for the first frame to arrive after VirtualDisplay is created. */
        private const val FIRST_FRAME_TIMEOUT_MS = 3_000L
        private const val FRAME_POLL_INTERVAL_MS = 50L

        /**
         * Minimum interval between full-resolution bitmap decodes.
         * The ImageReader fires at display refresh rate (~60 Hz); we only need
         * one fresh frame per query, so we throttle decoding to save memory/CPU.
         * This is only active while waiting for a frame — once the frame is
         * captured and the service is about to stop, it becomes irrelevant.
         */
        private const val BITMAP_THROTTLE_MS = 500L
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "onCreate: service created, scope started")

        // Collect capture requests. The StateFlow holds the request that was
        // posted while the MediaProjection consent dialog was open, so this
        // fires immediately once the service is running with a valid projection.
        serviceScope.launch {
            ScreenExplainManager.captureRequest.filterNotNull().collect { request ->
                Log.d(TAG, "processRequest: received request question=\"${request.question}\"")
                processRequest(request)

                // ---------------------------------------------------------------
                // STOP AFTER EACH QUERY — core of the on-demand design.
                //
                // Once the result is emitted, there is no reason to keep the
                // VirtualDisplay alive. Stopping here releases GPU compositing,
                // the ImageReader buffer (~10 MB), and the ML Kit recognizer.
                // The service will be restarted on the next screen-explain query.
                // ---------------------------------------------------------------
                Log.d(TAG, "processRequest: done, stopping service")
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Analyzing screen…")
            .setContentText("Trace is reading your screen for this query")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != 0 && resultData != null) {
            if (mediaProjection == null) {
                Log.d(TAG, "onStartCommand: starting capture with valid projection data")
                startCapture(resultCode, resultData)
            }
            ScreenExplainManager.isServiceRunning = true
        } else {
            Log.w(TAG, "onStartCommand: no valid projection data — stopping immediately")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: releasing all resources")

        // Cancel the scope FIRST so no new coroutine work starts during teardown.
        serviceScope.cancel("ScreenCaptureService destroyed")

        ScreenExplainManager.isServiceRunning = false

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        // Stop the frame-callback thread so it doesn't outlive the service.
        imageReaderHandler = null
        imageReaderThread?.quitSafely()
        imageReaderThread = null

        // MediaProjection.stop() triggers onStop() callback; safe to call in destroy.
        mediaProjection?.stop()
        mediaProjection = null

        latestBitmap?.recycle()
        latestBitmap = null

        helper.close()

        Log.d(TAG, "onDestroy: cleanup complete")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Core request processing
    // -------------------------------------------------------------------------

    /**
     * Waits for the first available frame, runs OCR on it, emits the result,
     * and marks the request as finished.
     *
     * This runs on [serviceScope] (IO dispatcher). After it returns, the
     * caller stops the service.
     */
    private suspend fun processRequest(request: ScreenExplainRequest) {
        val bitmap = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
            while (latestBitmap == null) delay(FRAME_POLL_INTERVAL_MS)
            // Guard against onDestroy recycling latestBitmap between the null-check and copy.
            try {
                latestBitmap?.takeIf { !it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                Log.w(TAG, "processRequest: frame copy failed (likely recycled during teardown)", e)
                null
            }
        }

        if (bitmap != null) {
            Log.d(TAG, "processRequest: got frame, running OCR")
            try {
                val result = helper.recognizeText(bitmap)
                Log.d(TAG, "processRequest: OCR done, ${result.wordCount} words in ${result.elapsedMs}ms")
                ScreenExplainManager.emitResult(
                    ScreenExplainResult(
                        question = request.question,
                        ocrText = result.rawText,
                        bitmap = bitmap,
                        speakResponse = request.speakResponse,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "processRequest: OCR failed", e)
                // Emit with empty OCR text so Gemma still gets the screenshot.
                ScreenExplainManager.emitResult(
                    ScreenExplainResult(request.question, "", bitmap, request.speakResponse)
                )
            }
        } else {
            Log.w(TAG, "processRequest: timed out waiting for first frame (${FIRST_FRAME_TIMEOUT_MS}ms)")
            ScreenExplainManager.emitResult(
                ScreenExplainResult(request.question, "", null, request.speakResponse)
            )
        }

        ScreenExplainManager.finishCapture(request)
    }

    // -------------------------------------------------------------------------
    // VirtualDisplay setup
    // -------------------------------------------------------------------------

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        // Downscale capture resolution to reduce lag and memory overhead.
        // ML Kit scales down internally anyway, so half-res is perfectly fine for OCR.
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped externally — stopping service")
                    // Clear the pending request so the coroutine doesn't hang waiting
                    // for a frame that will never arrive.
                    ScreenExplainManager.captureRequest.value?.let {
                        ScreenExplainManager.finishCapture(it)
                    }
                    stopSelf()
                }
            },
            Handler(mainLooper),
        )

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // Background thread for frame callbacks so bitmap decode never touches the UI thread.
        val readerThread = android.os.HandlerThread("TraceImageReader").also { it.start() }
        val readerHandler = Handler(readerThread.looper)
        imageReaderThread = readerThread
        imageReaderHandler = readerHandler

        var lastDecodeTime = 0L

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            // Only decode a bitmap if a request is actually pending AND
            // we haven't decoded one recently. This prevents wasteful allocations
            // while the service is initialising or between multiple rapid queries.
            val now = System.currentTimeMillis()
            val requestPending = ScreenExplainManager.captureRequest.value != null
            val throttleExpired = (now - lastDecodeTime) >= BITMAP_THROTTLE_MS

            if (!requestPending || !throttleExpired) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastDecodeTime = now

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val raw = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                raw.copyPixelsFromBuffer(buffer)

                // Crop the row-padding artifact out.
                val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
                raw.recycle()

                val old = latestBitmap
                latestBitmap = cropped
                old?.recycle()

                Log.v(TAG, "imageListener: frame decoded (${width}×${height})")
            } catch (e: Exception) {
                Log.e(TAG, "imageListener: frame decode failed", e)
            } finally {
                image.close()
            }
        }, readerHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TraceScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null,
        )

        Log.d(TAG, "startCapture: VirtualDisplay created (${width}×${height} @ ${density}dpi)")
    }

    // -------------------------------------------------------------------------
    // Notification channel
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Trace reads your screen for a single query"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
