package cn.yuandianai.juziscan.scan.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class MlKitOcrEngine : OcrEngine {
    override val name: String = "ML Kit 中文 OCR"

    override suspend fun recognize(
        context: Context,
        bitmap: Bitmap
    ): OcrEngineResult {
        require(!bitmap.isRecycled) { "OCR input bitmap has been recycled." }
        val inputBitmap = prepareOcrInputBitmap(bitmap)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val image = InputImage.fromBitmap(inputBitmap, 0)
            val result = withTimeout(OCR_TIMEOUT_MS) {
                recognizer.process(image).awaitResult()
            }
            val normalizedText = normalizeRecognizedText(result.text)
            OcrEngineResult(
                text = normalizedText,
                engineName = name,
                blockCount = result.textBlocks.size,
                lineCount = result.textBlocks.sumOf { it.lines.size },
                paragraphCount = countParagraphs(normalizedText)
            )
        } finally {
            recognizer.close()
            if (inputBitmap !== bitmap && !inputBitmap.isRecycled) {
                inputBitmap.recycle()
            }
        }
    }
}

internal const val OCR_INPUT_MAX_SIDE = 1600
internal const val OCR_TIMEOUT_MS = 30_000L

/**
 * Keep the native OCR pipeline away from very large or device-specific bitmap
 * configurations. Some vendor camera stacks return bitmaps that are valid for
 * drawing but unreliable when passed directly to native ML code.
 */
internal fun prepareOcrInputBitmap(bitmap: Bitmap): Bitmap {
    val longestSide = max(bitmap.width, bitmap.height)
    val scaled = if (longestSide > OCR_INPUT_MAX_SIDE) {
        val scale = OCR_INPUT_MAX_SIDE / longestSide.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }

    if (scaled.config == Bitmap.Config.ARGB_8888) return scaled
    return scaled.copy(Bitmap.Config.ARGB_8888, false).also {
        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
    }
}

internal fun normalizeRecognizedText(text: String): String {
    return text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
}

internal fun countParagraphs(text: String): Int {
    if (text.isBlank()) return 0
    return text
        .split(Regex("\\n\\s*\\n"))
        .count { it.isNotBlank() }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) {
                continuation.resume(value)
            }
        }
        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
}
