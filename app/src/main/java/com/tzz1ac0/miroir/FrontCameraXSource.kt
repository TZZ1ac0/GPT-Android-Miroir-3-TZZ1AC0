package com.tzz1ac0.miroir

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import com.pedro.encoder.input.sources.video.VideoSource
import java.util.concurrent.Executor

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
    private var outputSurface: Surface? = null
    private var starting = false
    private var startGeneration = 0

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        return width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0
    }

    private val diagnosticHandler = Handler(Looper.getMainLooper())

    private fun logTextureTransform(
        trigger: String,
        texture: SurfaceTexture,
        boundCamera: Camera?
    ) {
        // SurfaceTexture only exposes a useful timestamp/transform after its consumer
        // has latched a frame. At CAMERA_OPEN this is often still timestamp 0.
        val timestampNs = runCatching { texture.timestamp }.getOrDefault(0L)
        val matrix = FloatArray(16)
        if (timestampNs > 0L) {
            runCatching { texture.getTransformMatrix(matrix) }
                .onFailure {
                    cameraDiag("CAMERAX_TEXTURE_DIAGNOSTIC trigger=$trigger textureTimestampNs=$timestampNs matrixReadError=${it.message}")
                }
        }
        val rotation = runCatching {
            boundCamera?.cameraInfo?.getSensorRotationDegrees(Surface.ROTATION_0)
        }.getOrNull()
        cameraDiag(
            "CAMERAX_TEXTURE_DIAGNOSTIC trigger=$trigger frameReady=${timestampNs > 0L} " +
                "textureTimestampNs=$timestampNs sourceSize=${width}x${height} " +
                "targetRotation=ROTATION_90 sensorRotationForTarget=$rotation " +
                "matrix=[${matrix.joinToString(",") { it.toString() }}]"
        )
    }

    private fun scheduleTextureDiagnostics(
        texture: SurfaceTexture,
        boundCamera: Camera?,
        generation: Int
    ) {
        // Sample later without replacing RootEncoder's SurfaceTexture listener or
        // calling updateTexImage ourselves, both of which could disturb rendering.
        listOf(250L, 750L, 1500L).forEach { delayMs ->
            diagnosticHandler.postDelayed({
                if (generation == startGeneration &&
                    this.surfaceTexture === texture &&
                    camera === boundCamera
                ) {
                    logTextureTransform("AFTER_OPEN_${delayMs}MS", texture, boundCamera)
                }
            }, delayMs)
        }
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        cameraDiag("CAMERAX_START_REQUEST starting=$starting cameraBound=${camera != null} generation=$startGeneration sourceSize=${width}x${height}")
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
                // Keep CameraX's input stream at the encoder's 4:3 aspect ratio.
                // setTargetResolution() is only a preference and can select a square
                // stream on some front cameras, which then gets stretched by the 4:3
                // GL encoder. Prefer 4:3 while allowing CameraX to choose a supported size.
                // EXPERIMENT: keep CameraX's target rotation aligned with the phone's
                // portrait display. The previous ROTATION_90 target produced a stable
                // quarter-turn SurfaceTexture matrix, while RootEncoder renders that
                // texture directly into a fixed landscape 640x480 FBO. This may be the
                // source of the vertical compression; verify the real device output.
                val builder = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(
                                AspectRatioStrategy(
                                    AspectRatio.RATIO_4_3,
                                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                                )
                            )
                            .build()
                    )
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
                cameraDiag("CAMERAX_PREVIEW_CONFIG targetRotation=ROTATION_0 requestedAspect=4:3 sourceSize=${width}x${height}")
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
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview
                )
                camera?.cameraInfo?.cameraState?.observe(this) { state ->
                    cameraDiag("CAMERAX_STATE type=${state.type} error=${state.error?.cause?.message}")
                    if (state.type == androidx.camera.core.CameraState.Type.OPEN) {
                        // Sample after OPEN too: the GL consumer may not have latched a frame yet.
                        logTextureTransform("CAMERA_OPEN", surfaceTexture, camera)
                        scheduleTextureDiagnostics(surfaceTexture, camera, generation)
                    }
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
        if (provider != null && preview != null) {
            runCatching { provider.unbind(preview) }
        }
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
