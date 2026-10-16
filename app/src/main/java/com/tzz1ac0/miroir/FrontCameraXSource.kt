package com.tzz1ac0.miroir

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pedro.encoder.input.sources.video.VideoSource
import java.util.concurrent.Executor

/**
 * A front-camera CameraX source rendered by RootEncoder into both the local preview
 * and the RTSP encoder. The same SurfaceTexture frames feed both outputs.
 */
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

    override fun start(surfaceTexture: SurfaceTexture) {
        if (starting || camera != null) return

        this.surfaceTexture = surfaceTexture
        surfaceTexture.setDefaultBufferSize(width, height)
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        starting = true
        val generation = ++startGeneration

        cameraProviderFuture.addListener({
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
                val surface = Surface(surfaceTexture)
                outputSurface = surface
                preview.setSurfaceProvider { request ->
                    request.provideSurface(surface, mainExecutor) {
                        if (outputSurface === surface) {
                            outputSurface = null
                            surface.release()
                        }
                    }
                }

                provider.unbindAll()
                cameraProvider = provider
                previewUseCase = preview
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview
                )
                starting = false
            } catch (exception: Exception) {
                starting = false
                onCameraError(
                    "Impossible de démarrer la caméra avant : " +
                        (exception.message ?: "erreur CameraX inconnue")
                )
            }
        }, mainExecutor)
    }

    override fun stop() {
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
    }

    override fun release() {
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
