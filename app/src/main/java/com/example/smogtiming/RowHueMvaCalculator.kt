package com.example.smogtiming

import android.graphics.Bitmap

/**
 * Calculates moving average of hue (H in HSV) along a single row of an image.
 * Only performs MVA computation; no reference or pass/fail logic.
 *
 * @param period MVA window size (default 10)
 */
class RowHueMvaCalculator(private val period: Int = 10) {
    private val colorUtil = ColorUtil()

    /**
     * Returns the MVA series for the given row: for each valid window position,
     * the average H value over [x, x+period) in 0..360 (HSV hue).
     * Length = width - period + 1.
     */
    fun getMvaSeries(bitmap: Bitmap, rowIndex: Int): DoubleArray {
        val width = bitmap.width
        if (rowIndex < 0 || rowIndex >= bitmap.height || width < period) {
            return doubleArrayOf()
        }
        val hRow = FloatArray(width) { x ->
            val pixel = bitmap.getPixel(x, rowIndex)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            colorUtil.rgbToHSV(r, g, b)[0]
        }
        val n = width - period + 1
        val mva = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until period) sum += hRow[i]
        mva[0] = sum / period
        for (i in 1 until n) {
            sum = sum - hRow[i - 1] + hRow[i + period - 1]
            mva[i] = sum / period
        }
        return mva
    }

    /**
     * MVA value at the center of the row (middle index of the MVA series).
     */
    fun getCenterMvaValue(bitmap: Bitmap, rowIndex: Int): Double {
        val series = getMvaSeries(bitmap, rowIndex)
        if (series.isEmpty()) return 0.0
        return series[series.size / 2]
    }
}
