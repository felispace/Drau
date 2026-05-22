package com.felipeg.cameraoverlay

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment

import android.provider.MediaStore

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help

import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FlipCameraAndroid

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ─── Tool enum for right sidebar ───
enum class ToolPanel { NONE, OPACITY, ADJUST, GRID }

// ─── Menu section for "..." menu ───
enum class MenuSection { NONE, MAIN, ARCHIVO, HERRAMIENTAS, PREFERENCIAS, AYUDA }

// ─── Splash Screen ───
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Drau", color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(8.dp))
            Text("Dibuja con precisión", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Light)
        }
    }
}

// ─── Entry point ───
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraOverlayScreen() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) { SplashScreen { showSplash = false }; return }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) cameraPermissionState.launchPermissionRequest()
    }

    when {
        cameraPermissionState.status.isGranted -> CameraWithOverlay()
        cameraPermissionState.status.shouldShowRationale ->
            PermissionRationale { cameraPermissionState.launchPermissionRequest() }
        else -> PermissionDenied()
    }
}

// ─── Main camera + overlay screen ───
@Composable
fun CameraWithOverlay() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Overlay states ──
    var overlayImageUri by remember { mutableStateOf<Uri?>(null) }
    var overlayOpacity by remember { mutableFloatStateOf(0.5f) }
    var overlayScale by remember { mutableFloatStateOf(1f) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlayRotation by remember { mutableFloatStateOf(0f) }
    var overlayFlipped by remember { mutableStateOf(false) }
    var overlayBrightness by remember { mutableFloatStateOf(0f) }
    var overlayContrast by remember { mutableFloatStateOf(1f) }
    var overlayOutline by remember { mutableStateOf(false) }
    var outlineBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ── Layer 2 ──
    var layer2Uri by remember { mutableStateOf<Uri?>(null) }
    var layer2Opacity by remember { mutableFloatStateOf(0.5f) }
    var layer2Scale by remember { mutableFloatStateOf(1f) }
    var layer2Offset by remember { mutableStateOf(Offset.Zero) }
    var layer2Rotation by remember { mutableFloatStateOf(0f) }
    var activeLayer by remember { mutableIntStateOf(1) }

    // ── Camera states ──
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    // ── UI states ──
    var imageLocked by remember { mutableStateOf(false) }
    var activeToolPanel by remember { mutableStateOf(ToolPanel.NONE) }
    var showGrid by remember { mutableStateOf(false) }
    var gridCount by remember { mutableIntStateOf(9) }
    var menuSection by remember { mutableStateOf(MenuSection.NONE) }
    var darkMode by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Colors based on dark mode
    val uiBg = if (darkMode) Color(0xFF1E1E1E) else Color.White
    val uiFg = if (darkMode) Color.White else Color.Black
    val uiSecondary = if (darkMode) Color(0xFF888888) else Color(0xFFAAAAAA)
    val uiSurface = if (darkMode) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (activeLayer == 2) {
                layer2Uri = uri; layer2Scale = 1f; layer2Offset = Offset.Zero; layer2Rotation = 0f
            } else {
                overlayImageUri = uri; overlayScale = 1f; overlayOffset = Offset.Zero
                overlayRotation = 0f; overlayFlipped = false; overlayBrightness = 0f
                overlayContrast = 1f; overlayOutline = false; outlineBitmap = null
            }
            imageLocked = false
        }
    }

    // Edge detection for outline
    LaunchedEffect(overlayOutline, overlayImageUri) {
        if (overlayOutline && overlayImageUri != null && outlineBitmap == null) {
            try {
                val inputStream = context.contentResolver.openInputStream(overlayImageUri!!)
                val src = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (src != null) {
                    outlineBitmap = applyEdgeDetection(src)
                }
            } catch (_: Exception) { }
        }
    }

    // ── Full screen container ──
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imgCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCaptureUseCase = imgCapture

                    val cameraSelector = if (useFrontCamera)
                        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imgCapture
                        )
                        cameraRef = camera
                        cameraError = false
                        if (camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(flashEnabled)
                        }
                    } catch (_: Exception) { cameraError = true }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Camera error placeholder
        if (cameraError && overlayImageUri == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Cámara no disponible", color = Color.White.copy(0.3f), fontSize = 14.sp)
                }
            }
        }

        // Grid overlay
        if (showGrid) {
            val gridLines = if (gridCount == 9) 2 else 3
            Box(modifier = Modifier.fillMaxSize().drawBehind {
                val w = size.width; val h = size.height
                val paint = Color.White.copy(alpha = 0.25f)
                for (i in 1..gridLines) {
                    val x = w * i / (gridLines + 1)
                    drawLine(paint, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                for (i in 1..gridLines) {
                    val y = h * i / (gridLines + 1)
                    drawLine(paint, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
            })
        }

        // Calibration crosshair
        if (showCalibration) {
            Box(modifier = Modifier.fillMaxSize().drawBehind {
                val cx = size.width / 2; val cy = size.height / 2
                val col = Color.White.copy(alpha = 0.5f)
                drawLine(col, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.5f)
                drawLine(col, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.5f)
                drawCircle(col, radius = 40f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                drawCircle(col, radius = 100f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            })
        }

        // ── Overlay Layer 2 ──
        layer2Uri?.let { uri ->
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(uri).crossfade(true).build()
            )
            val state2 = rememberTransformableState { z, o, r ->
                if (!imageLocked && activeLayer == 2) {
                    layer2Scale = (layer2Scale * z).coerceIn(0.1f, 5f)
                    layer2Offset = Offset(layer2Offset.x + o.x, layer2Offset.y + o.y)
                    layer2Rotation += r
                }
            }
            Image(
                painter = painter, contentDescription = "Layer 2",
                modifier = Modifier.fillMaxSize()
                    .offset { IntOffset(layer2Offset.x.roundToInt(), layer2Offset.y.roundToInt()) }
                    .graphicsLayer(scaleX = layer2Scale, scaleY = layer2Scale, rotationZ = layer2Rotation)
                    .alpha(layer2Opacity)
                    .then(if (!imageLocked && activeLayer == 2) Modifier.transformable(state2) else Modifier),
                contentScale = ContentScale.Fit
            )
        }

        // ── Overlay Layer 1 ──
        overlayImageUri?.let { uri ->
            val transformState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                if (!imageLocked && activeLayer == 1) {
                    overlayScale = (overlayScale * zoomChange).coerceIn(0.1f, 5f)
                    overlayOffset = Offset(overlayOffset.x + offsetChange.x, overlayOffset.y + offsetChange.y)
                    overlayRotation += rotationChange
                }
            }

            if (overlayOutline && outlineBitmap != null) {
                Image(
                    bitmap = outlineBitmap!!.asImageBitmap(), contentDescription = "Outline",
                    modifier = Modifier.fillMaxSize()
                        .offset { IntOffset(overlayOffset.x.roundToInt(), overlayOffset.y.roundToInt()) }
                        .graphicsLayer(
                            scaleX = overlayScale * (if (overlayFlipped) -1f else 1f),
                            scaleY = overlayScale, rotationZ = overlayRotation
                        )
                        .alpha(overlayOpacity)
                        .then(if (!imageLocked && activeLayer == 1) Modifier.transformable(transformState) else Modifier),
                    contentScale = ContentScale.Fit
                )
            } else {
                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context).data(uri).crossfade(true).build()
                )
                val brightnessMatrix = ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                        overlayContrast, 0f, 0f, 0f, overlayBrightness * 255f,
                        0f, overlayContrast, 0f, 0f, overlayBrightness * 255f,
                        0f, 0f, overlayContrast, 0f, overlayBrightness * 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
                Image(
                    painter = painter, contentDescription = "Overlay",
                    modifier = Modifier.fillMaxSize()
                        .offset { IntOffset(overlayOffset.x.roundToInt(), overlayOffset.y.roundToInt()) }
                        .graphicsLayer(
                            scaleX = overlayScale * (if (overlayFlipped) -1f else 1f),
                            scaleY = overlayScale, rotationZ = overlayRotation
                        )
                        .alpha(overlayOpacity)
                        .then(if (!imageLocked && activeLayer == 1) Modifier.transformable(transformState) else Modifier),
                    contentScale = ContentScale.Fit,
                    colorFilter = brightnessMatrix
                )
            }
        }

        // ═════════════════════════════════════════
        // ── TOP BAR ──
        // ═════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "..." menu
            IconButton(
                onClick = { menuSection = if (menuSection == MenuSection.MAIN) MenuSection.NONE else MenuSection.MAIN },
                modifier = Modifier.size(44.dp).background(uiBg.copy(0.9f), CircleShape)
            ) {
                Icon(Icons.Default.MoreHoriz, "Menú", tint = uiFg, modifier = Modifier.size(22.dp))
            }

            // Image name + lock
            if (overlayImageUri != null) {
                Row(
                    modifier = Modifier
                        .background(uiBg.copy(0.9f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Layers, null, tint = uiSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Imagen", color = uiFg, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { imageLocked = !imageLocked },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (imageLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            "Bloquear", tint = if (imageLocked) uiFg else uiSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Layers icon
            IconButton(
                onClick = {
                    activeLayer = if (activeLayer == 1) 2 else 1
                    Toast.makeText(context, "Capa $activeLayer activa", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(44.dp).background(uiBg.copy(0.9f), CircleShape)
            ) {
                Icon(Icons.Default.Layers, "Capas", tint = uiFg, modifier = Modifier.size(22.dp))
            }
        }

        // ═════════════════════════════════════════
        // ── RIGHT SIDE TOOLBAR ──
        // ═════════════════════════════════════════
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                .background(uiBg.copy(0.9f), RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToolButton(Icons.Default.SwapHoriz, "Flip",
                active = overlayFlipped, fg = uiFg, secondary = uiSecondary) {
                overlayFlipped = !overlayFlipped
            }
            ToolButton(Icons.Default.GridOn, "Grid",
                active = showGrid, fg = uiFg, secondary = uiSecondary) {
                showGrid = !showGrid
                if (showGrid) activeToolPanel = ToolPanel.GRID
                else if (activeToolPanel == ToolPanel.GRID) activeToolPanel = ToolPanel.NONE
            }
            ToolButton(Icons.Default.Opacity, "Opacity",
                active = activeToolPanel == ToolPanel.OPACITY, fg = uiFg, secondary = uiSecondary) {
                activeToolPanel = if (activeToolPanel == ToolPanel.OPACITY) ToolPanel.NONE else ToolPanel.OPACITY
            }
            ToolButton(Icons.Default.Straighten, "Outline",
                active = overlayOutline, fg = uiFg, secondary = uiSecondary) {
                overlayOutline = !overlayOutline
                if (!overlayOutline) outlineBitmap = null
            }
            ToolButton(Icons.Default.Tune, "Adjust",
                active = activeToolPanel == ToolPanel.ADJUST, fg = uiFg, secondary = uiSecondary) {
                activeToolPanel = if (activeToolPanel == ToolPanel.ADJUST) ToolPanel.NONE else ToolPanel.ADJUST
            }
        }

        // ═════════════════════════════════════════
        // ── SLIDER PANEL (above bottom bar) ──
        // ═════════════════════════════════════════
        AnimatedVisibility(
            visible = activeToolPanel != ToolPanel.NONE,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .background(uiBg.copy(0.92f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                when (activeToolPanel) {
                    ToolPanel.OPACITY -> {
                        SliderRow("Opacity", "${(overlayOpacity * 100).toInt()}%", overlayOpacity, 0f, 1f, uiFg) {
                            overlayOpacity = it
                        }
                    }
                    ToolPanel.ADJUST -> {
                        SliderRow("Brightness", "${(overlayBrightness * 100).toInt()}", overlayBrightness, -0.5f, 0.5f, uiFg) {
                            overlayBrightness = it
                        }
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Contrast", "${(overlayContrast * 100).toInt()}%", overlayContrast, 0.5f, 2f, uiFg) {
                            overlayContrast = it
                        }
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Scale", "${(overlayScale * 100).toInt()}%", overlayScale, 0.1f, 5f, uiFg) {
                            if (!imageLocked) overlayScale = it
                        }
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Rotation", "${overlayRotation.toInt()}°", overlayRotation, 0f, 360f, uiFg) {
                            if (!imageLocked) overlayRotation = it
                        }
                    }
                    ToolPanel.GRID -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(9 to "3×3", 12 to "4×3").forEach { (count, label) ->
                                Text(
                                    label, color = if (gridCount == count) uiFg else uiSecondary,
                                    fontSize = 14.sp, fontWeight = if (gridCount == count) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .background(
                                            if (gridCount == count) uiSurface else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { gridCount = count }
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                    ToolPanel.NONE -> {}
                }
            }
        }

        // ═════════════════════════════════════════
        // ── BOTTOM BAR ──
        // ═════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image button
            Row(
                modifier = Modifier
                    .background(uiBg.copy(0.9f), RoundedCornerShape(22.dp))
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Image, "Imagen", tint = uiFg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Image", color = uiFg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Capture button (circle)
            Box(
                modifier = Modifier.size(64.dp)
                    .background(uiBg.copy(0.9f), CircleShape)
                    .border(3.dp, uiSecondary.copy(0.3f), CircleShape)
                    .clickable {
                        capturePhoto(imageCaptureUseCase, context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(52.dp)
                        .background(uiBg, CircleShape)
                        .border(2.dp, uiSecondary.copy(0.2f), CircleShape)
                )
            }

            // Calibrate button
            Row(
                modifier = Modifier
                    .background(uiBg.copy(0.9f), RoundedCornerShape(22.dp))
                    .clickable {
                        showCalibration = !showCalibration
                        Toast.makeText(context,
                            if (showCalibration) "Calibración activada" else "Calibración desactivada",
                            Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CenterFocusStrong, "Calibrar", tint = uiFg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Calibrate", color = uiFg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ═════════════════════════════════════════
        // ── "..." MENU OVERLAY ──
        // ═════════════════════════════════════════
        AnimatedVisibility(
            visible = menuSection != MenuSection.NONE,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(0.5f))
                    .clickable { menuSection = MenuSection.NONE }
            ) {
                Column(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(top = 100.dp, start = 16.dp)
                        .width(240.dp)
                        .background(uiBg, RoundedCornerShape(16.dp))
                        .clickable(enabled = false) {}
                        .padding(8.dp)
                ) {
                    when (menuSection) {
                        MenuSection.MAIN -> {
                            MenuItem(Icons.Default.FolderOpen, "Archivo", uiFg) { menuSection = MenuSection.ARCHIVO }
                            MenuItem(Icons.Default.Tune, "Herramientas", uiFg) { menuSection = MenuSection.HERRAMIENTAS }
                            MenuItem(Icons.Default.LightMode, "Preferencias", uiFg) { menuSection = MenuSection.PREFERENCIAS }
                            MenuItem(Icons.AutoMirrored.Filled.Help, "Ayuda", uiFg) { menuSection = MenuSection.AYUDA }
                        }
                        MenuSection.ARCHIVO -> {
                            MenuBack("Archivo", uiFg) { menuSection = MenuSection.MAIN }
                            MenuItem(Icons.Default.Image, "Importar imagen", uiFg) {
                                menuSection = MenuSection.NONE
                                imagePickerLauncher.launch("image/*")
                            }
                            MenuItem(Icons.Default.Save, "Guardar proyecto", uiFg) {
                                menuSection = MenuSection.NONE
                                Toast.makeText(context, "Proyecto guardado", Toast.LENGTH_SHORT).show()
                            }
                            MenuItem(Icons.Default.Share, "Compartir", uiFg) {
                                menuSection = MenuSection.NONE
                                Toast.makeText(context, "Compartir", Toast.LENGTH_SHORT).show()
                            }
                        }
                        MenuSection.HERRAMIENTAS -> {
                            MenuBack("Herramientas", uiFg) { menuSection = MenuSection.MAIN }
                            MenuItem(Icons.AutoMirrored.Filled.RotateRight, "Rotar canvas", uiFg) {
                                overlayRotation = (overlayRotation + 90f) % 360f
                                menuSection = MenuSection.NONE
                            }
                            MenuItem(Icons.Default.SwapHoriz, "Voltear imagen", uiFg) {
                                overlayFlipped = !overlayFlipped
                                menuSection = MenuSection.NONE
                            }
                            MenuItem(Icons.Default.FlipCameraAndroid, "Cambiar cámara", uiFg) {
                                useFrontCamera = !useFrontCamera
                                menuSection = MenuSection.NONE
                            }
                        }
                        MenuSection.PREFERENCIAS -> {
                            MenuBack("Preferencias", uiFg) { menuSection = MenuSection.MAIN }
                            MenuItem(
                                if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                if (darkMode) "Modo claro" else "Modo oscuro", uiFg
                            ) {
                                darkMode = !darkMode
                            }
                            MenuItem(Icons.Default.GridOn, "Grid: ${if (gridCount == 9) "3×3" else "4×3"}", uiFg) {
                                gridCount = if (gridCount == 9) 12 else 9
                            }
                        }
                        MenuSection.AYUDA -> {
                            MenuBack("Ayuda", uiFg) { menuSection = MenuSection.MAIN }
                            MenuItem(Icons.AutoMirrored.Filled.Help, "Tutorial", uiFg) {
                                menuSection = MenuSection.NONE
                                Toast.makeText(context, "Tutorial: usa los gestos para mover, escalar y rotar la imagen", Toast.LENGTH_LONG).show()
                            }
                            MenuItem(Icons.Default.Share, "Feedback", uiFg) {
                                menuSection = MenuSection.NONE
                                Toast.makeText(context, "Enviar feedback", Toast.LENGTH_SHORT).show()
                            }
                        }
                        MenuSection.NONE -> {}
                    }
                }
            }
        }

        // ═════════════════════════════════════════
        // ── BOTTOM SHEET (Recent images) ──
        // ═════════════════════════════════════════
        AnimatedVisibility(
            visible = showBottomSheet,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            RecentImagesSheet(context, uiBg, uiFg, uiSecondary) { uri ->
                if (activeLayer == 2) {
                    layer2Uri = uri; layer2Scale = 1f; layer2Offset = Offset.Zero; layer2Rotation = 0f
                } else {
                    overlayImageUri = uri; overlayScale = 1f; overlayOffset = Offset.Zero
                    overlayRotation = 0f; overlayFlipped = false; overlayBrightness = 0f
                    overlayContrast = 1f; overlayOutline = false; outlineBitmap = null
                }
                imageLocked = false
                showBottomSheet = false
            }
        }
    }
}

// ═══════════════════════════════
// ── Reusable components ──
// ═══════════════════════════════

@Composable
fun ToolButton(icon: ImageVector, label: String, active: Boolean, fg: Color, secondary: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) fg.copy(0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .width(48.dp)
    ) {
        Icon(icon, label, tint = if (active) fg else secondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = if (active) fg else secondary,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
fun SliderRow(label: String, value: String, current: Float, min: Float, max: Float, fg: Color, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(value, color = fg.copy(0.6f), fontSize = 13.sp)
    }
    Slider(
        value = current, onValueChange = onChange, valueRange = min..max,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = fg.copy(0.15f)
        )
    )
}

@Composable
fun MenuItem(icon: ImageVector, label: String, fg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg.copy(0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 14.sp)
    }
}

@Composable
fun MenuBack(title: String, fg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Close, null, tint = fg.copy(0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecentImagesSheet(context: Context, bg: Color, fg: Color, secondary: Color, onSelect: (Uri) -> Unit) {
    val recentImages = remember { queryRecentImages(context, limit = 10) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(top = 12.dp, bottom = 32.dp)
    ) {
        Box(
            Modifier.width(40.dp).height(4.dp).background(secondary.copy(0.3f), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Recent", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("View all", color = secondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentImages) { uri ->
                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context).data(uri).crossfade(true).size(200).build()
                )
                Image(
                    painter = painter, contentDescription = null,
                    modifier = Modifier.size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, secondary.copy(0.2f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(uri) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// ═══════════════════════════════
// ── Utility functions ──
// ═══════════════════════════════

private fun capturePhoto(imageCapture: ImageCapture?, context: Context) {
    imageCapture ?: run {
        Toast.makeText(context, "Cámara no lista", Toast.LENGTH_SHORT).show()
        return
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Drau_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Drau")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(context, "Error al capturar", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

private fun applyEdgeDetection(src: Bitmap): Bitmap {
    val w = src.width; val h = src.height
    val gray = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(gray)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(0f) }
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)

    val pixels = IntArray(w * h)
    gray.getPixels(pixels, 0, w, 0, 0, w, h)

    val result = IntArray(w * h)
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val gx = -getGray(pixels, x - 1, y - 1, w) - 2 * getGray(pixels, x - 1, y, w) - getGray(pixels, x - 1, y + 1, w) +
                    getGray(pixels, x + 1, y - 1, w) + 2 * getGray(pixels, x + 1, y, w) + getGray(pixels, x + 1, y + 1, w)
            val gy = -getGray(pixels, x - 1, y - 1, w) - 2 * getGray(pixels, x, y - 1, w) - getGray(pixels, x + 1, y - 1, w) +
                    getGray(pixels, x - 1, y + 1, w) + 2 * getGray(pixels, x, y + 1, w) + getGray(pixels, x + 1, y + 1, w)
            val mag = Math.sqrt((gx * gx + gy * gy).toDouble()).toInt().coerceIn(0, 255)
            val inv = 255 - mag
            result[y * w + x] = (0xFF shl 24) or (inv shl 16) or (inv shl 8) or inv
        }
    }

    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    output.setPixels(result, 0, w, 0, 0, w, h)
    return output
}

private fun getGray(pixels: IntArray, x: Int, y: Int, w: Int): Int {
    val pixel = pixels[y * w + x]
    return (pixel shr 16) and 0xFF
}

private fun queryRecentImages(context: Context, limit: Int): List<Uri> {
    val images = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection, null, null, sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idColumn)
            images.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            count++
        }
    }
    return images
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
        stream?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }
}

// ═══════════════════════════════
// ── Permission screens ──
// ═══════════════════════════════

@Composable
fun PermissionRationale(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Drau", color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.CameraAlt, null, tint = Color.Black, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Se necesita acceso a la cámara", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Drau necesita acceso a tu cámara para funcionar.", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.background(Color.Black, RoundedCornerShape(28.dp))
                    .clickable(onClick = onRequestPermission).padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Text("Conceder permiso", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun PermissionDenied() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Drau", color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.CameraAlt, null, tint = Color.Black, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Permiso de cámara requerido", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Habilita el permiso de cámara en ajustes para usar Drau.", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
