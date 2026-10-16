package com.tzz1ac0.miroir

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

private const val DEFAULT_RTSP_PORT = 8554
private const val STREAM_WIDTH = 640
private const val STREAM_HEIGHT = 480
private const val STREAM_FPS = 30
private const val STREAM_BITRATE = 1_500_000
private const val CAMERA_DIAG_TAG = "CameraPreviewDiag"

private fun cameraDiag(message: String) {
    Log.d(CAMERA_DIAG_TAG, "t=${SystemClock.elapsedRealtime()}ms $message")
}

/**
 * Diagnostic subclass: tells us whether CameraX frames reach RootEncoder's GL input.
 * The first frame is logged once per SurfaceView surface lifetime, not on every frame.
 */
private class DiagnosticOpenGlView(context: android.content.Context) : OpenGlView(context) {
    private var firstFrameLogged = false

    override fun surfaceCreated(holder: SurfaceHolder) {
        cameraDiag("GL_SURFACE_CREATED valid=${holder.surface.isValid} view=${System.identityHashCode(this)}")
        firstFrameLogged = false
        super.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        cameraDiag("GL_SURFACE_CHANGED valid=${holder.surface.isValid} size=${width}x${height} view=${System.identityHashCode(this)}")
        super.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cameraDiag("GL_SURFACE_DESTROYED view=${System.identityHashCode(this)}")
        super.surfaceDestroyed(holder)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (!firstFrameLogged) {
            firstFrameLogged = true
            cameraDiag("GL_FIRST_FRAME_RECEIVED view=${System.identityHashCode(this)} running=${isRunning}")
        }
        super.onFrameAvailable(surfaceTexture)
    }
}

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
        cameraDiag("PERMISSION_RESULT granted=$granted rtspStartRequested=$rtspStartRequested")
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
                title = {
                    Text(
                        text = if (rtspEnabled) {
                            "Miroir Android (RTSP actif)"
                        } else {
                            "Miroir Android (RTSP inactif)"
                        }
                    )
                },
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
                                text = { Text("Caméra avant") },
                                onClick = {
                                    menuExpanded = false
                                    rtspSubmenuExpanded = false
                                    cameraRequested = true
                                    rtspEnabled = false
                                    if (cameraPermissionGranted) {
                                        showFrontCamera = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("RTSP") },
                                onClick = { rtspSubmenuExpanded = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Quitter") },
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
                                text = { Text("Configurer RTSP...") },
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
                (rtspEnabled || showFrontCamera) && cameraPermissionGranted -> {
                    CameraPreviewWithRtsp(
                        port = rtspPort,
                        rtspEnabled = rtspEnabled,
                        onServerError = { message ->
                            rtspError = message
                            rtspEnabled = false
                            showFrontCamera = false
                        }
                    )
                }
                rtspError != null -> {
                    Text(text = rtspError.orEmpty(), color = Color.White)
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

/**
 * Keeps one CameraX/GL pipeline alive for the on-device preview and optional RTSP output.
 * Toggling RTSP only adds/removes the encoder and server; it does not open another camera.
 */
@Composable
private fun CameraPreviewWithRtsp(
    port: Int,
    rtspEnabled: Boolean,
    onServerError: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val openGlView = remember(context) {
        DiagnosticOpenGlView(context).also {
            cameraDiag("GL_VIEW_CREATED view=${System.identityHashCode(it)} context=${context.javaClass.simpleName}")
        }
    }
    var server by remember(context, port) { mutableStateOf<RtspCameraXServer?>(null) }
    var surfaceAvailable by remember(openGlView) { mutableStateOf(false) }
    var previewStarted by remember(openGlView, port) { mutableStateOf(false) }

    // RootEncoder's OpenGlView is a SurfaceView. Layout completion does not mean
    // its underlying Surface is ready; wait for surfaceCreated before starting.
    DisposableEffect(openGlView) {
        val callback = object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                cameraDiag("APP_SURFACE_CREATED valid=${holder.surface.isValid} view=${System.identityHashCode(openGlView)}")
                surfaceAvailable = holder.surface.isValid
            }

            override fun surfaceChanged(
                holder: android.view.SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                cameraDiag("APP_SURFACE_CHANGED valid=${holder.surface.isValid} size=${width}x${height} view=${System.identityHashCode(openGlView)}")
                surfaceAvailable = holder.surface.isValid
                if (width > 0 && height > 0) {
                    server?.getGlInterface()?.setPreviewResolution(width, height)
                }
            }

            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                cameraDiag("APP_SURFACE_DESTROYED previewStarted=$previewStarted view=${System.identityHashCode(openGlView)}")
                surfaceAvailable = false
                if (previewStarted) {
                    runCatching { server?.stopPreview() }
                    previewStarted = false
                }
            }
        }
        openGlView.holder.addCallback(callback)
        if (openGlView.holder.surface.isValid) surfaceAvailable = true
        onDispose {
            cameraDiag("APP_SURFACE_CALLBACK_DISPOSED view=${System.identityHashCode(openGlView)}")
            openGlView.holder.removeCallback(callback)
        }
    }

    DisposableEffect(context, openGlView, port) {
        var disposed = false
        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) = Unit
            override fun onConnectionSuccess() = Unit
            override fun onConnectionFailed(reason: String) {
                if (!disposed) onServerError("Impossible de démarrer RTSP sur le port $port : $reason")
            }
            override fun onNewBitrate(bitrate: Long) = Unit
            override fun onDisconnect() = Unit
            override fun onAuthError() = Unit
            override fun onAuthSuccess() = Unit
        }

        cameraDiag("SERVER_CREATE port=$port view=${System.identityHashCode(openGlView)}")
        val activeServer = RtspCameraXServer(
            context = context,
            connectChecker = checker,
            port = port,
            onCameraError = { message ->
                if (!disposed) onServerError(message)
            }
        )

        try {
            val videoPrepared = activeServer.prepareVideo(
                STREAM_WIDTH,
                STREAM_HEIGHT,
                STREAM_BITRATE,
                STREAM_FPS,
                2,
                0
            )
            // StreamBase always starts its audio encoder, even when RTSP is video-only.
            // Prepare a silent AAC encoder so startStream() doesn't fail at runtime.
            val audioPrepared = videoPrepared &&
                activeServer.prepareAudio(44_100, true, 64_000)
            cameraDiag("SERVER_PREPARED video=$videoPrepared audio=$audioPrepared port=$port")
            if (!videoPrepared) {
                onServerError("L'encodeur vidéo ne peut pas être initialisé.")
            } else if (!audioPrepared) {
                onServerError("L'encodeur audio silencieux ne peut pas être initialisé.")
            } else {
                server = activeServer
                // Mirror both outputs identically for the front-facing camera.
                activeServer.getGlInterface().setIsPreviewHorizontalFlip(true)
                activeServer.getGlInterface().setIsStreamHorizontalFlip(true)
            }
        } catch (exception: Exception) {
            cameraDiag("SERVER_PREPARE_EXCEPTION ${Log.getStackTraceString(exception)}")
            onServerError(
                "Impossible d'initialiser la caméra/RTSP : " +
                    (exception.message ?: "erreur inconnue")
            )
        }

        onDispose {
            cameraDiag("SERVER_DISPOSE port=$port previewStarted=$previewStarted streaming=${activeServer.isStreaming} view=${System.identityHashCode(openGlView)}")
            disposed = true
            if (server === activeServer) server = null
            runCatching { activeServer.release() }
        }
    }

    // Start only after the actual SurfaceView surface exists, not merely after Compose layout.
    LaunchedEffect(surfaceAvailable, server) {
        val activeServer = server ?: return@LaunchedEffect
        if (!surfaceAvailable || previewStarted) return@LaunchedEffect
        try {
            cameraDiag("START_PREVIEW surfaceAvailable=$surfaceAvailable alreadyStarted=$previewStarted view=${System.identityHashCode(openGlView)}")
            activeServer.startPreview(openGlView, true)
            previewStarted = true
            cameraDiag("START_PREVIEW_RETURNED running=${openGlView.isRunning} view=${System.identityHashCode(openGlView)}")
        } catch (exception: Exception) {
            cameraDiag("START_PREVIEW_EXCEPTION ${Log.getStackTraceString(exception)}")
            onServerError(
                "Impossible de démarrer l'aperçu caméra : " +
                    (exception.message ?: "erreur inconnue")
            )
        }
    }

    LaunchedEffect(rtspEnabled, server) {
        val activeServer = server ?: return@LaunchedEffect
        try {
            cameraDiag("RTSP_EFFECT enabled=$rtspEnabled streaming=${activeServer.isStreaming} previewStarted=$previewStarted")
            if (rtspEnabled && !activeServer.isStreaming) {
                activeServer.startStream("")
            } else if (!rtspEnabled && activeServer.isStreaming) {
                activeServer.stopStream()
            }
        } catch (exception: Exception) {
            onServerError(
                "Impossible de modifier l'état RTSP : " +
                    (exception.message ?: "erreur inconnue")
            )
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { openGlView },
            modifier = Modifier.fillMaxSize()
        )
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

fun greetingFor(name: String = "World"): String = "Hello $name!"

@Preview(showBackground = true)
@Composable
private fun HelloWorldPreview() {
    MaterialTheme {
        HelloWorldScreen()
    }
}
