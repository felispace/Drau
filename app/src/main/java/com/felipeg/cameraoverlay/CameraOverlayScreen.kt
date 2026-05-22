package com.felipeg.cameraoverlay

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import java.io.OutputStream
import kotlin.math.roundToInt

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Drau",
                color = Color.Black,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dibuja con precisión",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraOverlayScreen() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    when {
        cameraPermissionState.status.isGranted -> {
            CameraWithOverlay()
        }
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionRationale(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
        }
        else -> {
            PermissionDenied()
        }
    }
}

@Composable
fun CameraWithOverlay() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var overlayImageUri by remember { mutableStateOf<Uri?>(null) }
    var overlayOpacity by remember { mutableFloatStateOf(0.5f) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var overlayScale by remember { mutableFloatStateOf(1f) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlayRotation by remember { mutableFloatStateOf(0f) }

    // New states
    var flashEnabled by remember { mutableStateOf(false) }
    var imageLocked by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(true) }
    var screenLocked by remember { mutableStateOf(false) }
    var timelapseActive by remember { mutableStateOf(false) }
    var timelapseCount by remember { mutableIntStateOf(0) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        overlayImageUri = uri
        overlayScale = 1f
        overlayOffset = Offset.Zero
        overlayRotation = 0f
        imageLocked = false
    }

    // Timelapse: capture screenshot every 3 seconds
    LaunchedEffect(timelapseActive) {
        if (timelapseActive) {
            while (timelapseActive) {
                delay(3000)
                if (timelapseActive) {
                    captureScreen(view, context, timelapseCount)
                    timelapseCount++
                }
            }
        }
    }

    // Reset timelapse count when stopped
    DisposableEffect(timelapseActive) {
        onDispose {
            if (!timelapseActive) timelapseCount = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                        cameraRef = camera
                        cameraError = false
                        // Apply flash state
                        if (camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(flashEnabled)
                        }
                    } catch (_: Exception) {
                        cameraError = true
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Camera error placeholder
        if (cameraError && overlayImageUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cámara no disponible",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Overlay image
        overlayImageUri?.let { uri ->
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(true)
                    .build()
            )

            val transformableState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                if (!imageLocked) {
                    overlayScale = (overlayScale * zoomChange).coerceIn(0.2f, 5f)
                    overlayOffset = Offset(
                        x = overlayOffset.x + offsetChange.x,
                        y = overlayOffset.y + offsetChange.y
                    )
                    overlayRotation += rotationChange
                }
            }

            Image(
                painter = painter,
                contentDescription = "Overlay image",
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(overlayOffset.x.roundToInt(), overlayOffset.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = overlayScale,
                        scaleY = overlayScale,
                        rotationZ = overlayRotation
                    )
                    .alpha(overlayOpacity)
                    .then(
                        if (!imageLocked) Modifier.transformable(state = transformableState)
                        else Modifier
                    ),
                contentScale = ContentScale.Fit
            )
        }

        // Screen lock overlay
        if (screenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // Tap to unlock area at top center
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { screenLocked = false },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Desbloquear pantalla",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toca para desbloquear",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            return@Box
        }

        // Left side menu (collapsible)
        // Toggle arrow button
        IconButton(
            onClick = { menuExpanded = !menuExpanded },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (menuExpanded) 52.dp else 0.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
        ) {
            Icon(
                imageVector = if (menuExpanded) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = if (menuExpanded) "Ocultar menú" else "Mostrar menú",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Side menu icons
        AnimatedVisibility(
            visible = menuExpanded,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .offset(x = 0.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Flash toggle
                IconButton(
                    onClick = {
                        flashEnabled = !flashEnabled
                        cameraRef?.let { camera ->
                            if (camera.cameraInfo.hasFlashUnit()) {
                                camera.cameraControl.enableTorch(flashEnabled)
                            }
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (flashEnabled) "Flash encendido" else "Flash apagado",
                        tint = if (flashEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Lock/unlock image
                IconButton(
                    onClick = { imageLocked = !imageLocked },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (imageLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (imageLocked) "Imagen bloqueada" else "Imagen desbloqueada",
                        tint = if (imageLocked) Color(0xFF4FC3F7) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Screen lock
                IconButton(
                    onClick = { screenLocked = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenLockPortrait,
                        contentDescription = "Bloquear pantalla",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Flip camera
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Cambiar cámara",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Timelapse
                IconButton(
                    onClick = {
                        timelapseActive = !timelapseActive
                        if (timelapseActive) {
                            Toast.makeText(context, "Timelapse iniciado (cada 3s)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Timelapse detenido ($timelapseCount capturas)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timelapse",
                        tint = if (timelapseActive) Color(0xFF4CAF50) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Remove overlay
                if (overlayImageUri != null) {
                    IconButton(
                        onClick = { overlayImageUri = null },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Quitar imagen",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Timelapse indicator
        if (timelapseActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REC · $timelapseCount",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Opacity slider
            if (overlayImageUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opacidad: ${(overlayOpacity * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = overlayOpacity,
                        onValueChange = { overlayOpacity = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Gallery picker button
            FloatingActionButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                containerColor = Color.White.copy(alpha = 0.9f),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Seleccionar imagen de galería",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun captureScreen(view: View, context: Context, @Suppress("UNUSED_PARAMETER") count: Int) {
    try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val activity = context as? ComponentActivity
        val window: Window? = activity?.window

        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopy.request(
                window,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        saveBitmapToGallery(context, bitmap, "Drau_timelapse_${System.currentTimeMillis()}")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            @Suppress("DEPRECATION")
            view.isDrawingCacheEnabled = true
            @Suppress("DEPRECATION")
            val cacheBitmap = view.drawingCache
            if (cacheBitmap != null) {
                val copy = cacheBitmap.copy(Bitmap.Config.ARGB_8888, false)
                saveBitmapToGallery(context, copy, "Drau_timelapse_${System.currentTimeMillis()}")
            }
            @Suppress("DEPRECATION")
            view.isDrawingCacheEnabled = false
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error al capturar", Toast.LENGTH_SHORT).show()
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Drau")
        }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        val stream: OutputStream? = context.contentResolver.openOutputStream(it)
        stream?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

@Composable
fun PermissionRationale(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Drau",
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Se necesita acceso a la cámara",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Drau necesita acceso a tu cámara para funcionar. Por favor, concede el permiso.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            FloatingActionButton(
                onClick = onRequestPermission,
                containerColor = Color.Black
            ) {
                Text(
                    text = "Conceder permiso",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionDenied() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Drau",
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Permiso de cámara requerido",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Por favor, habilita el permiso de cámara en los ajustes de tu dispositivo para usar Drau.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
