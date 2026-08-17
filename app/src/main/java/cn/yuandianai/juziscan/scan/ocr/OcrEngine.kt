package cn.yuandianai.juziscan.scan.ocr

import android.content.Context
import android.graphics.Bitmap

interface OcrEngine {
    val name: String

    suspend fun recognize(
        context: Context,
        bitmap: Bitmap
    ): OcrEngineResult
}

data class OcrEngineResult(
    val text: String,
    val engineName: String,
    val blockCount: Int,
    val lineCount: Int,
    val paragraphCount: Int
)
