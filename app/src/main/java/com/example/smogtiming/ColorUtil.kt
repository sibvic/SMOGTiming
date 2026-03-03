package com.example.smogtiming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class ColorUtil {
        /**
     * Converts RGB to HSV color space.
     * @return FloatArray with [H, S, V] values
     */
    fun rgbToHSV(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        
        var h = 0f
        val s = if (max == 0f) 0f else delta / max
        val v = max
        
        if (delta != 0f) {
            when (max) {
                rf -> {
                    h = ((gf - bf) / delta) % 6f
                    if (h < 0) h += 6f
                }
                gf -> h = (bf - rf) / delta + 2f
                bf -> h = (rf - gf) / delta + 4f
            }
            h *= 60f
        }
        
        return floatArrayOf(h, s, v)
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