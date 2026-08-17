package cn.yuandianai.juziscan.scan

import android.content.Context
import android.graphics.Bitmap
import cn.yuandianai.juziscan.scan.ocr.OcrEngineRegistry

data class OcrRecognitionResult(
    val text: String,
    val engineName: String,
    val blockCount: Int,
    val lineCount: Int,
    val paragraphCount: Int
)

suspend fun recognizeChineseText(
    context: Context,
    bitmap: Bitmap
): OcrRecognitionResult {
    val result = OcrEngineRegistry.defaultEngine().recognize(context, bitmap)
    return OcrRecognitionResult(
        text = result.text,
        engineName = result.engineName,
        blockCount = result.blockCount,
        lineCount = result.lineCount,
        paragraphCount = result.paragraphCount
    )
}
