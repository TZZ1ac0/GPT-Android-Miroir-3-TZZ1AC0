package com.tzz1ac0.miroir

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Miroir Android") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasCameraPermission) {
                CameraPreview(activity = activity, modifier = Modifier.weight(1f))
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
private fun CameraPreview(activity: ComponentActivity, modifier: Modifier = Modifier) {
    val previewView = remember {
        PreviewView(activity).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var frameCount by remember { mutableIntStateOf(0) }
    var cameraStatus by remember { mutableStateOf("Initialisation de la caméra…") }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxWidth()
    )

    Text(cameraStatus)
    Text("Images reçues : $frameCount")

    DisposableEffect(activity, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                var receivedFrames = 0
                imageAnalysis.setAnalyzer(analysisExecutor) { image ->
                    receivedFrames += 1
                    if (receivedFrames % 15 == 0) {
                        val count = receivedFrames
                        activity.runOnUiThread {
                            if (!disposed) {
                                frameCount = count
                                cameraStatus = "Caméra active"
                            }
                        }
                    }
                    image.close()
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                cameraStatus = "Caméra démarrée"
            } catch (error: Exception) {
                cameraStatus = "Échec de démarrage : ${error.localizedMessage ?: "erreur caméra"}"
            }
        }, ContextCompat.getMainExecutor(activity))

        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }
}

fun greetingFor(name: String = "World"): String = "Hello $name!"
