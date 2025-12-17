package com.example.avatarbot_mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import okhttp3.*
import java.io.IOException

// === CONSTANTE DE CONFIGURAÇÃO ===
const val API_BASE_URL = "http://avatarbot.us-east-1.elasticbeanstalk.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configura modo imersivo (Tela Cheia)
        hideSystemUI()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RobotAppScreen()
                }
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// === LÓGICA DO APP E UI ===

@Composable
fun RobotAppScreen() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("RobotPrefs", Context.MODE_PRIVATE)

    // --- PERMISSÕES (Câmera & Mic) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
        )
    }

    // --- ESTADOS ---
    var savedThingCode by remember {
        mutableStateOf(sharedPrefs.getString("thing_code", null))
    }

    var activeRoomUrl by remember { mutableStateOf<String?>(null) }
    var lastCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // --- FUNÇÕES AUXILIARES ---
    fun saveCode(code: String) {
        sharedPrefs.edit().putString("thing_code", code).apply()
        savedThingCode = code
    }

    fun resetCode() {
        lastCode = savedThingCode ?: ""
        sharedPrefs.edit().clear().apply()
        savedThingCode = null
        Toast.makeText(context, "Configuration reset!", Toast.LENGTH_SHORT).show()
    }

    // Função unificada para encerrar a sessão (Visual + Backend)
    val stopSession = {
        activeRoomUrl = null // 1. Fecha a tela imediatamente
        if (savedThingCode != null) {
            endRobotSession(savedThingCode!!) // 2. Avisa o backend para ficar OFFLINE
        }
    }

    // --- RENDERIZAÇÃO ---
    if (activeRoomUrl != null) {

        // LOOP DE HEARTBEAT (Envia sinal a cada 5s enquanto a tela estiver aberta)
        LaunchedEffect(activeRoomUrl) {
            while (true) {
                if (savedThingCode != null) {
                    sendHeartbeat(savedThingCode!!)
                }
                delay(5000)
            }
        }

        // Lida com o botão "Voltar" físico do Android
        BackHandler {
            stopSession()
        }

        // Exibe a WebView com a chamada
        VideoWebView(
            url = activeRoomUrl!!,
            onClose = { stopSession() }
        )

    } else {
        // Exibe o Menu Principal / Configuração
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to Backend...")
            } else {
                if (savedThingCode == null) {
                    ConfigScreen(
                        initialValue = lastCode,
                        onSave = { saveCode(it) }
                    )
                } else {
                    ReadyScreen(
                        thingCode = savedThingCode!!,
                        onStart = {
                            isLoading = true
                            startRobotSession(context, savedThingCode!!) { url ->
                                isLoading = false
                                if (url != null) {
                                    activeRoomUrl = url
                                }
                            }
                        },
                        onReset = { resetCode() }
                    )
                }
            }
        }
    }
}

// === COMPONENTE WEBVIEW (WebRTC) ===
@Composable
fun VideoWebView(url: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }
                    // Auto-aceita permissões de mídia (Câmera/Mic)
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.grant(request.resources)
                        }
                    }
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }
        )
    }
}

// === TELAS DE CONFIGURAÇÃO E READY ===

@Composable
fun ConfigScreen(initialValue: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    Text("Configure Robot", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("ThingCode") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = { if (text.isNotBlank()) onSave(text.trim()) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save Configuration") }
}

@Composable
fun ReadyScreen(thingCode: String, onStart: () -> Unit, onReset: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var resetTriggered by remember { mutableStateOf(false) }
    var showResetUI by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (showResetUI) 1f else 0f,
        animationSpec = tween(durationMillis = 5000),
        label = "ResetProgress"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            resetTriggered = false
            delay(2000)
            showResetUI = true
            delay(5000)
            resetTriggered = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onReset()
        } else {
            showResetUI = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(thingCode, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(if (showResetUI && !resetTriggered) "Hold 5s to Reset..." else "Ready to start", fontSize = 12.sp, color = if (showResetUI) Color.Red else Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { if (!resetTriggered) onStart() },
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            modifier = Modifier.fillMaxWidth(0.8f).height(80.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("START ROBOT 🚀", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (showResetUI && !resetTriggered) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = Color.Red, trackColor = Color.Transparent)
                }
            }
        }
    }
}

// Helper para detectar botão pressionado
@Composable
fun androidx.compose.foundation.interaction.InteractionSource.collectIsPressedAsState(): State<Boolean> {
    val isPressed = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed.value = true
                is PressInteraction.Release -> isPressed.value = false
                is PressInteraction.Cancel -> isPressed.value = false
            }
        }
    }
    return isPressed
}

// === FUNÇÕES DE REDE (Networking) ===

// 1. Iniciar Sessão (Cria Sala)
fun startRobotSession(context: Context, thingCode: String, onComplete: (String?) -> Unit) {
    val url = "$API_BASE_URL/api/Bot/createRoom/$thingCode"

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .post(RequestBody.create(null, ByteArray(0)))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                onComplete(null)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        // Limpa a string da URL (remove aspas extras que o JSON pode trazer)
                        val roomUrl = body.trim().removeSurrounding("\"")
                        onComplete(roomUrl)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                        onComplete(null)
                    }
                } else {
                    Toast.makeText(context, "API Failed: ${response.code}", Toast.LENGTH_LONG).show()
                    onComplete(null)
                }
            }
        }
    })
}

// 2. Heartbeat (Mantém Online)
fun sendHeartbeat(thingCode: String) {
    val url = "$API_BASE_URL/api/Bot/heartbeat/$thingCode"

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .post(RequestBody.create(null, ByteArray(0)))
        .build()

    // Fire-and-forget (ignora erros de rede temporários)
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("Heartbeat fail: ${e.message}")
        }
        override fun onResponse(call: Call, response: Response) { }
    })
}

// 3. Encerrar Sessão (Fica Offline)
fun endRobotSession(thingCode: String) {
    val url = "$API_BASE_URL/api/Bot/endCall/$thingCode"

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .post(RequestBody.create(null, ByteArray(0)))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("EndCall fail: ${e.message}")
        }
        override fun onResponse(call: Call, response: Response) {
            println("EndCall success: ${response.code}")
        }
    })
}