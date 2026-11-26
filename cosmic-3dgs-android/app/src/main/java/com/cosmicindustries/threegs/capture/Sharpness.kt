package com.cosmicindustries.threegs.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Blur check: variance of the Laplacian on a downscaled grayscale copy of the frame.
 * Higher = sharper. The absolute number depends on the scene, so [BlurJudge]
 * compares each frame to the recent sharp ones instead of a fixed threshold.
 */
object Sharpness {
    fun score(file: File, targetWidth: Int = 640): Double {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return 0.0
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return 0.0
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val g = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            g[i] = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = (g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4f * g[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}

/**
 * Flags a frame as blurry when it scores under [ratio] x the median of the last
 * [window] sharp frames. Needs [warmup] frames before it starts judging.
 */
class BlurJudge(
    private val ratio: Double = 0.55,
    private val window: Int = 15,
    private val warmup: Int = 5,
) {
    private val recent = ArrayDeque<Double>()

    fun isBlurry(score: Double): Boolean {
        val blurry = recent.size >= warmup && score < median() * ratio
        if (!blurry) {
            recent.addLast(score)
            if (recent.size > window) recent.removeFirst()
        }
        return blurry
    }

    fun reset() = recent.clear()

    private fun median(): Double {
        val s = recent.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}
