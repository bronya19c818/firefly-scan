package cn.yuandianai.juziscan.scanner

import android.graphics.Bitmap
import cn.yuandianai.juziscan.scan.DocumentImageStyle
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Fixed presets shared by the editor, background processing and export. Input is never mutated. */
object DocumentStyleProcessor {
    private const val COLOR_NORMALIZATION = 0.55
    private const val WHITE_NORMALIZATION = 0.90
    private const val WHITE_GAMMA = 0.88
    private const val BW_BIAS = 10.0
    private const val GRAY_TAIL = 0.004
    private const val COLOR_SHARPEN = 0.25
    private const val GRAY_SHARPEN = 0.40

    fun apply(source: Bitmap, style: DocumentImageStyle): Bitmap {
        if (style == DocumentImageStyle.Original) return source.copy(Bitmap.Config.ARGB_8888, false)
        check(OpenCVLoader.initLocal()) { "图片处理暂不可用，请重试" }
        val mats = mutableListOf<Mat>()
        fun mat() = Mat().also { mats.add(it) }
        val rgba = mat(); val rgb = mat(); val hsv = mat(); val value = mat()
        val background = mat(); val result = mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            rgba.release()
            if (style == DocumentImageStyle.BlackWhite) {
                Imgproc.cvtColor(rgb, value, Imgproc.COLOR_RGB2GRAY)
                rgb.release()
                // A small bias and no median erosion retain fine table rules and pale writing.
                val block = (minOf(source.width, source.height) / 16).coerceIn(25, 101) or 1
                Imgproc.adaptiveThreshold(value, result, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, block, BW_BIAS)
            } else {
                if (style == DocumentImageStyle.White) {
                    rgb.copyTo(value)
                    rgb.release()
                } else {
                    Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
                    rgb.release()
                    Core.extractChannel(hsv, value, 2)
                }
                val kernelSize = (sqrt(source.width.toDouble() * source.height) / 32).roundToInt().coerceIn(15, 101) or 1
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelSize.toDouble(), kernelSize.toDouble()))
                try { Imgproc.dilate(value, background, kernel) } finally { kernel.release() }
                Imgproc.GaussianBlur(background, background, Size(kernelSize.toDouble(), kernelSize.toDouble()), 0.0)
                Core.divide(value, background, background, 255.0)
                val strength = if (style == DocumentImageStyle.White) WHITE_NORMALIZATION else COLOR_NORMALIZATION
                Core.addWeighted(background, strength, value, 1.0 - strength, 0.0, value)
                background.release()
                if (style == DocumentImageStyle.White) {
                    val lut = mat().apply { create(1, 256, CvType.CV_8UC1) }
                    lut.put(0, 0, ByteArray(256) { (255 * (it / 255.0).pow(WHITE_GAMMA)).roundToInt().toByte() })
                    Core.LUT(value, lut, value)
                    lut.release()
                }
                if (style == DocumentImageStyle.White) {
                    // Channel normalization also reduces the cast of slightly yellow paper.
                    value.copyTo(result)
                    value.release()
                } else {
                    // Color enhancement preserves hue and saturation of seals, pens and photos.
                    Core.insertChannel(value, hsv, 2)
                    value.release()
                    Imgproc.cvtColor(hsv, result, Imgproc.COLOR_HSV2RGB)
                    hsv.release()
                }
                if (style == DocumentImageStyle.Grayscale) {
                    Imgproc.cvtColor(result, result, Imgproc.COLOR_RGB2GRAY)
                    stretchGray(result)
                }
                val amount = if (style == DocumentImageStyle.Grayscale) GRAY_SHARPEN else COLOR_SHARPEN
                val blurred = mat()
                Imgproc.GaussianBlur(result, blurred, Size(0.0, 0.0), 1.0)
                Core.addWeighted(result, 1.0 + amount, blurred, -amount, 0.0, result)
                blurred.release()
            }
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                try { Utils.matToBitmap(result, it) } catch (error: Throwable) { it.recycle(); throw error }
            }
        } finally { mats.forEach { it.release() } }
    }

    private fun stretchGray(gray: Mat) {
        val bytes = ByteArray(gray.total().toInt())
        gray.get(0, 0, bytes)
        val counts = IntArray(256)
        bytes.forEach { counts[it.toInt() and 255]++ }
        val tail = (bytes.size * GRAY_TAIL).toInt()
        var low = 0; var high = 255; var sum = 0
        while (low < 255 && sum + counts[low] <= tail) { sum += counts[low]; low++ }
        sum = 0
        while (high > low && sum + counts[high] <= tail) { sum += counts[high]; high-- }
        if (high - low < 24) return
        val lut = Mat(1, 256, CvType.CV_8UC1)
        try {
            lut.put(0, 0, ByteArray(256) { (((it - low) * 255) / (high - low)).coerceIn(0, 255).toByte() })
            Core.LUT(gray, lut, gray)
        } finally { lut.release() }
    }
}
