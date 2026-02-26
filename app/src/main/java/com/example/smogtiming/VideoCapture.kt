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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Composable function that captures video and analyzes each frame.
 * 
 * @param onFrameAnalyzed Callback that receives the analysis result for each frame
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
    
    // Calibration state
    var isCalibrationMode by remember { mutableStateOf(false) }
    var calibrationBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            calibrationBitmap?.recycle()
        }
    }
    
    // Calibration function
    fun performCalibration() {
        val bitmap = calibrationBitmap ?: return
        
        try {
            val width = bitmap.width
            val height = bitmap.height
            val centerX = width / 2
            val centerY = height / 2
            
            // Sample pixels in a 21x21 area (center +/- 10 pixels)
            val pixels = mutableListOf<Int>()
            for (y in (centerY - 10).coerceAtLeast(0)..(centerY + 10).coerceAtMost(height - 1)) {
                for (x in (centerX - 10).coerceAtLeast(0)..(centerX + 10).coerceAtMost(width - 1)) {
                    val pixel = bitmap.getPixel(x, y)
                    pixels.add(pixel)
                }
            }
            
            // Calculate min/max RGB values
            var minR = 255
            var maxR = 0
            var minG = 255
            var maxG = 0
            var minB = 255
            var maxB = 0
            
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                minR = minOf(minR, r)
                maxR = maxOf(maxR, r)
                minG = minOf(minG, g)
                maxG = maxOf(maxG, g)
                minB = minOf(minB, b)
                maxB = maxOf(maxB, b)
            }
            
            // Add some tolerance to the range
            val tolerance = 20
            frameAnalyzer.updateColorRange(
                minR = (minR - tolerance).coerceAtLeast(0),
                maxR = (maxR + tolerance).coerceAtMost(255),
                minG = (minG - tolerance).coerceAtLeast(0),
                maxG = (maxG + tolerance).coerceAtMost(255),
                minB = (minB - tolerance).coerceAtLeast(0),
                maxB = (maxB + tolerance).coerceAtMost(255)
            )
            
            calibrationBitmap?.recycle()
            calibrationBitmap = null
            isCalibrationMode = false
        } catch (e: Exception) {
            e.printStackTrace()
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
                                    // Capture bitmap for calibration if in calibration mode
                                    if (isCalibrationMode && calibrationBitmap == null && image.format == ImageFormat.YUV_420_888) {
                                        try {
                                            calibrationBitmap = frameAnalyzer.imageProxyToBitmap(image)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    
                                    if (!isCalibrationMode) {
                                        val result = frameAnalyzer.analyzeFrame(image)
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
                val middleX = size.width / 2
                val middleY = size.height / 2
                val lineWidth = 3.dp.toPx()
                val crossLength = 50.dp.toPx()
                
                if (isCalibrationMode) {
                    // Draw cross in the middle
                    // Horizontal line
                    drawLine(
                        color = Color.Red,
                        start = Offset(middleX - crossLength / 2, middleY),
                        end = Offset(middleX + crossLength / 2, middleY),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                    // Vertical line
                    drawLine(
                        color = Color.Red,
                        start = Offset(middleX, middleY - crossLength / 2),
                        end = Offset(middleX, middleY + crossLength / 2),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    // Draw horizontal line across the middle
                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, middleY),
                        end = Offset(size.width, middleY),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                }
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
                        if (isCalibrationMode) {
                            performCalibration()
                        } else {
                            isCalibrationMode = true
                        }
                    }
                ) {
                    Text(if (isCalibrationMode) "Применить" else "Калибровать")
                }
            }
        } else {
            // Handle permission request - you may want to add permission handling UI here
            // For now, this is a placeholder
        }
    }
}
