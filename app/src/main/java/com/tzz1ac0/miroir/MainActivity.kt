package com.tzz1ac0.miroir

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

private const val DEFAULT_RTSP_PORT = 8554
private const val STREAM_WIDTH = 640
private const val STREAM_HEIGHT = 480
private const val STREAM_FPS = 30
private const val STREAM_BITRATE = 1_500_000

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HelloWorldScreen(onQuit = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelloWorldScreen(onQuit: () -> Unit = {}) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var rtspSubmenuExpanded by remember { mutableStateOf(false) }
    var showFrontCamera by remember { mutableStateOf(false) }
    var cameraRequested by remember { mutableStateOf(false) }
    var rtspEnabled by remember { mutableStateOf(false) }
    var rtspStartRequested by remember { mutableStateOf(false) }
    var showRtspConfiguration by remember { mutableStateOf(false) }
    var rtspPort by remember { mutableStateOf(DEFAULT_RTSP_PORT) }
    var rtspError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (rtspStartRequested) {
            rtspStartRequested = false
            if (granted) {
                showFrontCamera = false
                rtspError = null
                rtspEnabled = true
            } else {
                rtspError = "L'autorisation caméra est nécessaire pour activer RTSP."
                showFrontCamera = false
            }
        } else {
            showFrontCamera = granted
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Miroir Android.") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Ouvrir le menu"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                                rtspSubmenuExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Caméra avant") },
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    cameraRequested = true
                                    if (cameraPermissionGranted) {
                                        rtspEnabled = false
                                        showFrontCamera = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = "RTSP  ›") },
                                onClick = { rtspSubmenuExpanded = true }
                            )
                            DropdownMenuItem(
                                text = { Text(text = "Quitter") },
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    onQuit()
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = rtspSubmenuExpanded,
                            onDismissRequest = {
                                rtspSubmenuExpanded = false
                                menuExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Activer RTSP") },
                                enabled = !rtspEnabled,
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    cameraRequested = true
                                    if (cameraPermissionGranted) {
                                        showFrontCamera = false
                                        rtspError = null
                                        rtspEnabled = true
                                    } else {
                                        rtspStartRequested = true
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Désactiver RTSP") },
                                enabled = rtspEnabled,
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    rtspEnabled = false
                                    showFrontCamera = cameraPermissionGranted
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Configurer RTSP") },
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    showRtspConfiguration = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                rtspEnabled && cameraPermissionGranted -> {
                    RtspCameraPreview(
                        port = rtspPort,
                        onServerError = { message ->
                            // Keep the menu state consistent with the actual server state.
                            rtspError = message
                            rtspEnabled = false
                            showFrontCamera = false
                        }
                    )
                }
                rtspError != null -> {
                    Text(text = rtspError.orEmpty(), color = Color.White)
                }
                showFrontCamera && cameraPermissionGranted -> {
                    FrontCameraPreview(modifier = Modifier.fillMaxSize())
                }
                cameraRequested && !cameraPermissionGranted -> {
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text("Autoriser l'accès à la caméra")
                    }
                }
                else -> {
                    Text(text = greetingFor(), color = Color.White)
                }
            }
        }
    }

    if (showRtspConfiguration) {
        RtspConfigurationDialog(
            initialPort = rtspPort,
            onDismiss = { showRtspConfiguration = false },
            onSave = { newPort ->
                rtspPort = newPort
                showRtspConfiguration = false
            }
        )
    }
}

@Composable
private fun RtspConfigurationDialog(
    initialPort: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var portText by remember(initialPort) { mutableStateOf(initialPort.toString()) }
    val port = portText.toIntOrNull()
    val portIsValid = port != null && port in 1..65535
    val clientUrl = "rtsp://${getLocalIpv4Address()}:${if (portIsValid) port else initialPort}/"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurer RTSP") },
        text = {
            Column {
                Text("Port du serveur")
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !portIsValid,
                    supportingText = {
                        if (!portIsValid) Text("Saisir un port compris entre 1 et 65535.")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Adresse du flux")
                Text(clientUrl, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Commande client (VLC)")
                Text("vlc $clientUrl", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = portIsValid,
                onClick = { onSave(port!!) }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun RtspCameraPreview(
    port: Int,
    onServerError: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val openGlView = remember(context) { OpenGlView(context) }
    var startupMessage by remember(port) { mutableStateOf<String?>(null) }

    DisposableEffect(context, openGlView, port) {
        var disposed = false
        var server: RtspServerCamera2? = null
        var started = false
        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) = Unit
            override fun onConnectionSuccess() = Unit
            override fun onConnectionFailed(reason: String) {
                if (!disposed) startupMessage = "Connexion RTSP refusée : $reason"
            }
            override fun onNewBitrate(bitrate: Long) = Unit
            override fun onDisconnect() = Unit
            override fun onAuthError() = Unit
            override fun onAuthSuccess() = Unit
        }

        fun startWhenSurfaceIsReady() {
            if (disposed || started || !openGlView.holder.surface.isValid) return
            started = true
            try {
                val activeServer = RtspServerCamera2(openGlView, checker, port)
                server = activeServer
                // Use Android's current camera/display orientation for both preview and encoded stream.
                val cameraRotation = CameraHelper.getCameraOrientation(context)
                val videoPrepared = activeServer.prepareVideo(
                    STREAM_WIDTH,
                    STREAM_HEIGHT,
                    STREAM_FPS,
                    STREAM_BITRATE,
                    2,
                    cameraRotation
                )
                if (!videoPrepared) {
                    throw IllegalStateException("L'encodeur vidéo ne peut pas être initialisé.")
                }
                activeServer.getGlInterface().setIsStreamHorizontalFlip(true)
                activeServer.startPreview(
                    CameraHelper.Facing.FRONT,
                    STREAM_WIDTH,
                    STREAM_HEIGHT,
                    STREAM_FPS,
                    cameraRotation
                )
                // Camera2 opens asynchronously; the capture session may not exist yet here.
                activeServer.startStream()
                val exposureHandler = Handler(Looper.getMainLooper())
                var exposureAttempts = 0
                val enableAutoExposureWhenReady = object : Runnable {
                    override fun run() {
                        if (disposed || server !== activeServer) return
                        if (activeServer.enableAutoExposure() || activeServer.isAutoExposureEnabled) {
                            startupMessage = null
                        } else if (exposureAttempts++ < 20) {
                            exposureHandler.postDelayed(this, 100L)
                        } else {
                            startupMessage = "Attention : la caméra n'a pas confirmé l'activation de l'exposition automatique."
                        }
                    }
                }
                exposureHandler.post(enableAutoExposureWhenReady)
            } catch (exception: Exception) {
                runCatching { server?.stopStream() }
                runCatching { server?.stopPreview() }
                server = null
                started = false
                onServerError(
                    "Impossible de démarrer RTSP sur le port $port. " +
                        (exception.message ?: "Vérifie le port et l'autorisation caméra.")
                )
            }
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startWhenSurfaceIsReady()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                startWhenSurfaceIsReady()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                started = false
                runCatching { server?.stopStream() }
                runCatching { server?.stopPreview() }
                server = null
            }
        }
        openGlView.holder.addCallback(surfaceCallback)
        startWhenSurfaceIsReady()

        onDispose {
            disposed = true
            openGlView.holder.removeCallback(surfaceCallback)
            runCatching { server?.stopStream() }
            runCatching { server?.stopPreview() }
            server = null
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { openGlView },
            modifier = Modifier.fillMaxSize()
        )
        startupMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            )
        }
    }
}

private fun getLocalIpv4Address(): String {
    return try {
        val addresses = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { address ->
                        Triple(
                            address.hostAddress.orEmpty(),
                            if (networkInterface.name.startsWith("wlan", ignoreCase = true)) 0 else 1,
                            networkInterface.name
                        )
                    }
            }
            .sortedBy { it.second }
        addresses.firstOrNull()?.first ?: "IP_DU_TELEPHONE"
    } catch (_: Exception) {
        "IP_DU_TELEPHONE"
    }
}

@Composable
private fun FrontCameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // FIT_CENTER preserves the complete camera field of view instead of cropping it.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                val previewBuilder = CameraPreview.Builder()
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON
                    )
                val cameraPreview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    cameraPreview
                )
                cameraError = null
            } catch (exception: Exception) {
                cameraError = "Impossible de démarrer la caméra avant."
            }
        }, executor)

        onDispose {
            disposed = true
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProvider?.unbindAll() }
            }
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        cameraError?.let { message ->
            Text(text = message, color = Color.White)
        }
    }
}

fun greetingFor(name: String = "World"): String = "Hello $name!"

@Preview(showBackground = true)
@Composable
private fun HelloWorldPreview() {
    MaterialTheme {
        HelloWorldScreen()
    }
}
