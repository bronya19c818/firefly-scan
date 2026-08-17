package cn.yuandianai.juziscan.scan.ocr

object OcrEngineRegistry {
    private val mlKitEngine: OcrEngine by lazy { MlKitOcrEngine() }

    fun defaultEngine(): OcrEngine = mlKitEngine

    fun fallbackEngine(): OcrEngine = mlKitEngine
}
