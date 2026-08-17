package cn.yuandianai.juziscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun prepareWorkingBitmap(source: Bitmap, maxSide: Int = 2200): Bitmap {
    val longestSide = max(source.width, source.height)
    val scaled = if (longestSide <= maxSide) {
        source
    } else {
        val scale = maxSide / longestSide.toFloat()
        Bitmap.createScaledBitmap(
            source,
            max((source.width * scale).toInt(), 1),
            max((source.height * scale).toInt(), 1),
            true
        )
    }
    return if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, false).also { converted ->
            if (converted !== scaled && scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }
}

fun preparePreviewBitmap(source: Bitmap, maxSide: Int = 1280): Bitmap {
    val longestSide = max(source.width, source.height)
    val scaled = if (longestSide <= maxSide) {
        source
    } else {
        val scale = maxSide / longestSide.toFloat()
        Bitmap.createScaledBitmap(
            source,
            max((source.width * scale).toInt(), 1),
            max((source.height * scale).toInt(), 1),
            true
        )
    }
    return if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, false).also { converted ->
            if (converted !== scaled && scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }
}

fun defaultDocumentQuad(): List<PointF> = listOf(
    PointF(0.08f, 0.08f),
    PointF(0.92f, 0.08f),
    PointF(0.92f, 0.92f),
    PointF(0.08f, 0.92f)
)

fun rotateQuadClockwise(quad: List<PointF>): List<PointF> {
    val rawRotatedQuad = quad.map { PointF(1f - it.y, it.x) }
    return if (rawRotatedQuad.size == 4) {
        listOf(rawRotatedQuad[3], rawRotatedQuad[0], rawRotatedQuad[1], rawRotatedQuad[2])
    } else {
        rawRotatedQuad
    }
}

fun materializePageRotation(page: ScannedPage): ScannedPage {
    val normalizedRotation = normalizePageRotation(page.rotationDegrees)
    if (normalizedRotation == 0f) return page.copy(rotationDegrees = 0f)

    var rotatedPage = page.copy(rotationDegrees = 0f)
    val quarterTurns = (normalizedRotation / 90f).toInt().coerceIn(0, 3)
    repeat(quarterTurns) {
        val rotatedSource = SimpleDocumentScanner.rotate(rotatedPage.sourceBitmap, 90f)
        val reviewBitmap = rotatedPage.reviewBitmap ?: rotatedPage.sourceBitmap
        val processedBitmap = rotatedPage.processedBitmap ?: rotatedPage.sourceBitmap

        val rotatedReview = when {
            reviewBitmap === rotatedPage.sourceBitmap -> rotatedSource
            else -> SimpleDocumentScanner.rotate(reviewBitmap, 90f)
        }
        val rotatedProcessed = when {
            processedBitmap === rotatedPage.sourceBitmap -> rotatedSource
            processedBitmap === reviewBitmap -> rotatedReview
            else -> SimpleDocumentScanner.rotate(processedBitmap, 90f)
        }

        rotatedPage = rotatedPage.copy(
            sourceBitmap = rotatedSource,
            reviewBitmap = rotatedReview,
            editableQuad = rotateQuadClockwise(rotatedPage.editableQuad),
            processedBitmap = rotatedProcessed,
            previewBitmap = preparePreviewBitmap(rotatedProcessed),
            rotationDegrees = 0f
        )
    }
    return rotatedPage
}

const val SCAN_INPUT_MAX_SIDE = 2200
private const val DEFAULT_BITMAP_MAX_SIDE = 4096

internal fun calculateBoundedDimensions(
    width: Int,
    height: Int,
    maxSide: Int
): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Image dimensions must be positive." }
    require(maxSide > 0) { "Maximum side must be positive." }
    val longestSide = max(width, height)
    if (longestSide <= maxSide) return width to height
    val scale = maxSide / longestSide.toFloat()
    return max((width * scale).toInt(), 1) to max((height * scale).toInt(), 1)
}

fun loadBitmapFromUri(
    context: Context,
    uri: Uri,
    maxSide: Int = DEFAULT_BITMAP_MAX_SIDE
): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (targetWidth, targetHeight) = calculateBoundedDimensions(
                info.size.width,
                info.size.height,
                maxSide
            )
            if (targetWidth != info.size.width || targetHeight != info.size.height) {
                decoder.setTargetSize(targetWidth, targetHeight)
            }
            decoder.isMutableRequired = false
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxSide * 2) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val (targetWidth, targetHeight) = calculateBoundedDimensions(
            decoded.width,
            decoded.height,
            maxSide
        )
        if (targetWidth == decoded.width && targetHeight == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    }
}

fun prepareAvatarJpeg(context: Context, uri: Uri, maxSide: Int = 512): ByteArray? {
    val source = loadBitmapFromUri(context, uri, maxSide) ?: return null
    val longestSide = max(source.width, source.height)
    val avatar = if (longestSide <= maxSide) {
        source
    } else {
        val scale = maxSide / longestSide.toFloat()
        Bitmap.createScaledBitmap(
            source,
            max((source.width * scale).toInt(), 1),
            max((source.height * scale).toInt(), 1),
            true
        )
    }
    return ByteArrayOutputStream().use { output ->
        if (!avatar.compress(Bitmap.CompressFormat.JPEG, 85, output)) return null
        output.toByteArray()
    }
}

data class SourceBitmapProcessingResult(
    val sourceBitmap: Bitmap,
    val reviewBitmap: Bitmap,
    val quad: List<PointF>
)

fun processSourceBitmap(context: Context, bitmap: Bitmap): SourceBitmapProcessingResult {
    val workingBitmap = prepareWorkingBitmap(bitmap)
    val quad = SimpleDocumentScanner.detectDocumentQuad(context, workingBitmap) ?: emptyList()
    if (workingBitmap !== bitmap && !bitmap.isRecycled) bitmap.recycle()
    return SourceBitmapProcessingResult(
        sourceBitmap = workingBitmap,
        reviewBitmap = workingBitmap,
        quad = quad
    )
}
