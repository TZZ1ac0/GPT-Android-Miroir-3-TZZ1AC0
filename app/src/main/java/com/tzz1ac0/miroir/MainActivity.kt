package com.tzz1ac0.miroir

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.tzz1ac0.miroir.camera.AutoExposureCameraXSource
import com.pedro.library.generic.GenericStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(activity = this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraScreen(activity: ComponentActivity) {
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Scaffold(topBar = { TopAppBar(title = { Text("Miroir Android") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasCameraPermission) {
                RootEncoderCameraPreview(
                    activity = activity,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text("L’autorisation caméra est nécessaire pour démarrer la capture.")
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                    Text("Autoriser la caméra")
                }
            }
        }
    }
}

@Composable
private fun RootEncoderCameraPreview(
    activity: ComponentActivity,
    modifier: Modifier = Modifier
) {
    var status by remember { mutableStateOf("Initialisation de RootEncoder…") }
    var prepared by remember { mutableStateOf(false) }

    val cameraSource = remember { AutoExposureCameraXSource(activity.applicationContext) }
    val stream = remember {
        GenericStream(
            activity.applicationContext,
            object : ConnectChecker {
                override fun onConnectionStarted(url: String) = Unit
                override fun onConnectionSuccess() = Unit
                override fun onConnectionFailed(reason: String) = Unit
                override fun onDisconnect() = Unit
                override fun onAuthError() = Unit
                override fun onAuthSuccess() = Unit
                override fun onStreamingStats(report: StreamingStatsReport) = Unit
            },
            cameraSource,
            NoAudioSource()
        )
    }

    DisposableEffect(stream) {
        try {
            // Let RootEncoder update the preview orientation when the device rotates.
            stream.getGlInterface().autoHandleOrientation = true
            prepared = stream.prepareVideo(
                width = 640,
                height = 480,
                bitrate = 1_200_000,
                fps = 30
            )
            status = if (prepared) {
                "Source CameraX de RootEncoder prête"
            } else {
                "RootEncoder n’a pas pu préparer l’encodeur vidéo"
            }
        } catch (error: Exception) {
            status = "Échec d’initialisation : ${error.localizedMessage ?: "erreur vidéo"}"
            prepared = false
        }

        onDispose {
            if (stream.isOnPreview) stream.stopPreview()
            stream.release()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (prepared) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        // Let RootEncoder manage the SurfaceView lifecycle and start its preview
                        // only once the underlying surface is valid.
                        stream.startPreview(this, true)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                // RootEncoder's callback was registered first and starts CameraX.
                                post {
                                    if (cameraSource.isRunning()) {
                                        cameraSource.switchCamera()
                                        val aeRequested = cameraSource.enableAutoExposure()
                                        status = if (aeRequested) {
                                            "Caméra avant active — mode AE Camera2 demandé, mesure centrale activée"
                                        } else {
                                            "Caméra avant active — demande AE non appliquée (caméra pas encore démarrée)"
                                        }
                                    } else {
                                        status = "Aperçu RootEncoder démarré ; démarrage CameraX en cours"
                                    }
                                }
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                        })
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
        Text(status)
        Text("RootEncoder est intégré ; aucune diffusion RTSP n’est démarrée dans cette étape.")
    }
}

fun greetingFor(name: String = "World"): String = "Hello $name!"
