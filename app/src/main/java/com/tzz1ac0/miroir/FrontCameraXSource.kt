package com.tzz1ac0.miroir

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import android.os.SystemClock
import com.pedro.encoder.input.sources.video.VideoSource
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A front-camera CameraX source rendered by RootEncoder into both the local preview
 * and the RTSP encoder. The same SurfaceTexture frames feed both outputs.
 */
private const val CAMERA_DIAG_TAG = "CameraPreviewDiag"

private fun cameraDiag(message: String) {
    Log.d(CAMERA_DIAG_TAG, "t=${SystemClock.elapsedRealtime()}ms $message")
}

internal class FrontCameraXSource(
    context: Context,
    private val onCameraError: (String) -> Unit
) : VideoSource(), LifecycleOwner {

    private val appContext = context.applicationContext
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var analysisExecutor: java.util.concurrent.ExecutorService? = null
    private var outputSurface: Surface? = null
    private var starting = false
    private var startGeneration = 0

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        return width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        cameraDiag("CAMERAX_START_REQUEST starting=$starting cameraBound=${camera != null} generation=$startGeneration")
        if (starting || camera != null) {
            cameraDiag("CAMERAX_START_IGNORED starting=$starting cameraBound=${camera != null}")
            return
        }

        this.surfaceTexture = surfaceTexture
        surfaceTexture.setDefaultBufferSize(width, height)
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        starting = true
        val generation = ++startGeneration

        cameraProviderFuture.addListener({
            cameraDiag("CAMERAX_PROVIDER_LISTENER generation=$generation currentGeneration=$startGeneration lifecycle=${lifecycleRegistry.currentState}")
            if (generation != startGeneration ||
                lifecycleRegistry.currentState != Lifecycle.State.STARTED
            ) {
                return@addListener
            }

            try {
                val provider = cameraProviderFuture.get()
                val builder = Preview.Builder()
                    .setTargetResolution(Size(width, height))
                Camera2Interop.Extender(builder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON
                    )

                val preview = builder.build()

                // Keep an analysis stream bound with Preview. This mirrors the validated
                // CameraX baseline: requesting AE only on Preview was not sufficient on
                // the target device, while AE requested on both use cases converged.
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(width, height))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                Camera2Interop.Extender(analysisBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON
                    )
                val analysis = analysisBuilder.build()
                val analyzerExecutor = Executors.newSingleThreadExecutor()
                analysis.setAnalyzer(analyzerExecutor) { image ->
                    image.close()
                }

                val surface = Surface(surfaceTexture)
                outputSurface = surface
                preview.setSurfaceProvider { request ->
                    cameraDiag(
                        "CAMERAX_SURFACE_REQUEST resolution=${request.resolution.width}x${request.resolution.height} " +
                            "surfaceValid=${surface.isValid} texture=${System.identityHashCode(surfaceTexture)} " +
                            "generation=$generation"
                    )
                    surfaceTexture.setDefaultBufferSize(
                        request.resolution.width,
                        request.resolution.height
                    )
                    request.provideSurface(surface, mainExecutor) { result ->
                        cameraDiag(
                            "CAMERAX_SURFACE_RESULT result=${result.resultCode} " +
                                "surfaceValid=${surface.isValid} generation=$generation"
                        )
                        if (outputSurface === surface) {
                            outputSurface = null
                            surface.release()
                        }
                    }
                }

                cameraProvider = provider
                previewUseCase = preview
                analysisUseCase = analysis
                analysisExecutor = analyzerExecutor
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                camera?.cameraInfo?.cameraState?.observe(this) { state ->
                    cameraDiag("CAMERAX_STATE type=${state.type} error=${state.error?.cause?.message}")
                }
                cameraDiag("CAMERAX_BOUND generation=$generation lifecycle=${lifecycleRegistry.currentState}")
                starting = false
            } catch (exception: Exception) {
                cameraDiag("CAMERAX_START_EXCEPTION ${Log.getStackTraceString(exception)}")
                starting = false
                onCameraError(
                    "Impossible de démarrer la caméra avant : " +
                        (exception.message ?: "erreur CameraX inconnue")
                )
            }
        }, mainExecutor)
    }

    override fun stop() {
        cameraDiag("CAMERAX_STOP starting=$starting cameraBound=${camera != null} lifecycle=${lifecycleRegistry.currentState} generation=$startGeneration")
        startGeneration++
        starting = false

        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        val provider = cameraProvider
        val preview = previewUseCase
        val analysis = analysisUseCase
        if (provider != null) {
            runCatching {
                if (preview != null && analysis != null) {
                    provider.unbind(preview, analysis)
                } else if (preview != null) {
                    provider.unbind(preview)
                }
            }
        }
        analysis?.clearAnalyzer()
        analysisExecutor?.shutdown()
        analysisExecutor = null
        analysisUseCase = null
        camera = null
        previewUseCase = null
        cameraProvider = null
        outputSurface?.release()
        outputSurface = null
        cameraDiag("CAMERAX_STOP_COMPLETE generation=$startGeneration")
    }

    override fun release() {
        cameraDiag("CAMERAX_RELEASE lifecycle=${lifecycleRegistry.currentState}")
        stop()
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    override fun isRunning(): Boolean = starting || camera != null
}
