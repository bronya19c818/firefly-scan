package cn.yuandianai.juziscan.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as CvPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SimpleDocumentScanner {
    enum class CorrectionMode {
        AUTO,
        DOCUMENT,
        ID_CARD,
        INVOICE,
        FREE
    }

    @Volatile
    private var openCvReady: Boolean? = null

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    private var ortSession: OrtSession? = null

    private const val DOCUMENT_ASPECT = 1.4142135f
    private const val INVOICE_ASPECT = 12f / 7f
    private const val SQUARE_ASPECT = 1f
    private const val MAX_PROCESSING_SIDE = 2200
    private val enhanceToneMapLut: Mat by lazy {
        val values = ByteArray(256) { index -> mapEnhancedLuma(index).toByte() }
        Mat(1, 256, CvType.CV_8UC1).apply {
            put(0, 0, values)
        }
    }

    private fun getModelPath(context: Context, assetName: String): String {
        val file = java.io.File(context.cacheDir, assetName)
        if (shouldRefreshCachedAsset(context, assetName, file)) {
            context.assets.open(assetName).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("JuZiScan", "Refreshed cached model asset=$assetName size=${file.length()}")
        }
        return file.absolutePath
    }

    private fun shouldRefreshCachedAsset(
        context: Context,
        assetName: String,
        cachedFile: java.io.File
    ): Boolean {
        if (!cachedFile.exists() || cachedFile.length() == 0L) return true

        val assetLength = runCatching { context.assets.openFd(assetName).length }.getOrNull()
        if (assetLength != null && cachedFile.length() != assetLength) {
            Log.d(
                "JuZiScan",
                "Cached model length mismatch asset=$assetName cache=${cachedFile.length()} assetLen=$assetLength"
            )
            return true
        }

        val assetDigest = runCatching {
            context.assets.open(assetName).use { computeSha256(it) }
        }.getOrNull() ?: return true
        val cachedDigest = runCatching {
            cachedFile.inputStream().use { computeSha256(it) }
        }.getOrNull() ?: return true

        val changed = !assetDigest.contentEquals(cachedDigest)
        if (changed) {
            Log.d("JuZiScan", "Cached model digest mismatch asset=$assetName")
        }
        return changed
    }

    private fun computeSha256(inputStream: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    @Synchronized
    private fun initSession(context: Context) {
        if (ortSession == null) {
            val modelPath = getModelPath(context, "docaligner_heatmap.onnx")
            ortSession = ortEnv.createSession(modelPath)
            Log.d("JuZiScan", "Heatmap session ready path=$modelPath")
        }
    }

    fun detectDocumentQuad(context: Context, source: Bitmap): List<PointF>? {
        try {
            initSession(context)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        val onnxQuad = detectOnnxQuad(source)
        if (onnxQuad != null) return onnxQuad

        val openCvQuad = detectOpenCvQuad(source)
        if (openCvQuad != null) {
            Log.d("JuZiScan", "Falling back to OpenCV quad=$openCvQuad")
        } else {
            Log.w("JuZiScan", "Both ONNX and OpenCV quad detection returned null")
        }
        return openCvQuad
    }

    private fun detectOnnxQuad(source: Bitmap): List<PointF>? {
        val session = ortSession ?: return null
        return detectOnnxQuad(session, source, centerCrop = false)
            ?: detectOnnxQuad(session, source, centerCrop = true)
    }

    private fun detectOnnxQuad(
        session: OrtSession,
        source: Bitmap,
        centerCrop: Boolean
    ): List<PointF>? {
        val prepared = if (centerCrop) centerCropToSquare(source) else source
        val cropOffsetX = if (centerCrop) ((source.width - prepared.width) / 2).coerceAtLeast(0) else 0
        val cropOffsetY = if (centerCrop) ((source.height - prepared.height) / 2).coerceAtLeast(0) else 0

        var resized: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            val resizedBitmap = Bitmap.createScaledBitmap(prepared, 256, 256, true)
            resized = resizedBitmap
            val floatBuffer = FloatBuffer.allocate(1 * 3 * 256 * 256)
            val pixels = IntArray(256 * 256)
            resizedBitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)

            // R channel
            for (i in pixels.indices) {
                val color = pixels[i]
                floatBuffer.put(((color shr 16) and 0xFF) / 255.0f)
            }
            // G channel
            for (i in pixels.indices) {
                val color = pixels[i]
                floatBuffer.put(((color shr 8) and 0xFF) / 255.0f)
            }
            // B channel
            for (i in pixels.indices) {
                val color = pixels[i]
                floatBuffer.put((color and 0xFF) / 255.0f)
            }
            floatBuffer.rewind()

            val tensor = OnnxTensor.createTensor(
                ortEnv,
                floatBuffer,
                longArrayOf(1, 3, 256, 256)
            )
            inputTensor = tensor
            val inputName = session.inputNames.firstOrNull() ?: "img"
            results = session.run(mapOf(inputName to tensor))
            val heatmapTensor = selectHeatmapTensor(session, results) ?: return null
            val heatmapInfo = heatmapTensor.info as? TensorInfo ?: run {
                Log.e("JuZiScan", "Heatmap tensor info is not TensorInfo: ${heatmapTensor.info}")
                return null
            }
            val heatmapShape = heatmapInfo.shape
            val heatmapDims = heatmapShape.map { it.toInt() }
            val (channelCount, heatmapWidth, heatmapHeight) = parseHeatmapDimensions(heatmapDims)
                ?: run {
                    Log.e("JuZiScan", "Unsupported heatmap shape=${heatmapShape.contentToString()}")
                    return null
                }
            if (channelCount < 4) {
                Log.e(
                    "JuZiScan",
                    "Heatmap channel count too small=$channelCount shape=${heatmapShape.contentToString()}"
                )
                return null
            }

            val heatmapData = FloatArray(heatmapInfo.shape.fold(1L) { acc, dim -> acc * dim }.toInt())
            heatmapTensor.floatBuffer.get(heatmapData)
            Log.d(
                "JuZiScan",
                "Heatmap tensor parsed input=$inputName channels=$channelCount size=${heatmapWidth}x$heatmapHeight"
            )

            val points = mutableListOf<CvPoint>()
            val heatmapThreshold = if (centerCrop) 0.22f else 0.26f
            for (channel in 0 until 4) {
                val channelData = FloatArray(heatmapWidth * heatmapHeight)
                val offset = channel * heatmapWidth * heatmapHeight
                for (i in channelData.indices) {
                    channelData[i] = heatmapData[offset + i]
                }
                val point = extractHeatmapPoint(
                    channelIndex = channel,
                    channelData = channelData,
                    heatmapWidth = heatmapWidth,
                    heatmapHeight = heatmapHeight,
                    targetWidth = prepared.width,
                    targetHeight = prepared.height,
                    threshold = heatmapThreshold
                ) ?: run {
                    Log.d("JuZiScan", "Channel $channel failed threshold check (returned null)")
                    return null
                }
                points += CvPoint(
                    point.x + cropOffsetX,
                    point.y + cropOffsetY
                )
            }

            val ordered = orderQuad(points.toTypedArray(), source.width, source.height)
            Log.d(
                "JuZiScan",
                "DocAligner heatmap centerCrop=$centerCrop shape=${heatmapShape.contentToString()} quad=$ordered"
            )
            if (isReasonableModelQuad(ordered)) ordered else null
        } catch (e: Exception) {
            Log.e("JuZiScan", "DocAligner heatmap detect failed centerCrop=$centerCrop", e)
            null
        } finally {
            inputTensor?.close()
            results?.close()
            resized?.takeIf { it !== prepared && !it.isRecycled }?.recycle()
            prepared.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }

    private fun centerCropToSquare(source: Bitmap): Bitmap {
        if (source.width == source.height) return source
        val cropSize = min(source.width, source.height)
        val left = ((source.width - cropSize) / 2).coerceAtLeast(0)
        val top = ((source.height - cropSize) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, left, top, cropSize, cropSize)
    }

    private fun isReasonableModelQuad(points: List<PointF>): Boolean {
        if (points.size != 4) {
            Log.d("JuZiScan", "isReasonableModelQuad failed: size = ${points.size} (expected 4)")
            return false
        }
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            Log.d("JuZiScan", "isReasonableModelQuad failed: non-finite coordinates found: $points")
            return false
        }
        if (points.any { it.x !in -0.05f..1.05f || it.y !in -0.05f..1.05f }) {
            Log.d("JuZiScan", "isReasonableModelQuad failed: coordinates out of bounds (-0.05..1.05): $points")
            return false
        }
        val score = scoreQuad(points)
        Log.d("JuZiScan", "isReasonableModelQuad: score = $score")
        return score >= 0.10f
    }

    private fun extractHeatmapPoint(
        channelIndex: Int,
        channelData: FloatArray,
        heatmapWidth: Int,
        heatmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        threshold: Float
    ): CvPoint? {
        var maxVal = -1f
        var maxIdx = -1
        for (i in channelData.indices) {
            val v = channelData[i]
            if (v > maxVal) {
                maxVal = v
                maxIdx = i
            }
        }

        val minThreshold = min(threshold, 0.18f)
        Log.d("JuZiScan", "Channel $channelIndex: maxVal = $maxVal, minThreshold = $minThreshold")
        if (maxVal < minThreshold) {
            return null
        }

        val heatmapX = maxIdx % heatmapWidth
        val heatmapY = maxIdx / heatmapWidth

        val x = if (heatmapWidth <= 1) {
            targetWidth * 0.5
        } else {
            (heatmapX.toDouble() / (heatmapWidth - 1).toDouble()) * targetWidth
        }
        val y = if (heatmapHeight <= 1) {
            targetHeight * 0.5
        } else {
            (heatmapY.toDouble() / (heatmapHeight - 1).toDouble()) * targetHeight
        }
        return CvPoint(x, y)
    }

    private fun selectHeatmapTensor(
        session: OrtSession,
        results: OrtSession.Result
    ): OnnxTensor? {
        val namedHeatmap = getTensorByName(results, "heatmap")
        if (namedHeatmap != null) return namedHeatmap

        val candidates = buildList {
            addAll(session.outputNames)
            for (entry in results) {
                add(entry.key)
            }
        }.distinct()

        val candidateName = candidates.firstOrNull { name ->
            val tensor = getTensorByName(results, name) ?: return@firstOrNull false
            val info = tensor.info as? TensorInfo ?: return@firstOrNull false
            info.type == OnnxJavaType.FLOAT && info.shape.size >= 3
        }

        if (candidateName == null) {
            Log.e("JuZiScan", "No output tensors found in heatmap session")
            return null
        }

        Log.w("JuZiScan", "Heatmap output name fallback to $candidateName")
        val tensor = getTensorByName(results, candidateName)
        if (tensor == null) {
            Log.e("JuZiScan", "Unable to read tensor for output=$candidateName")
        }
        return tensor
    }

    private fun getTensorByName(
        results: OrtSession.Result,
        name: String
    ): OnnxTensor? {
        val value = runCatching { results.get(name).orElse(null) }.getOrNull() ?: return null
        return value as? OnnxTensor
    }

    private fun parseHeatmapDimensions(dims: List<Int>): Triple<Int, Int, Int>? {
        return when (dims.size) {
            4 -> {
                val channels = if (dims[1] > 1) dims[1] else dims[3]
                val width = if (dims[1] > 1) dims[3] else dims[2]
                val height = if (dims[1] > 1) dims[2] else dims[1]
                Triple(channels, width, height)
            }

            3 -> Triple(dims[0], dims[2], dims[1])
            else -> null
        }
    }

    fun perspectiveCorrect(
        source: Bitmap,
        quad: List<PointF>,
        correctionMode: CorrectionMode = CorrectionMode.AUTO
    ): Bitmap {
        if (quad.size != 4) return source
        val src = FloatArray(8)
        quad.forEachIndexed { index, point ->
            src[index * 2] = point.x.coerceIn(0f, 1f) * source.width
            src[index * 2 + 1] = point.y.coerceIn(0f, 1f) * source.height
        }

        val metrics = computePerspectiveMetrics(source, src)
        val resolvedAspect = resolveTargetAspect(metrics, correctionMode)
        val (outWidth, outHeight) = resolveOutputSize(metrics, resolvedAspect)

        return perspectiveCorrectInternal(source, src, outWidth, outHeight)
    }

    fun perspectiveCorrect(
        source: Bitmap,
        quad: List<PointF>,
        targetAspect: Float
    ): Bitmap {
        if (quad.size != 4) return source
        val src = FloatArray(8)
        quad.forEachIndexed { index, point ->
            src[index * 2] = point.x.coerceIn(0f, 1f) * source.width
            src[index * 2 + 1] = point.y.coerceIn(0f, 1f) * source.height
        }

        val metrics = computePerspectiveMetrics(source, src)
        val resolvedAspect = if (metrics.isLandscape) {
            targetAspect.coerceAtLeast(0.1f)
        } else {
            (1f / targetAspect.coerceAtLeast(0.1f)).coerceAtLeast(0.1f)
        }
        val (outWidth, outHeight) = resolveOutputSize(metrics, resolvedAspect)

        return perspectiveCorrectInternal(source, src, outWidth, outHeight)
    }

    private fun perspectiveCorrectInternal(
        source: Bitmap,
        src: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        perspectiveCorrectOpenCv(source, src, outWidth, outHeight)?.let { return it }

        val dst = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private data class PerspectiveMetrics(
        val averageWidth: Float,
        val averageHeight: Float,
        val aspect: Float,
        val normalizedAspect: Float,
        val areaRatio: Float,
        val isLandscape: Boolean
    )

    private fun computePerspectiveMetrics(source: Bitmap, src: FloatArray): PerspectiveMetrics {
        val top = distance(src[0], src[1], src[2], src[3])
        val right = distance(src[2], src[3], src[4], src[5])
        val bottom = distance(src[4], src[5], src[6], src[7])
        val left = distance(src[6], src[7], src[0], src[1])
        val averageWidth = max((top + bottom) / 2f, 1f)
        val averageHeight = max((left + right) / 2f, 1f)
        val aspect = averageWidth / averageHeight
        val normalizedAspect = max(aspect, 1f / aspect)
        val quadArea = quadrilateralArea(src)
        val sourceArea = (source.width * source.height).toFloat().coerceAtLeast(1f)
        return PerspectiveMetrics(
            averageWidth = averageWidth,
            averageHeight = averageHeight,
            aspect = aspect,
            normalizedAspect = normalizedAspect,
            areaRatio = (quadArea / sourceArea).coerceIn(0f, 1f),
            isLandscape = aspect >= 1f
        )
    }

    private fun resolveTargetAspect(
        metrics: PerspectiveMetrics,
        correctionMode: CorrectionMode
    ): Float {
        val landscapeTarget = when (correctionMode) {
            CorrectionMode.AUTO -> selectAutoAspect(metrics)
            CorrectionMode.DOCUMENT -> DOCUMENT_ASPECT
            CorrectionMode.INVOICE -> INVOICE_ASPECT
            CorrectionMode.FREE -> metrics.normalizedAspect
            CorrectionMode.ID_CARD -> metrics.normalizedAspect
        }
        return if (metrics.isLandscape || landscapeTarget == SQUARE_ASPECT) {
            landscapeTarget
        } else {
            1f / landscapeTarget
        }
    }

    private fun selectAutoAspect(metrics: PerspectiveMetrics): Float {
        val docError = aspectError(metrics.normalizedAspect, DOCUMENT_ASPECT)
        val invoiceError = aspectError(metrics.normalizedAspect, INVOICE_ASPECT)
        val squareError = aspectError(metrics.normalizedAspect, SQUARE_ASPECT)

        if (squareError <= 0.10f && metrics.areaRatio <= 0.45f) {
            return SQUARE_ASPECT
        }
        if (invoiceError <= 0.12f && metrics.normalizedAspect >= 1.45f) {
            return INVOICE_ASPECT
        }
        if (metrics.areaRatio >= 0.50f && metrics.normalizedAspect <= 1.20f) {
            return DOCUMENT_ASPECT
        }
        if (docError <= 0.24f && metrics.areaRatio >= 0.22f) {
            return DOCUMENT_ASPECT
        }
        return metrics.normalizedAspect
    }

    private fun resolveOutputSize(metrics: PerspectiveMetrics, targetAspect: Float): Pair<Int, Int> {
        val safeAspect = targetAspect.coerceAtLeast(0.1f)
        val baseArea = max(metrics.averageWidth * metrics.averageHeight, 1f)
        val rawWidth = max(sqrt(baseArea * safeAspect).roundToInt(), 1)
        val rawHeight = max(sqrt(baseArea / safeAspect).roundToInt(), 1)
        val longestSide = max(rawWidth, rawHeight)
        if (longestSide <= MAX_PROCESSING_SIDE) return rawWidth to rawHeight
        val scale = MAX_PROCESSING_SIDE / longestSide.toFloat()
        return max((rawWidth * scale).roundToInt(), 1) to
            max((rawHeight * scale).roundToInt(), 1)
    }

    private fun aspectError(value: Float, target: Float): Float {
        return abs(value - target) / target
    }

    private fun quadrilateralArea(src: FloatArray): Float {
        val x0 = src[0]
        val y0 = src[1]
        val x1 = src[2]
        val y1 = src[3]
        val x2 = src[4]
        val y2 = src[5]
        val x3 = src[6]
        val y3 = src[7]
        val sum = x0 * y1 + x1 * y2 + x2 * y3 + x3 * y0 -
            (y0 * x1 + y1 * x2 + y2 * x3 + y3 * x0)
        return abs(sum) * 0.5f
    }

    fun rotate(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun enhanceDocument(source: Bitmap): Bitmap {
        applyOpenCvEnhance(source)?.let { return it }
        return applyFallbackEnhance(source)
    }

    private fun detectOpenCvQuad(source: Bitmap): List<PointF>? {
        if (!ensureOpenCvReady()) return null

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val kernel = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val approximations = mutableListOf<MatOfPoint2f>()
        val convexMats = mutableListOf<MatOfPoint>()

        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 48.0, 144.0)

            val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            dilateKernel.copyTo(kernel)
            dilateKernel.release()
            Imgproc.dilate(edges, edges, kernel)

            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val imageArea = source.width * source.height.toDouble()
            var bestQuad: List<PointF>? = null
            var bestScore = 0f

            contours
                .asSequence()
                .map { contour -> contour to Imgproc.contourArea(contour) }
                .filter { (_, area) -> area in (imageArea * 0.10)..(imageArea * 0.95) }
                .sortedByDescending { (_, area) -> area }
                .take(20)
                .forEach { (contour, area) ->
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    try {
                        val perimeter = Imgproc.arcLength(contour2f, true)
                        listOf(0.018, 0.026, 0.036, 0.05).forEach { epsilonRatio ->
                            val approx = MatOfPoint2f()
                            approximations.add(approx)
                            Imgproc.approxPolyDP(contour2f, approx, perimeter * epsilonRatio, true)
                            val points = approx.toArray()
                            if (points.size == 4) {
                                val ordered = orderQuad(points, source.width, source.height)
                                val convex = MatOfPoint(
                                    *ordered.map { point ->
                                        CvPoint(
                                            (point.x * source.width).toDouble(),
                                            (point.y * source.height).toDouble()
                                        )
                                    }.toTypedArray()
                                )
                                convexMats.add(convex)
                                if (Imgproc.isContourConvex(convex)) {
                                    val quadScore = scoreQuad(ordered) +
                                        (area / imageArea).toFloat() * 0.2f
                                    if (quadScore > bestScore) {
                                        bestScore = quadScore
                                        bestQuad = ordered
                                    }
                                }
                            }
                        }
                    } finally {
                        contour2f.release()
                    }
                }

            bestQuad
        } catch (_: Throwable) {
            null
        } finally {
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            kernel.release()
            contours.forEach { it.release() }
            approximations.forEach { it.release() }
            convexMats.forEach { it.release() }
        }
    }

    private fun perspectiveCorrectOpenCv(
        source: Bitmap,
        src: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): Bitmap? {
        if (!ensureOpenCvReady()) return null
        val sourceMat = Mat()
        val dstMat = Mat()
        val srcPoints = MatOfPoint2f(
            CvPoint(src[0].toDouble(), src[1].toDouble()),
            CvPoint(src[2].toDouble(), src[3].toDouble()),
            CvPoint(src[4].toDouble(), src[5].toDouble()),
            CvPoint(src[6].toDouble(), src[7].toDouble())
        )
        val dstPoints = MatOfPoint2f(
            CvPoint(0.0, 0.0),
            CvPoint(outWidth.toDouble(), 0.0),
            CvPoint(outWidth.toDouble(), outHeight.toDouble()),
            CvPoint(0.0, outHeight.toDouble())
        )
        var transform: Mat? = null
        return try {
            Utils.bitmapToMat(source, sourceMat)
            transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            Imgproc.warpPerspective(
                sourceMat,
                dstMat,
                transform,
                Size(outWidth.toDouble(), outHeight.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_CONSTANT,
                Scalar(255.0, 255.0, 255.0, 255.0)
            )
            Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also { output ->
                Utils.matToBitmap(dstMat, output)
            }
        } catch (_: Throwable) {
            null
        } finally {
            sourceMat.release()
            dstMat.release()
            srcPoints.release()
            dstPoints.release()
            transform?.release()
        }
    }

    private fun applyOpenCvEnhance(source: Bitmap): Bitmap? {
        if (!ensureOpenCvReady()) return null
        val rgba = Mat()
        val gray = Mat()
        val normalized = Mat()
        val blurred = Mat()
        val enhanced = Mat()
        val toned = Mat()
        val outputRgba = Mat()
        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            normalizeDocumentGrayOpenCv(gray, normalized)
            Imgproc.GaussianBlur(normalized, blurred, Size(0.0, 0.0), 1.1)
            Core.addWeighted(normalized, 1.42, blurred, -0.42, 2.0, enhanced)
            Core.LUT(enhanced, enhanceToneMapLut, toned)
            Imgproc.cvtColor(toned, outputRgba, Imgproc.COLOR_GRAY2RGBA)
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
                Utils.matToBitmap(outputRgba, output)
            }
        } catch (_: Throwable) {
            null
        } finally {
            rgba.release()
            gray.release()
            normalized.release()
            blurred.release()
            enhanced.release()
            toned.release()
            outputRgba.release()
        }
    }

    private fun normalizeDocumentGrayOpenCv(gray: Mat, output: Mat) {
        val background = Mat()
        try {
            val sigma = (min(gray.cols(), gray.rows()) / 16.0).coerceIn(14.0, 42.0)
            Imgproc.GaussianBlur(gray, background, Size(0.0, 0.0), sigma)
            Core.max(background, Scalar(24.0), background)
            Core.divide(gray, background, output, 255.0)
        } finally {
            background.release()
        }
    }

    private fun applyFallbackEnhance(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)

        val lumaValues = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            lumaValues[i] = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
        }
        val sorted = lumaValues.sorted()
        val low = sorted[(sorted.lastIndex * 0.04f).toInt()]
        val high = sorted[(sorted.lastIndex * 0.98f).toInt()].coerceAtLeast(low + 1)
        val range = (high - low).toFloat().coerceAtLeast(1f)
        val background = localBackgroundLuma(lumaValues, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) and 0xff
            val luma = lumaValues[i]
            val global = (((luma - low) / range) * 255f).coerceIn(0f, 255f)
            val localBg = background[i].coerceIn(96f, 252f)
            val local = ((luma / localBg) * 255f).coerceIn(0f, 255f)
            val normalized = (global * 0.30f + local * 0.70f).coerceIn(0f, 255f)
            val mapped = mapEnhancedLuma(normalized.roundToInt())
            pixels[i] = (alpha shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun mapEnhancedLuma(value: Int): Int {
        val v = value.coerceIn(0, 255)
        return when {
            v >= 228 -> 255
            v >= 205 -> (v + (255 - v) * 0.78f).roundToInt().coerceAtMost(255)
            v >= 180 -> (v + 10f).roundToInt().coerceAtMost(255)
            v <= 92 -> (v * 0.58f).roundToInt().coerceAtLeast(0)
            v <= 148 -> (v * 0.76f - 8f).roundToInt().coerceAtLeast(0)
            else -> (v * 0.90f - 2f).roundToInt().coerceIn(0, 255)
        }
    }

    private fun localBackgroundLuma(
        lumaValues: IntArray,
        width: Int,
        height: Int
    ): FloatArray {
        val integral = buildIntegral(lumaValues, width, height)
        val radius = (min(width, height) / 14).coerceIn(18, 72)
        val background = FloatArray(width * height)
        for (y in 0 until height) {
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius).coerceAtMost(width - 1)
                val sum = rectSum(integral, width, left, top, right, bottom)
                val count = (right - left + 1) * (bottom - top + 1)
                val average = sum / count.toFloat()
                val luma = lumaValues[y * width + x].toFloat()
                val lifted = average + max(average - luma, 0f) * 0.72f
                background[y * width + x] = max(lifted.coerceAtMost(255f), luma)
            }
        }
        return background
    }

    private fun buildIntegral(values: IntArray, width: Int, height: Int): LongArray {
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += values[y * width + x].toLong()
                val target = (y + 1) * (width + 1) + x + 1
                integral[target] = integral[target - (width + 1)] + rowSum
            }
        }
        return integral
    }

    private fun rectSum(
        integral: LongArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Long {
        val stride = width + 1
        val x1 = left
        val y1 = top
        val x2 = right + 1
        val y2 = bottom + 1
        return integral[y2 * stride + x2] -
            integral[y1 * stride + x2] -
            integral[y2 * stride + x1] +
            integral[y1 * stride + x1]
    }

    private fun orderQuad(points: Array<CvPoint>, width: Int, height: Int): List<PointF> {
        val normalized = points.map { point ->
            PointF(
                (point.x / width).toFloat().coerceIn(0f, 1f),
                (point.y / height).toFloat().coerceIn(0f, 1f)
            )
        }
        val sortedBySum = normalized.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()
        val remaining = normalized - topLeft - bottomRight
        val topRight = remaining.minBy { it.y - it.x }
        val bottomLeft = remaining.maxBy { it.y - it.x }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun scoreQuad(points: List<PointF>): Float {
        if (points.size != 4) return 0f
        val area = quadArea(points)
        if (area < 0.05f || area > 0.98f) return 0f

        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val centerScore = (1f - abs(centerX - 0.5f) * 1.4f - abs(centerY - 0.5f) * 1.4f)
            .coerceIn(0f, 1f)
        val angleScore = cornerAngleScore(points)
        val widthScore = min(
            distance(points[0], points[1]),
            distance(points[2], points[3])
        ).coerceIn(0f, 1f)
        val heightScore = min(
            distance(points[1], points[2]),
            distance(points[3], points[0])
        ).coerceIn(0f, 1f)

        return area * 0.45f + centerScore * 0.20f + angleScore * 0.20f + widthScore * 0.075f + heightScore * 0.075f
    }

    private fun cornerAngleScore(points: List<PointF>): Float {
        val scores = points.indices.map { index ->
            val prev = points[(index + 3) % 4]
            val current = points[index]
            val next = points[(index + 1) % 4]
            val ax = prev.x - current.x
            val ay = prev.y - current.y
            val bx = next.x - current.x
            val by = next.y - current.y
            val normA = hypot(ax, ay)
            val normB = hypot(bx, by)
            if (normA <= 0f || normB <= 0f) {
                0f
            } else {
                val cosValue = ((ax * bx) + (ay * by)) / (normA * normB)
                1f - abs(cosValue)
            }
        }
        return scores.average().toFloat().coerceIn(0f, 1f)
    }

    private fun quadArea(points: List<PointF>): Float {
        var sum = 0f
        points.indices.forEach { index ->
            val next = points[(index + 1) % points.size]
            sum += points[index].x * next.y - next.x * points[index].y
        }
        return abs(sum) * 0.5f
    }

    private fun distance(first: PointF, second: PointF): Float = hypot(second.x - first.x, second.y - first.y)

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    private fun downscaleForAnalysis(source: Bitmap, maxSide: Int): Bitmap {
        val longestSide = max(source.width, source.height)
        if (longestSide <= maxSide) return source
        val scale = maxSide / longestSide.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            max((source.width * scale).toInt(), 1),
            max((source.height * scale).toInt(), 1),
            true
        )
    }

    private fun ensureOpenCvReady(): Boolean {
        val cached = openCvReady
        if (cached != null) return cached
        return runCatching { OpenCVLoader.initLocal() }
            .getOrDefault(false)
            .also { openCvReady = it }
    }
}
