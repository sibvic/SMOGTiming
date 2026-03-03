package com.example.smogtiming

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import kotlin.math.abs


/** Fixed-size ring buffer for doubles; average is over all stored values (up to capacity). */
class RingBufferDouble(private val capacity: Int) {
    private val buffer = DoubleArray(capacity)
    private var writeIndex = 0
    private var count = 0
    private val lock = Any()

    fun add(value: Double) = synchronized(lock) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    fun average(): Double = synchronized(lock) {
        if (count == 0) return 0.0
        var sum = 0.0
        for (i in 0 until count) sum += buffer[(writeIndex + i) % capacity]
        sum / count
    }

    fun clear() = synchronized(lock) {
        writeIndex = 0
        count = 0
    }
}

class RangeValue {
    var min: Float = Float.MAX_VALUE
    var max: Float = Float.MIN_VALUE

    fun set(value: Float) {
        if (min > value) {
            min = value
        }
        if (max < value) {
            max = value
        }
    }

    fun isInRange(value: Double): Boolean {
        return value in (min - tolerance)..(max + tolerance)
    }
    var tolerance: Float = 0.toFloat()
    fun addTolerance(value: Float) {
        tolerance = value
    }
}

class ColorRange {
    var r: RangeValue = RangeValue()
    var g: RangeValue = RangeValue()
    var b: RangeValue = RangeValue()

    fun isInRange(r: Double, g: Double, b: Double) : Boolean {
        return this.r.isInRange(r)
                && this.g.isInRange(g)
                && this.b.isInRange(b)
    }
    fun addTolerance(value: Float) {
        r.addTolerance(value)
        g.addTolerance(value)
        b.addTolerance(value)
    }
}

/**
 * Analyzes video frames to detect orange road cone using hue MVA along the middle row.
 * Reference value and tolerance are stored here; MVA is computed by RowHueMvaCalculator.
 */
class FrameAnalyzer {
    private val colorUtil = ColorUtil()

    private var refColor: ColorRange = ColorRange()
    val rRingBuffer = RingBufferDouble(10)
    val gRingBuffer = RingBufferDouble(10)
    val bRingBuffer = RingBufferDouble(10)

    fun setRefColor(color: ColorRange) {
        refColor = color
    }

    fun imageProxyToBitmap(image: ImageProxy): Bitmap = colorUtil.imageProxyToBitmap(image)

    private fun passes(): Boolean =
        refColor.isInRange(rRingBuffer.average(), gRingBuffer.average(), bRingBuffer.average())

    /**
     * Analyzes a single frame: MVA of H on the middle row; detection is true
     * only if the value in the middle of the series is within reference ± tolerance.
     */
    fun analyzeFrame(image: ImageProxy): Boolean {
        if (image.format != ImageFormat.YUV_420_888) return false
        try {
            val bitmap = colorUtil.imageProxyToBitmap(image)
            val width = bitmap.width
            val height = bitmap.height
            val centerX = width / 2
            val centerY = height / 2
            val centerPixel = bitmap.getPixel(centerX, centerY)
            val lastCenterR = (centerPixel shr 16) and 0xFF
            val lastCenterG = (centerPixel shr 8) and 0xFF
            val lastCenterB = centerPixel and 0xFF
            
            rRingBuffer.add(lastCenterR.toDouble())
            gRingBuffer.add(lastCenterG.toDouble())
            bRingBuffer.add(lastCenterB.toDouble())
            return passes()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
