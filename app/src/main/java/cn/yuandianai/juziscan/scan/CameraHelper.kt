package cn.yuandianai.juziscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PointF
import android.os.SystemClock
import android.graphics.Rect as AndroidRect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class AutoCaptureStabilityTracker(
    private val stableThresholdMs: Long = 900L,
    private val captureCooldownMs: Long = 1800L
) {
    private var previousQuad: List<PointF>? = null
    private var stableSinceMs: Long? = null
    private var lastAnalyzedAtMs: Long = 0L
    private var lastCaptureAtMs: Long = 0L

    fun shouldAnalyze(nowMs: Long, minIntervalMs: Long = 320L): Boolean {
        if (nowMs - lastAnalyzedAtMs < minIntervalMs) return false
        lastAnalyzedAtMs = nowMs
        return true
    }

    fun reset() {
        previousQuad = null
        stableSinceMs = null
    }

    fun update(
        nowMs: Long,
        quad: List<PointF>,
        autoCaptureEnabled: Boolean = true
    ): AutoCaptureAnalysisResult {
        if (quad.size != 4 || quadArea(quad) < 0.18f) {
            reset()
            return AutoCaptureAnalysisResult(
                uiState = AutoCaptureUiState(
                    hintText = "将文档放入取景框内",
                    stableProgress = 0f,
                    documentDetected = false,
                    liveQuad = emptyList(),
                    isStable = false
                ),
                shouldCapture = false
            )
        }

        val isStable = previousQuad?.let { previous ->
            averageCornerDistance(previous, quad) < 0.035f &&
                abs(quadArea(previous) - quadArea(quad)) < 0.08f
        } ?: false

        stableSinceMs = when {
            stableSinceMs == null -> nowMs
            isStable -> stableSinceMs
            else -> nowMs
        }
        previousQuad = quad

        val stableForMs = nowMs - (stableSinceMs ?: nowMs)
        val progress = (stableForMs / stableThresholdMs.toFloat()).coerceIn(0f, 1f)
        val shouldCapture = autoCaptureEnabled && progress >= 1f &&
            (nowMs - lastCaptureAtMs) >= captureCooldownMs
        if (shouldCapture) {
            lastCaptureAtMs = nowMs
            stableSinceMs = nowMs
        }

        val hint = when {
            !autoCaptureEnabled -> "已识别文档，点击快门拍摄"
            shouldCapture -> "已识别稳定文档，正在自动拍照"
            progress > 0f -> "保持稳定，准备自动拍照"
            else -> "已识别文档，保持稳定即可自动拍照"
        }

        return AutoCaptureAnalysisResult(
            uiState = AutoCaptureUiState(
                hintText = hint,
                stableProgress = if (autoCaptureEnabled) progress else 0f,
                documentDetected = true,
                liveQuad = quad,
                isStable = progress > 0f
            ),
            shouldCapture = shouldCapture
        )
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    flashMode: FlashModeOption,
    captureMode: CaptureModeOption,
    captureTask: CaptureTask,
    analysisExecutor: ExecutorService,
    onAutoCaptureState: (AutoCaptureUiState) -> Unit,
    shouldAnalyze: () -> Boolean,
    onAutoCaptureRequest: () -> Unit
): ImageCapture {
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .build()
        .also { it.surfaceProvider = previewView.surfaceProvider }
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        if (captureTask == CaptureTask.Document) Size(4000, 3000)
                        else Size(CAPTURE_TARGET_WIDTH, CAPTURE_TARGET_HEIGHT),
                        if (captureTask == CaptureTask.Document) ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        else ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()
        )
        .build()
        .apply {
            this.flashMode = flashMode.imageCaptureMode
        }
    val autoTracker = AutoCaptureStabilityTracker()

    val analysis = if (captureTask == CaptureTask.Document) {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(analysisExecutor) { image ->
                    var frameBitmap: Bitmap? = null
                    var rotatedBitmap: Bitmap? = null
                    var analysisBitmap: Bitmap? = null
                    try {
                        if (!shouldAnalyze()) {
                            autoTracker.reset()
                            mainExecutor.execute {
                                onAutoCaptureState(
                                    AutoCaptureUiState(
                                        hintText = "将文档放入取景框内",
                                        stableProgress = 0f,
                                        documentDetected = false
                                    )
                                )
                            }
                            return@setAnalyzer
                        }

                        val nowMs = SystemClock.elapsedRealtime()
                        if (!autoTracker.shouldAnalyze(nowMs)) return@setAnalyzer

                        val bitmap = imageProxyToBitmap(image)
                        frameBitmap = bitmap
                        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                        if (bitmap == null) {
                            autoTracker.reset()
                            mainExecutor.execute {
                                onAutoCaptureState(
                                    AutoCaptureUiState(
                                        hintText = "点击快门拍摄当前页面",
                                        stableProgress = 0f,
                                        documentDetected = false
                                    )
                                )
                            }
                            return@setAnalyzer
                        }

                        val rotated = SimpleDocumentScanner.rotate(bitmap, rotationDegrees)
                        rotatedBitmap = rotated
                        val preparedForAnalysis = prepareAnalysisBitmap(rotated)
                        analysisBitmap = preparedForAnalysis
                        val quad = SimpleDocumentScanner.detectDocumentQuad(
                            context,
                            preparedForAnalysis
                        ) ?: emptyList()
                        val result = autoTracker.update(
                            nowMs, quad, autoCaptureEnabled = captureMode == CaptureModeOption.Auto
                        )
                        // Detection uses the entire rotated buffer; CameraX maps it to
                        // the shared viewport, including PreviewView's FILL_CENTER crop.
                        val sourceTransform = ImageProxyTransformFactory().apply {
                            isUsingRotationDegrees = true
                        }.getOutputTransform(image)
                        val points = result.uiState.liveQuad.flatMap { point ->
                            listOf(point.x * rotated.width, point.y * rotated.height)
                        }.toFloatArray()
                        mainExecutor.execute {
                            if (!shouldAnalyze()) return@execute
                            val targetTransform = previewView.outputTransform
                            val mappedQuad = if (targetTransform != null && points.size == 8 &&
                                previewView.width > 0 && previewView.height > 0
                            ) {
                                CoordinateTransform(sourceTransform, targetTransform).mapPoints(points)
                                points.asList().chunked(2).map { (x, y) ->
                                    PointF(x / previewView.width, y / previewView.height)
                                }
                            } else emptyList()
                            onAutoCaptureState(result.uiState.copy(liveQuad = mappedQuad))
                            if (captureMode == CaptureModeOption.Auto && result.shouldCapture) {
                                onAutoCaptureRequest()
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w("JuZiScan", "Document analysis failed", t)
                        autoTracker.reset()
                        mainExecutor.execute {
                            onAutoCaptureState(
                                AutoCaptureUiState(
                                    hintText = "未识别到文档，可直接点击快门拍摄",
                                    stableProgress = 0f,
                                    documentDetected = false
                                )
                            )
                        }
                    } finally {
                        analysisBitmap
                            ?.takeIf { it !== rotatedBitmap && it !== frameBitmap && !it.isRecycled }
                            ?.recycle()
                        rotatedBitmap
                            ?.takeIf { it !== frameBitmap && !it.isRecycled }
                            ?.recycle()
                        frameBitmap?.takeIf { !it.isRecycled }?.recycle()
                        image.close()
                    }
                }
            }
    } else {
        mainExecutor.execute { onAutoCaptureState(AutoCaptureUiState()) }
        null
    }

    cameraProvider.unbindAll()
    if (analysis != null) {
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageCapture)
            .addUseCase(analysis)
            .setViewPort(requireNotNull(previewView.viewPort))
            .build()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            group
        )
    } else {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }
    return imageCapture
}

private const val CAPTURE_TARGET_WIDTH = 2048
private const val CAPTURE_TARGET_HEIGHT = 1536

fun imageProxyToBitmap(image: ImageProxy, maxSide: Int? = null, jpegQuality: Int = 75): Bitmap? {
    return when (image.format) {
        ImageFormat.YUV_420_888 -> {
            val nv21 = yuv420888ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(AndroidRect(0, 0, image.width, image.height), jpegQuality, out)
            decodeCompressedBitmap(out.toByteArray(), maxSide)
        }

        ImageFormat.JPEG, ImageFormat.JPEG_R -> {
            val buffer = image.planes.firstOrNull()?.buffer ?: return null
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            decodeCompressedBitmap(bytes, maxSide)
        }

        else -> null
    }
}

internal fun calculateBitmapSampleSize(width: Int, height: Int, maxSide: Int): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var sampleSize = 1
    while (max(width / sampleSize, height / sampleSize) > maxSide && sampleSize <= 64) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun decodeCompressedBitmap(bytes: ByteArray, maxSide: Int?): Bitmap? {
    if (maxSide == null) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateBitmapSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val (targetWidth, targetHeight) = calculateBoundedDimensions(
        decoded.width,
        decoded.height,
        maxSide
    )
    if (targetWidth == decoded.width && targetHeight == decoded.height) return decoded

    return Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { scaled ->
        if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
    }
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)

    val chromaRowStride = image.planes[1].rowStride
    val chromaPixelStride = image.planes[1].pixelStride
    val width = image.width
    val height = image.height
    val vBytes = ByteArray(vSize).also { vBuffer.get(it) }
    val uBytes = ByteArray(uSize).also { uBuffer.get(it) }

    var offset = ySize
    for (row in 0 until height / 2) {
        var col = 0
        while (col < width / 2) {
            val index = row * chromaRowStride + col * chromaPixelStride
            nv21[offset++] = vBytes[index]
            nv21[offset++] = uBytes[index]
            col++
        }
    }
    return nv21
}

private fun prepareAnalysisBitmap(source: Bitmap, maxSide: Int = 960): Bitmap {
    val width = source.width
    val height = source.height
    val longestSide = max(width, height)
    if (longestSide <= maxSide) return source
    val scale = maxSide / longestSide.toFloat()
    return Bitmap.createScaledBitmap(
        source,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun quadArea(quad: List<PointF>): Float {
    if (quad.size != 4) return 0f
    var area = 0f
    for (index in quad.indices) {
        val current = quad[index]
        val next = quad[(index + 1) % quad.size]
        area += (current.x * next.y) - (next.x * current.y)
    }
    return abs(area) * 0.5f
}

private fun averageCornerDistance(first: List<PointF>, second: List<PointF>): Float {
    if (first.size != 4 || second.size != 4) return Float.MAX_VALUE
    return first.indices
        .map { index ->
            hypot(
                first[index].x - second[index].x,
                first[index].y - second[index].y
            )
        }
        .average()
        .toFloat()
}
