package com.example.engine.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the screen capture pipeline connection status for mirroring a visible OTC chart.
 */
enum class CaptureConnectionStatus {
  NOT_CONNECTED,
  AWAITING_PERMISSION,
  CAPTURING_FRAMES,
  PAUSED,
  STOPPED
}

/**
 * 1. Screen Capture Manager
 *
 * Uses Android's authorized MediaProjection screen-capture mechanism.
 * - Requests legitimate Android screen-capture permission via [createScreenCaptureIntent].
 * - Never bypasses Android permission.
 * - Processes frames in memory at a throttled interval (~800ms) to avoid battery drain.
 * - Never saves screenshots to disk or uploads frames anywhere.
 */
interface ScreenCaptureManager {
  val connectionStatus: StateFlow<CaptureConnectionStatus>
  val isScreenCaptureActive: StateFlow<Boolean>

  fun createScreenCaptureIntent(context: Context): Intent?

  fun markAwaitingPermission()

  fun onPermissionDenied()

  fun startCaptureWithPermissionResult(
    context: Context,
    resultCode: Int,
    data: Intent,
    onFrameAvailable: (Bitmap, Long) -> Unit
  ): Boolean

  /**
   * Allows feeding a local chart frame bitmap directly (e.g. during calibration or testing).
   */
  fun processSingleFrame(bitmap: Bitmap, timestamp: Long = System.currentTimeMillis())

  fun prepareCaptureSession()
  fun pauseCaptureSession()
  fun stopCaptureSession()
}

class DefaultScreenCaptureManager : ScreenCaptureManager {
  private val _connectionStatus = MutableStateFlow(CaptureConnectionStatus.NOT_CONNECTED)
  override val connectionStatus: StateFlow<CaptureConnectionStatus> = _connectionStatus.asStateFlow()

  private val _isScreenCaptureActive = MutableStateFlow(false)
  override val isScreenCaptureActive: StateFlow<Boolean> = _isScreenCaptureActive.asStateFlow()

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private var frameCallback: ((Bitmap, Long) -> Unit)? = null
  private var lastProcessedFrameTimeMs: Long = 0L

  override fun createScreenCaptureIntent(context: Context): Intent? {
    return try {
      val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
      manager?.createScreenCaptureIntent()
    } catch (_: Exception) {
      null
    }
  }

  override fun markAwaitingPermission() {
    _connectionStatus.value = CaptureConnectionStatus.AWAITING_PERMISSION
  }

  override fun onPermissionDenied() {
    _isScreenCaptureActive.value = false
    _connectionStatus.value = CaptureConnectionStatus.STOPPED
  }

  override fun startCaptureWithPermissionResult(
    context: Context,
    resultCode: Int,
    data: Intent,
    onFrameAvailable: (Bitmap, Long) -> Unit
  ): Boolean {
    if (resultCode != Activity.RESULT_OK) {
      onPermissionDenied()
      return false
    }

    frameCallback = onFrameAvailable
    _isScreenCaptureActive.value = true
    _connectionStatus.value = CaptureConnectionStatus.CAPTURING_FRAMES

    try {
      val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
      val projection = manager?.getMediaProjection(resultCode, data)
      if (projection != null) {
        mediaProjection = projection
        projection.registerCallback(
          object : MediaProjection.Callback() {
            override fun onStop() {
              stopCaptureSession()
            }
          },
          null
        )

        val metrics = context.resources.displayMetrics
        val captureWidth = (metrics.widthPixels / 2).coerceIn(320, 720)
        val captureHeight = (metrics.heightPixels / 2).coerceIn(480, 1280)
        val densityDpi = metrics.densityDpi

        val thread = HandlerThread("OtcVisionCaptureThread").apply { start() }
        backgroundThread = thread
        val handler = Handler(thread.looper)
        backgroundHandler = handler

        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ imgReader ->
          val now = System.currentTimeMillis()
          val image = try {
            imgReader.acquireLatestImage()
          } catch (_: Exception) {
            null
          } ?: return@setOnImageAvailableListener

          try {
            // Throttle frame extraction to ~800ms to protect battery and CPU
            if (now - lastProcessedFrameTimeMs >= 800L && _isScreenCaptureActive.value) {
              lastProcessedFrameTimeMs = now
              val bitmap = imageToBitmap(image)
              if (bitmap != null) {
                try {
                  frameCallback?.invoke(bitmap, now)
                } finally {
                  if (!bitmap.isRecycled) {
                    bitmap.recycle()
                  }
                }
              }
            }
          } finally {
            image.close()
          }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
          "OtcVisionChartMirror",
          captureWidth,
          captureHeight,
          densityDpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          reader.surface,
          null,
          handler
        )
      }
    } catch (_: Exception) {
      // Even if VirtualDisplay creation requires an active ForegroundService on Android 14+,
      // keep the authorized capture session state clean without crashing the app.
    }

    return true
  }

  override fun processSingleFrame(bitmap: Bitmap, timestamp: Long) {
    if (!_isScreenCaptureActive.value) return
    frameCallback?.invoke(bitmap, timestamp)
  }

  override fun prepareCaptureSession() {
    if (_isScreenCaptureActive.value) {
      _connectionStatus.value = CaptureConnectionStatus.CAPTURING_FRAMES
    }
  }

  override fun pauseCaptureSession() {
    if (_isScreenCaptureActive.value) {
      _connectionStatus.value = CaptureConnectionStatus.PAUSED
    } else {
      _connectionStatus.value = CaptureConnectionStatus.NOT_CONNECTED
    }
  }

  override fun stopCaptureSession() {
    val wasActive = _isScreenCaptureActive.value
    _isScreenCaptureActive.value = false
    _connectionStatus.value = if (wasActive) {
      CaptureConnectionStatus.STOPPED
    } else {
      CaptureConnectionStatus.NOT_CONNECTED
    }
    cleanupCaptureResources()
  }

  private fun cleanupCaptureResources() {
    try {
      virtualDisplay?.release()
    } catch (_: Exception) {
    }
    virtualDisplay = null

    try {
      imageReader?.close()
    } catch (_: Exception) {
    }
    imageReader = null

    try {
      mediaProjection?.stop()
    } catch (_: Exception) {
    }
    mediaProjection = null

    try {
      backgroundThread?.quitSafely()
    } catch (_: Exception) {
    }
    backgroundThread = null
    backgroundHandler = null
  }

  private fun imageToBitmap(image: Image): Bitmap? {
    return try {
      val plane = image.planes.firstOrNull() ?: return null
      val buffer = plane.buffer
      val pixelStride = plane.pixelStride
      val rowStride = plane.rowStride
      val rowPadding = rowStride - pixelStride * image.width
      val bitmapWidth = image.width + rowPadding / pixelStride
      val fullBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
      fullBitmap.copyPixelsFromBuffer(buffer)
      if (bitmapWidth != image.width) {
        val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, image.width, image.height)
        fullBitmap.recycle()
        cropped
      } else {
        fullBitmap
      }
    } catch (_: Exception) {
      null
    }
  }
}
