package com.example.smogtiming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Analyzes video frames to detect orange road cones.
 * Scans the middle horizontal line of the frame for orange pixels.
 */
class FrameAnalyzer {
    // Orange color range in RGB (allowing for shades and variations)
    // Typical orange: RGB(255, 165, 0) to RGB(255, 140, 0)
    // These can be calibrated using the calibration feature
    private var orangeMinR = 200
    private var orangeMaxR = 255
    private var orangeMinG = 100
    private var orangeMaxG = 200
    private var orangeMinB = 0
    private var orangeMaxB = 80
    
    // Minimum number of consecutive orange pixels to consider as detection
    private val minOrangePixelCount = 10
    
    /**
     * Analyzes a single frame from the video stream.
     * Scans the middle horizontal line for orange pixels.
     * 
     * @param image The image frame to analyze
     * @return Boolean true if orange cone is detected, false otherwise
     */
    fun analyzeFrame(image: ImageProxy): Boolean {
        if (image.format != ImageFormat.YUV_420_888) {
            return false
        }
        
        try {
            val bitmap = imageProxyToBitmapInternal(image)
            val width = bitmap.width
            val height = bitmap.height
            val middleRow = height / 2
            
            // Scan the middle row for orange pixels
            var consecutiveOrangePixels = 0
            var maxConsecutiveOrange = 0
            
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, middleRow)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                if (isOrangePixel(r, g, b)) {
                    consecutiveOrangePixels++
                    maxConsecutiveOrange = maxOf(maxConsecutiveOrange, consecutiveOrangePixels)
                } else {
                    consecutiveOrangePixels = 0
                }
            }
            
            // Return true if we found a series of orange pixels
            return maxConsecutiveOrange >= minOrangePixelCount
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Updates the color range based on calibration data.
     */
    fun updateColorRange(minR: Int, maxR: Int, minG: Int, maxG: Int, minB: Int, maxB: Int) {
        orangeMinR = minR
        orangeMaxR = maxR
        orangeMinG = minG
        orangeMaxG = maxG
        orangeMinB = minB
        orangeMaxB = maxB
    }
    
    /**
     * Checks if a pixel color matches orange (with tolerance for shades).
     */
    private fun isOrangePixel(r: Int, g: Int, b: Int): Boolean {
        return r in orangeMinR..orangeMaxR &&
               g in orangeMinG..orangeMaxG &&
               b in orangeMinB..orangeMaxB &&
               // Ensure R is dominant (orange characteristic)
               r > g &&
               g > b
    }
    
    /**
     * Converts ImageProxy to Bitmap for pixel access.
     * Made public for calibration purposes.
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return imageProxyToBitmapInternal(image)
    }
    
    /**
     * Converts ImageProxy to Bitmap for pixel access.
     * Handles YUV_420_888 format conversion to RGB.
     */
    private fun imageProxyToBitmapInternal(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        // YUV_420_888 format: Y plane is full size, U and V are quarter size
        // Convert to NV21 format for YuvImage
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Interleave U and V planes for NV21 format
        val uvSize = uSize + vSize
        val uvArray = ByteArray(uvSize)
        uBuffer.get(uvArray, 0, uSize)
        vBuffer.get(uvArray, uSize, vSize)
        
        // NV21 format: Y followed by interleaved VU
        // For YUV_420_888, we need to interleave properly
        var uvIndex = 0
        for (i in 0 until minOf(uSize, vSize)) {
            nv21[ySize + uvIndex++] = uvArray[uSize + i] // V
            nv21[ySize + uvIndex++] = uvArray[i]         // U
        }
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
