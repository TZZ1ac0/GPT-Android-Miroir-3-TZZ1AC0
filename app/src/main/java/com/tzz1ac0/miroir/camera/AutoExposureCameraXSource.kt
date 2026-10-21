/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tzz1ac0.miroir.camera

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.graphics.SurfaceTexture
import android.util.Range
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.input.video.Camera2ApiManager.ImageCallback
import com.pedro.encoder.input.video.Camera2ResolutionCalculator.getOptimalResolution
import com.pedro.encoder.input.video.CameraHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Created by pedro on 16/2/24.
 */
private const val CAMERA_SOURCE_TAG = "MiroirCameraDiag"

class AutoExposureCameraXSource(
  private val context: Context,
): VideoSource(), LifecycleOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
  private val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
  private var camera: Camera? = null
  private var preview = Preview.Builder().build()
  // Match the validated PreviewView baseline, which uses the front camera.
  private var facing = CameraSelector.LENS_FACING_FRONT
  private var cameraSelectorBuilder: CameraSelector.Builder = CameraSelector.Builder()
  private var surface: Surface? = null
  private var executor: ExecutorService? = null
  private var autoFocusEnabled = false
  private var autoExposureEnabled = false
  private var autoWhiteBalanceEnabled = false
  private var fingerSpacing = 0f
  private var requiredSize: Size? = null
  private var surfaceReleased = CountDownLatch(0)
  private var imageAnalysis: ImageAnalysis? = null
  private var analysisExecutor: ExecutorService? = null
  @Volatile private var analysisFrameCount = 0L

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  @OptIn(ExperimentalCamera2Interop::class)
  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    Log.i(CAMERA_SOURCE_TAG, "source_create width=$width height=$height fps=$fps rotation=$rotation")
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = requiredSize ?: getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    // Preserve the explicit AE configuration used by the original app implementation.
    // RootEncoder's upstream CameraXSource only adds an AE metering point and does not
    // explicitly set CONTROL_AE_MODE on the Preview capture request.
    val previewBuilder = Preview.Builder()
    Camera2Interop.Extender(previewBuilder)
      .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
      .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
    preview = previewBuilder
      .setTargetFrameRate(Range(fps, fps))
      .setResolutionSelector(
        ResolutionSelector.Builder()
          .setResolutionStrategy(
            ResolutionStrategy(
              optimalResolution,
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
          ).build()
      ).build()

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    Log.i(CAMERA_SOURCE_TAG, "source_create_done resolution=${optimalResolution.width}x${optimalResolution.height}")
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    Log.i(CAMERA_SOURCE_TAG, "source_start alreadyRunning=${isRunning()} texture=${surfaceTexture.hashCode()} glThread=${Thread.currentThread().name}")
    this.surfaceTexture = surfaceTexture
    if (isRunning()) return
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = requiredSize ?: getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    surfaceTexture.setDefaultBufferSize(optimalResolution.width, optimalResolution.height)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    val cameraSelector = cameraSelectorBuilder
      .requireLensFacing(this.facing)
      .build()

    preview.setSurfaceProvider { request ->
      Log.i(CAMERA_SOURCE_TAG, "surface_request resolution=${request.resolution.width}x${request.resolution.height} thread=${Thread.currentThread().name}")
      val surface = Surface(surfaceTexture)
      val executor = Executors.newSingleThreadExecutor()
      request.provideSurface(surface, executor) { result ->
        Log.i(CAMERA_SOURCE_TAG, "surface_result=${result.resultCode}")
        surface.release()
        executor.shutdown()
        surfaceReleased.countDown()
      }
      this.surface = surface
      this.executor = executor
    }
    // Observe CameraX delivery independently from RootEncoder's SurfaceTexture/GL path.
    // ImageAnalysis closes every proxy immediately and does not install a SurfaceTexture listener.
    val analysis = ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()
    val analyzerExecutor = Executors.newSingleThreadExecutor()
    analysisFrameCount = 0L
    analysis.setAnalyzer(analyzerExecutor) { image ->
      val count = ++analysisFrameCount
      if (count <= 5L || count % 30L == 0L) {
        // Sample the Y plane directly: this verifies actual camera pixels, not only frame delivery.
        var samples = 0
        var sumY = 0L
        var minY = 255
        var maxY = 0
        var rawHead = "unavailable"
        var bufferPosition = -1
        var bufferLimit = -1
        var rowStride = -1
        var pixelStride = -1
        val plane = image.planes.firstOrNull()
        if (plane != null) {
          val buffer = plane.buffer
          bufferPosition = buffer.position()
          bufferLimit = buffer.limit()
          rowStride = plane.rowStride
          pixelStride = plane.pixelStride
          val headCount = minOf(12, buffer.remaining())
          rawHead = (0 until headCount).joinToString(",") { offset ->
            (buffer.get(bufferPosition + offset).toInt() and 0xff).toString()
          }
          val stepX = (image.width / 32).coerceAtLeast(1)
          val stepY = (image.height / 24).coerceAtLeast(1)
          var y = 0
          while (y < image.height) {
            var x = 0
            while (x < image.width) {
              val index = bufferPosition + y * rowStride + x * pixelStride
              if (index >= bufferPosition && index < bufferLimit) {
                val value = buffer.get(index).toInt() and 0xff
                sumY += value
                minY = minOf(minY, value)
                maxY = maxOf(maxY, value)
                samples++
              }
              x += stepX
            }
            y += stepY
          }
        }
        val meanY = if (samples > 0) sumY / samples else -1
        Log.i(
          CAMERA_SOURCE_TAG,
          "image_analysis_frame count=$count size=${image.width}x${image.height} format=${image.format} " +
            "ySamples=$samples meanY=$meanY minY=$minY maxY=$maxY " +
            "yPosition=$bufferPosition yLimit=$bufferLimit rowStride=$rowStride pixelStride=$pixelStride rawHead=$rawHead"
        )
      }
      image.close()
    }
    imageAnalysis = analysis
    analysisExecutor = analyzerExecutor
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
    Log.i(CAMERA_SOURCE_TAG, "camera_bound running=${isRunning()} aeMode=ON analysis=true")
    camera?.cameraInfo?.cameraState?.observe(this) { state ->
      Log.i(CAMERA_SOURCE_TAG, "camera_state=${state.type} error=${state.error?.code}")
    }
    camera?.cameraInfo?.exposureState?.let { ae ->
      Log.i(CAMERA_SOURCE_TAG, "ae_state supported=${ae.isExposureCompensationSupported} range=${ae.exposureCompensationRange} index=${ae.exposureCompensationIndex}")
    }
  }

  override fun stop() {
    Log.i(CAMERA_SOURCE_TAG, "source_stop running=${isRunning()} analysisFrames=$analysisFrameCount")
    val wasRunning = isRunning()
    surfaceReleased = CountDownLatch(1)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    camera?.let {
      cameraProvider.unbindAll()
      camera = null
    }
    preview.surfaceProvider = null
    imageAnalysis?.clearAnalyzer()
    imageAnalysis = null
    analysisExecutor?.shutdown()
    analysisExecutor = null
    if (wasRunning) surfaceReleased.await(500L, TimeUnit.MILLISECONDS)
    surface?.release()
    surface = null
    executor?.shutdown()
    executor = null
    Log.i(CAMERA_SOURCE_TAG, "source_stop_done")
  }

  override fun release() {
    Log.i(CAMERA_SOURCE_TAG, "source_release running=${isRunning()}")
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }

  override fun isRunning() = camera != null

  fun switchCamera() {
    surfaceTexture?.let {
      stop()
      facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
      cameraSelectorBuilder = CameraSelector.Builder()
      start(it)
    }
  }

    @OptIn(ExperimentalCamera2Interop::class)
    fun openCamera(info: CameraInfo) {
    surfaceTexture?.let { surface ->
      stop()
      facing = info.lensFacing
      cameraSelectorBuilder = CameraSelector.Builder().apply {
        addCameraFilter { cameras ->
          cameras.filter { it == info }
        }
      }
      start(surface)
    }
  }

  fun getCameraFacing() = facing

  fun getCameraResolutions(facing: CameraHelper.Facing): List<Size> {
    val camera2 = Camera2Source(context)
    return camera2.getCameraResolutions(facing)
  }

  fun setExposure(level: Int) {
    camera?.cameraControl?.setExposureCompensationIndex(level)
  }

  fun getExposure(): Int {
    return if (isRunning()) camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0 else 0
  }

  fun enableLantern() {
    if (isRunning()) camera?.cameraControl?.enableTorch(true)
  }

  fun disableLantern() {
    if (isRunning()) camera?.cameraControl?.enableTorch(false)
  }

  fun isLanternEnabled(): Boolean {
    return if (isRunning()) camera?.cameraInfo?.torchState?.value == TorchState.ON else false
  }

  fun enableAutoFocus(): Boolean {
    if (isRunning()) {
      autoFocusEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      return true
    }
    autoFocusEnabled = false
    return false
  }

  fun disableAutoFocus(): Boolean {
    if (isRunning()) {
      autoFocusEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
      return true
    }
    return false
  }

  fun isAutoFocusEnabled() = autoFocusEnabled

  fun tapToFocus(view: View, event: MotionEvent): Boolean {
    if (isRunning()) {
      autoFocusEnabled = true
      val factory = SurfaceOrientedMeteringPointFactory(view.width.toFloat(), view.height.toFloat())
      val autoFocusPoint = factory.createPoint(event.x, event.y)
      val autoFocusAction = FocusMeteringAction.Builder(
        autoFocusPoint,
        getMeteringFlag()
      ).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      return true
    }
    autoFocusEnabled = false
    return false
  }

  @JvmOverloads
  fun setZoom(event: MotionEvent, delta: Float = 0.1f) {
    if (isRunning()) {
      if (event.pointerCount < 2 || event.action != MotionEvent.ACTION_MOVE) return
      val currentFingerSpacing = CameraHelper.getFingerSpacing(event)
      if (currentFingerSpacing > fingerSpacing) {
        val zoom = getZoom() + delta
        camera?.cameraControl?.setZoomRatio(zoom)
      } else if (currentFingerSpacing < fingerSpacing) {
        val zoom = getZoom() - delta
        camera?.cameraControl?.setZoomRatio(zoom)
      }
      fingerSpacing = currentFingerSpacing

    }
  }

  fun setZoom(level: Float) {
    if (isRunning()) camera?.cameraControl?.setZoomRatio(level)
  }

  fun getZoomRange(): Range<Float> {
    val zoomState = camera?.cameraInfo?.zoomState?.value ?: return Range(0f, 0f)
    return Range(zoomState.minZoomRatio, zoomState.maxZoomRatio)
  }

  fun getZoom(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0f

  fun enableAutoExposure(): Boolean {
    return if (isRunning()) {
      autoExposureEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      true
    } else {
      autoExposureEnabled = false
      false
    }
  }

  fun disableAutoExposure() {
    if (isRunning()) {
      autoExposureEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
    }
  }

  fun isAutoExposureEnabled() = autoExposureEnabled

  /**
   * @param format 1 YUV420, 2 RGBA
   */
  @OptIn(ExperimentalGetImage::class)
  fun addImageListener(width: Int, height: Int, format: Int, autoClose: Boolean, listener: ImageCallback) {
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    val imageAnalysis = ImageAnalysis.Builder()
      .setResolutionSelector(
        ResolutionSelector.Builder()
          .setResolutionStrategy(
            ResolutionStrategy(
              optimalResolution,
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
          ).build())
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
      .setOutputImageFormat(format)
      .build()
    val cameraSelector = cameraSelectorBuilder
      .requireLensFacing(this.facing)
      .build()
    cameraProvider.unbindAll()
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
      proxy.image?.let {
        listener.onImageAvailable(it)
        if (autoClose) proxy.close()
      }
    }
  }

  fun removeImageListener() {
    cameraProvider.unbindAll()
    val cameraSelector = cameraSelectorBuilder
      .requireLensFacing(this.facing)
      .build()
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
  }

  fun enableAutoWhiteBalance(): Boolean {
    return if (isRunning()) {
      autoWhiteBalanceEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      true
    } else {
      autoWhiteBalanceEnabled = false
      false
    }
  }

  fun disableAutoWhiteBalance() {
    if (isRunning()) {
      autoWhiteBalanceEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
    }
  }

  fun isAutoWhiteBalanceEnabled() = autoWhiteBalanceEnabled

  private fun getMeteringFlag(): Int {
    var flags = 0
    if (autoFocusEnabled) flags = flags or FocusMeteringAction.FLAG_AF
    if (autoExposureEnabled) flags = flags or FocusMeteringAction.FLAG_AE
    if (autoWhiteBalanceEnabled) flags = flags or FocusMeteringAction.FLAG_AWB
    return flags
  }

  /**
   * Set the required resolution for the camera.
   * Must be called before prepareVideo or changeVideoSource. Otherwise it will be ignored.
   */
  fun setRequiredResolution(size: Size?) {
    requiredSize = size
  }
}