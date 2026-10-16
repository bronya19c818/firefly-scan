package cn.yuandianai.juziscan.scan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.core.content.FileProvider
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.StringBuilder
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val A4_WIDTH_PX = 2480
private const val A4_HEIGHT_PX = 3508
private const val A4_WIDTH_MM = 210f
private const val A4_HEIGHT_MM = 297f
private const val DEFAULT_ID_CARD_GAP_MM = 12f
private const val MIN_ID_CARD_MARGIN_MM = 10f

fun executeShare(
    context: Context,
    pages: List<ScannedPage>,
    fileName: String,
    fileType: ExportFileType,
    fileSize: ExportFileSize,
    watermarkSettings: ExportWatermarkSettings = ExportWatermarkSettings()
) {
    if (pages.isEmpty()) {
        throw IllegalArgumentException("没有可分享的文档")
    }

    when (fileType) {
        ExportFileType.PDF -> {
            val pdfFile = File(context.cacheDir, "$fileName.pdf")
            pdfFile.outputStream().use { out ->
                writeExportToStream(
                    output = out,
                    pages = pages,
                    fileType = fileType,
                    fileSize = fileSize,
                    watermarkSettings = watermarkSettings
                )
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "共享 PDF 文档"))
        }

        ExportFileType.JPG, ExportFileType.PNG, ExportFileType.TIF, ExportFileType.HEIF -> {
            val mimeType = fileType.toMimeType()
            val extension = fileType.extension

            if (pages.size == 1) {
                val imageFile = File(context.cacheDir, "$fileName.$extension")
                imageFile.outputStream().use { out ->
                    writeSingleImageToStream(
                        output = out,
                        page = pages[0],
                        fileType = fileType,
                        fileSize = fileSize,
                        watermarkSettings = watermarkSettings
                    )
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "共享扫描图片"))
            } else {
                val uris = ArrayList<Uri>()
                for (i in pages.indices) {
                    val imageFile = File(context.cacheDir, "${fileName}_${i + 1}.$extension")
                    imageFile.outputStream().use { out ->
                        writeSingleImageToStream(
                            output = out,
                            page = pages[i],
                            fileType = fileType,
                            fileSize = fileSize,
                            watermarkSettings = watermarkSettings
                        )
                    }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        imageFile
                    )
                    uris.add(uri)
                }

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "共享扫描图片"))
            }
        }
    }
}

fun executePrint(
    context: Context,
    pages: List<ScannedPage>,
    fileName: String,
    fileSize: ExportFileSize
) {
    if (pages.isEmpty()) {
        throw IllegalArgumentException("没有可打印的文档")
    }

    val pdfFile = File(context.cacheDir, "$fileName-print.pdf")
    pdfFile.outputStream().use { out ->
        writePdfToStream(out, pages, fileSize)
    }

    executePrintFile(context, pdfFile, fileName)
}

/** Takes ownership of a disposable PDF snapshot until the system finishes the print task. */
fun executePrintFile(context: Context, pdfFile: File, fileName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: throw IllegalStateException("当前设备不支持系统打印")
    printManager.print(
        fileName,
        PdfFilePrintAdapter(context, pdfFile, fileName),
        PrintAttributes.Builder()
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    )
}

fun executeSharePlainText(
    context: Context,
    fileName: String,
    text: String
) {
    val normalizedText = text.trim()
    require(normalizedText.isNotBlank()) { "没有可导出的文字内容" }

    val textFile = File(context.cacheDir, "$fileName.txt")
    textFile.writeText(normalizedText, Charsets.UTF_8)
    shareSingleFile(
        context = context,
        file = textFile,
        mimeType = "text/plain",
        chooserTitle = "导出 TXT 文本",
        subject = fileName
    )
}

fun executeShareWordDocument(
    context: Context,
    fileName: String,
    text: String
) {
    val normalizedText = text.trim()
    require(normalizedText.isNotBlank()) { "没有可导出的文字内容" }

    val docFile = File(context.cacheDir, "$fileName.doc")
    val html = buildWordCompatibleHtml(normalizedText)
    docFile.writeText(html, Charsets.UTF_8)
    shareSingleFile(
        context = context,
        file = docFile,
        mimeType = "application/msword",
        chooserTitle = "导出 Word 文档",
        subject = fileName
    )
}

fun writeExportToStream(
    output: OutputStream,
    pages: List<ScannedPage>,
    fileType: ExportFileType,
    fileSize: ExportFileSize,
    watermarkSettings: ExportWatermarkSettings = ExportWatermarkSettings()
) {
    when (fileType) {
        ExportFileType.PDF -> writePdfToStream(output, pages, fileSize, watermarkSettings)
        ExportFileType.JPG, ExportFileType.PNG, ExportFileType.TIF, ExportFileType.HEIF -> {
            require(pages.size == 1) { "Only single-page image exports can be written to one file." }
            writeSingleImageToStream(
                output = output,
                page = pages.first(),
                fileType = fileType,
                fileSize = fileSize,
                watermarkSettings = watermarkSettings
            )
        }
    }
}

fun writePdfToStream(
    output: OutputStream,
    pages: List<ScannedPage>,
    fileSize: ExportFileSize,
    watermarkSettings: ExportWatermarkSettings = ExportWatermarkSettings()
) {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    try {
        for (i in pages.indices) {
            withPageExportBitmap(pages[i]) { originalBitmap ->
                val workingBmp = scaleBitmapForExport(originalBitmap, fileSize.scale)
                try {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                        workingBmp.width,
                        workingBmp.height,
                        i + 1
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(workingBmp, 0f, 0f, null)
                    drawWatermark(
                        canvas = page.canvas,
                        width = workingBmp.width.toFloat(),
                        height = workingBmp.height.toFloat(),
                        settings = watermarkSettings
                    )
                    pdfDocument.finishPage(page)
                } finally {
                    if (workingBmp !== originalBitmap) {
                        workingBmp.recycle()
                    }
                }
            }
        }
        pdfDocument.writeTo(output)
    } finally {
        pdfDocument.close()
    }
}

private class PdfFilePrintAdapter(
    private val context: Context,
    private val pdfFile: File,
    private val documentName: String
) : PrintDocumentAdapter() {

    override fun onFinish() {
        pdfFile.delete()
        super.onFinish()
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder("$documentName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        try {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }

            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("JuZiScan", "Print write failed", e)
            callback.onWriteFailed(e.message ?: context.getString(android.R.string.untitled))
        }
    }
}

private fun writeSingleImageToStream(
    output: OutputStream,
    page: ScannedPage,
    fileType: ExportFileType,
    fileSize: ExportFileSize,
    watermarkSettings: ExportWatermarkSettings = ExportWatermarkSettings()
) {
    withPageExportBitmap(page) { originalBitmap ->
        val workingBmp = scaleBitmapForExport(originalBitmap, fileSize.scale)
        try {
            val exportBitmap = if (watermarkSettings.enabled || watermarkSettings.brandMarkEnabled) {
                applyWatermarkToBitmap(workingBmp, watermarkSettings)
            } else {
                workingBmp
            }
            val format = when (fileType) {
                ExportFileType.PNG -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            exportBitmap.compress(format, fileSize.quality, output)
            if (exportBitmap !== workingBmp) {
                exportBitmap.recycle()
            }
        } finally {
            if (workingBmp !== originalBitmap) {
                workingBmp.recycle()
            }
        }
    }
}

fun writeSingleImageToFile(
    file: File,
    page: ScannedPage,
    fileType: ExportFileType,
    fileSize: ExportFileSize,
    watermarkSettings: ExportWatermarkSettings = ExportWatermarkSettings()
) {
    file.outputStream().use { out ->
        writeSingleImageToStream(
            output = out,
            page = page,
            fileType = fileType,
            fileSize = fileSize,
            watermarkSettings = watermarkSettings
        )
    }
}

private fun shareSingleFile(
    context: Context,
    file: File,
    mimeType: String,
    chooserTitle: String,
    subject: String? = null
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun buildWordCompatibleHtml(text: String): String {
    val body = StringBuilder()
    text.lines().forEach { line ->
        val escapedLine = escapeHtml(line).ifBlank { "&nbsp;" }
        body.append("<p>").append(escapedLine).append("</p>")
    }
    return """
        <html>
        <head>
        <meta charset="utf-8" />
        <style>
        body { font-family: sans-serif; font-size: 12pt; line-height: 1.7; margin: 24pt; }
        p { margin: 0 0 10pt 0; }
        </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(text: String): String {
    return buildString(text.length) {
        text.forEach { ch ->
            append(
                when (ch) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> ch
                }
            )
        }
    }
}

fun applyWatermarkToBitmap(
    bitmap: Bitmap,
    settings: ExportWatermarkSettings
): Bitmap {
    val mutableBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mutableBitmap)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    drawWatermark(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), settings)
    return mutableBitmap
}

fun drawWatermark(
    canvas: Canvas,
    width: Float,
    height: Float,
    settings: ExportWatermarkSettings
) {
    if (settings.enabled) {
        drawCustomWatermark(canvas, width, height, settings)
    }
    if (settings.brandMarkEnabled) {
        drawBrandMark(canvas, width, height)
    }
}

private fun drawCustomWatermark(
    canvas: Canvas,
    width: Float,
    height: Float,
    settings: ExportWatermarkSettings
) {

    val text = settings.resolvedText
    if (text.isBlank()) return

    val textSize = min(width, height) * if (settings.style == WatermarkStyleOption.Center) 0.055f else 0.042f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(settings.strength.alpha, 120, 128, 150)
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    when (settings.style) {
        WatermarkStyleOption.Center -> {
            canvas.save()
            canvas.rotate(-24f, width / 2f, height / 2f)
            val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, width / 2f, baseline, paint)
            canvas.restore()
        }

        WatermarkStyleOption.DiagonalRepeat -> {
            val diagonal = hypot(width.toDouble(), height.toDouble()).toFloat()
            val horizontalStep = paint.measureText(text) + width * 0.16f
            val verticalStep = textSize * 3.2f
            canvas.save()
            canvas.rotate(-24f, width / 2f, height / 2f)
            var y = -diagonal
            var rowIndex = 0
            while (y <= diagonal) {
                var x = -diagonal + if (rowIndex % 2 == 0) 0f else horizontalStep / 2f
                while (x <= diagonal) {
                    canvas.drawText(text, x, y, paint)
                    x += horizontalStep
                }
                y += verticalStep
                rowIndex++
            }
            canvas.restore()
        }
    }
}

private fun drawBrandMark(
    canvas: Canvas,
    width: Float,
    height: Float
) {
    val text = "🍊 桔子扫描"
    val shortEdge = min(width, height)
    val textSize = shortEdge * 0.044f
    val horizontalPadding = textSize * 0.55f
    val verticalPadding = textSize * 0.36f
    val edgeMargin = shortEdge * 0.025f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(125, 104, 109, 122)
        this.textSize = textSize
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    val textWidth = paint.measureText(text)
    val textHeight = paint.descent() - paint.ascent()
    val right = width - edgeMargin
    val bottom = height - edgeMargin
    val background = RectF(
        right - textWidth - horizontalPadding * 2f,
        bottom - textHeight - verticalPadding * 2f,
        right,
        bottom
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
    }
    canvas.drawRoundRect(background, textSize * 0.28f, textSize * 0.28f, backgroundPaint)
    val baseline = background.centerY() - (paint.ascent() + paint.descent()) / 2f
    canvas.drawText(text, background.right - horizontalPadding, baseline, paint)
}

private fun scaleBitmapForExport(bitmap: Bitmap, scale: Float): Bitmap {
    if (scale == 1.0f) return bitmap
    val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private inline fun <T> withPageExportBitmap(
    page: ScannedPage,
    block: (Bitmap) -> T
): T {
    val loadedBitmap = page.processedImagePath?.let {
        requireNotNull(android.graphics.BitmapFactory.decodeFile(it)) { "无法读取文档页面" }
    }
    val originalBitmap = loadedBitmap ?: requireNotNull(page.processedBitmap)
    return try {
        val exportBitmap = if (page.rotationDegrees % 360f == 0f) {
            originalBitmap
        } else {
            SimpleDocumentScanner.rotate(originalBitmap, page.rotationDegrees)
        }
        try { block(exportBitmap) }
        finally { if (exportBitmap !== originalBitmap) exportBitmap.recycle() }
    } finally {
        loadedBitmap?.recycle()
    }
}

fun ExportFileType.toMimeType(): String = when (this) {
    ExportFileType.PDF -> "application/pdf"
    ExportFileType.PNG -> "image/png"
    ExportFileType.JPG -> "image/jpeg"
    ExportFileType.TIF -> "image/tiff"
    ExportFileType.HEIF -> "image/heif"
}

fun buildPrintJobName(): String = "JuZiScan_${System.currentTimeMillis()}"

fun buildIdCardCompositePage(
    pages: List<ScannedPage>,
    documentType: IdCardDocumentType,
    layout: IdCardLayoutOption
): ScannedPage {
    val composite = buildIdCardCompositeBitmap(pages, documentType, layout)
    return ScannedPage(
        sourceBitmap = composite,
        processedBitmap = composite,
        previewBitmap = preparePreviewBitmap(composite)
    )
}

fun buildIdCardCompositeBitmap(
    pages: List<ScannedPage>,
    documentType: IdCardDocumentType,
    layout: IdCardLayoutOption
): Bitmap {
    val canvasBitmap = Bitmap.createBitmap(A4_WIDTH_PX, A4_HEIGHT_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    canvas.drawColor(Color.WHITE)

    val pixelsPerMillimeter = min(
        A4_WIDTH_PX / A4_WIDTH_MM,
        A4_HEIGHT_PX / A4_HEIGHT_MM
    )
    val gapPx = DEFAULT_ID_CARD_GAP_MM * pixelsPerMillimeter
    val minMarginPx = MIN_ID_CARD_MARGIN_MM * pixelsPerMillimeter
    val itemWidthPx = documentType.widthMm * pixelsPerMillimeter
    val itemHeightPx = documentType.heightMm * pixelsPerMillimeter
    val totalWidthPx = itemWidthPx * layout.columns + gapPx * (layout.columns - 1)
    val totalHeightPx = itemHeightPx * layout.rows + gapPx * (layout.rows - 1)
    val availableWidthPx = A4_WIDTH_PX - minMarginPx * 2f
    val availableHeightPx = A4_HEIGHT_PX - minMarginPx * 2f
    val scale = min(
        1f,
        min(
            availableWidthPx / totalWidthPx,
            availableHeightPx / totalHeightPx
        )
    )
    val scaledItemWidthPx = itemWidthPx * scale
    val scaledItemHeightPx = itemHeightPx * scale
    val scaledGapPx = gapPx * scale
    val layoutWidthPx = scaledItemWidthPx * layout.columns + scaledGapPx * (layout.columns - 1)
    val layoutHeightPx = scaledItemHeightPx * layout.rows + scaledGapPx * (layout.rows - 1)
    val startLeft = (A4_WIDTH_PX - layoutWidthPx) / 2f
    val startTop = (A4_HEIGHT_PX - layoutHeightPx) / 2f

    repeat(layout.captureCount) { index ->
        val row = index / layout.columns
        val column = index % layout.columns
        val left = startLeft + column * (scaledItemWidthPx + scaledGapPx)
        val top = startTop + row * (scaledItemHeightPx + scaledGapPx)
        val cellRect = RectF(left, top, left + scaledItemWidthPx, top + scaledItemHeightPx)
        drawIdCardSlot(
            canvas = canvas,
            cellRect = cellRect,
            page = pages.getOrNull(index),
            documentType = documentType
        )
    }

    return canvasBitmap
}

private fun drawIdCardSlot(
    canvas: Canvas,
    cellRect: RectF,
    page: ScannedPage?,
    documentType: IdCardDocumentType
) {
    val bitmap = page?.processedBitmap ?: return
    val targetAspect = documentType.widthMm / documentType.heightMm
    val drawRect = fitRectWithAspect(cellRect, targetAspect)
    canvas.drawBitmap(bitmap, null, drawRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
}

private fun fitRectWithAspect(
    bounds: RectF,
    aspect: Float
): RectF {
    val safeAspect = aspect.coerceAtLeast(0.1f)
    val boundsAspect = bounds.width() / bounds.height()
    return if (boundsAspect > safeAspect) {
        val targetWidth = bounds.height() * safeAspect
        val horizontalInset = (bounds.width() - targetWidth) / 2f
        RectF(
            bounds.left + horizontalInset,
            bounds.top,
            bounds.right - horizontalInset,
            bounds.bottom
        )
    } else {
        val targetHeight = bounds.width() / safeAspect
        val verticalInset = (bounds.height() - targetHeight) / 2f
        RectF(
            bounds.left,
            bounds.top + verticalInset,
            bounds.right,
            bounds.bottom - verticalInset
        )
    }
}
