package com.example.smogtiming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Composable function that captures video and analyzes each frame.
 * 
 * @param onFrameAnalyzed Callback for each analyzed frame (not invoked while calibration capture is active)
 * @param modifier Modifier for the composable
 */
@Composable
fun VideoCapture(
    onFrameAnalyzed: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val frameAnalyzer = remember { FrameAnalyzer() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    // Calibration state (mirror for analyzer thread)
    val calibrationActive = remember { AtomicBoolean(false) }

    // Analysis result state for circle indicator
    var analysisResult by remember { mutableStateOf(false) }
    // Debug values for centerMva, reference, and center pixel RGB (displayed below the line)
    var debugCenterMva by remember { mutableStateOf(0.0) }
    var debugCenterR by remember { mutableStateOf(0.toDouble()) }
    var debugCenterG by remember { mutableStateOf(0.toDouble()) }
    var debugCenterB by remember { mutableStateOf(0.toDouble()) }
    var colorRange by remember { mutableStateOf(ColorRange()) }
    var isCollectingColorRange by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        // Preview use case
                        val preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        
                        // Image analysis use case for frame-by-frame processing
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor) { image ->
                                    val result = frameAnalyzer.analyzeFrame(image)
                                    analysisResult = result
                                    debugCenterR = frameAnalyzer.rRingBuffer.average()
                                    debugCenterG = frameAnalyzer.gRingBuffer.average()
                                    debugCenterB = frameAnalyzer.bRingBuffer.average()
                                    if (isCollectingColorRange) {
                                        colorRange.r.set(debugCenterR.toFloat())
                                        colorRange.g.set(debugCenterG.toFloat())
                                        colorRange.b.set(debugCenterB.toFloat())
                                    }
                                    if (!calibrationActive.get()) {
                                        onFrameAnalyzed(result)
                                    }

                                    image.close()
                                }
                            }
                        
                        // Select back camera as default
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        try {
                            // Unbind use cases before rebinding
                            cameraProvider.unbindAll()
                            
                            // Bind use cases to camera
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, executor)
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Draw scanning line or cross overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val middleY = size.height / 2
                var middleX = size.width / 2
                val lineWidth = 3.dp.toPx()
                val circleRadius = 15.dp.toPx()
                val circlePadding = 16.dp.toPx()

                // Draw horizontal line across the middle
                drawLine(
                    color = Color.Red,
                    start = Offset(0f, middleY),
                    end = Offset(size.width, middleY),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Round
                )
                drawLine(color = Color.Red,
                    start = Offset(middleX, middleY - 20),
                    end = Offset(middleX, middleY + 20),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Round)
                // Debug: centerMva and reference below the line
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14 * density
                    setAntiAlias(true)
                }
                val textX = 16f
                val textY = middleY + 24
                drawContext.canvas.nativeCanvas.apply {
                    drawText("ref: r%.1f..%.1f, g%.1f..%.1f, b%.1f..%.1f".format(
                        colorRange.r.min, colorRange.r.max,
                        colorRange.g.min, colorRange.g.max,
                        colorRange.b.min, colorRange.b.max), textX, textY + 24, textPaint)
                    drawText("rgb: (%.1f, %.1f, %.1f)".format(debugCenterR, debugCenterG, debugCenterB), textX, textY + 60, textPaint)
                }
                // Draw circle in top right corner
                val circleX = size.width - circlePadding - circleRadius
                val circleY = circlePadding + circleRadius
                val circleColor = if (analysisResult) Color.Green else Color.Red
                drawCircle(
                    color = circleColor,
                    radius = circleRadius,
                    center = Offset(circleX, circleY)
                )
            }
            
            // Calibration button in bottom left corner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Button(
                    onClick = {
                        if (isCollectingColorRange) {
                            colorRange.addTolerance(3.toFloat())
                            frameAnalyzer.setRefColor(colorRange)
                            isCollectingColorRange = false
                            calibrationActive.set(false)
                        } else {
                            colorRange = ColorRange()
                            isCollectingColorRange = true
                            calibrationActive.set(true)
                        }
                    }
                ) {
                    Text(if (isCollectingColorRange) "Стоп" else "Калибровать")
                }
            }
        } else {
            // Handle permission request - you may want to add permission handling UI here
            // For now, this is a placeholder
        }
    }
}
