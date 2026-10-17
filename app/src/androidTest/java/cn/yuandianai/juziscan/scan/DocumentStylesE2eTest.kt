package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android image pipeline, durable files and rendered editor; no filter mocks. */
@RunWith(AndroidJUnit4::class)
class DocumentStylesE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = HistoryRepository(context)
    private val artifacts get() = File(context.getExternalFilesDir(null), "document-styles-e2e").apply { mkdirs() }
    private val styles = listOf("original", "color", "white", "grayscale", "bw")
    private fun fullQuad() = listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f))
    private fun ready() {
        ui.activityRule.scenario.recreate()
        instrumentation.runOnMainSync { ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("我同意").performClick()
    }
    private fun evidence(name: String) {
        ui.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(artifacts, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun openRecord(id: String, title: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-" + id).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasText(title) and hasAnyAncestor(hasTestTag("history-" + id))).performScrollTo().performClick()
        ui.waitUntil(20_000) { ui.onAllNodesWithText("旋转").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun fiveInputsProduceTwentyFiveDistinctStyleOutputsAndTimings() {
        val inputs = File(context.getExternalFilesDir(null), "document-style-inputs").listFiles()!!.filter { it.extension == "png" }.sortedBy { it.name }
        assertEquals("Push all five repository samples before running", 5, inputs.size)
        val timings = JSONArray()
        inputs.forEach { input ->
            val source = requireNotNull(BitmapFactory.decodeFile(input.path))
            val hashes = mutableSetOf<String>()
            try {
                styles.forEach { style ->
                    val output = File(artifacts, input.nameWithoutExtension + "-" + style + ".png")
                    output.delete() // Only this test's previous generated output.
                    val start = SystemClock.elapsedRealtime()
                    val result = processDocumentPage(context, ScannedPage(sourceBitmap = source,
                        editableQuad = fullQuad(), imageStyle = style), output)
                    timings.put(JSONObject().put("input", input.name).put("style", style)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - start).put("width", source.width).put("height", source.height))
                    hashes.add(MessageDigest.getInstance("SHA-256").digest(output.readBytes()).joinToString("") { "%02x".format(it) })
                    if (result.previewBitmap !== source) result.previewBitmap?.recycle()
                }
                assertEquals("Each style must render differently: " + input.name, 5, hashes.size)
                val sheet = Bitmap.createBitmap(1800, 560, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(sheet)
                canvas.drawColor(Color.WHITE)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.BLACK }
                styles.forEachIndexed { index, style ->
                    canvas.drawText(style, index * 360f + 12f, 28f, paint)
                    val image = requireNotNull(decodeBitmapFromFileBounded(File(artifacts, input.nameWithoutExtension + "-" + style + ".png").path, 500))
                    val scale = minOf(348f / image.width, 510f / image.height)
                    canvas.drawBitmap(image, null, android.graphics.RectF(index * 360f + 6f, 40f,
                        index * 360f + 6f + image.width * scale, 40f + image.height * scale), paint)
                    image.recycle()
                }
                File(artifacts, input.nameWithoutExtension + "-comparison.jpg").outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 93, it) }
                sheet.recycle()
            } finally { source.recycle() }
        }
        File(artifacts, "timings.json").writeText(timings.toString(2))
    }

    @Test fun editorReturnsToEdgesCancelsAndCommitsOnlyOnePageThenRestores() {
        val source = Bitmap.createBitmap(1800, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 180, 160)) }
        val record = repository.saveDocumentRecord((1..2).map { ScannedPage(sourceBitmap = source,
            editableQuad = fullQuad(), imageStyle = "color") })
        try {
            val before = repository.loadDocumentPages(record.id)
            val sourceFiles = before.map { File(it.sourceImagePath!!) }
            val modified = sourceFiles.map { it.lastModified() }
            ready(); openRecord(record.id, record.title)
            ui.onNodeWithText("···").performTouchInput { click() }
            ui.onNodeWithText("编辑").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("下一步").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("下一步").performClick()
            ui.onNodeWithText("图片处理").assertExists()
            listOf("黑白文档", "原图", "灰度", "白纸增强").forEach { ui.onNodeWithText(it).performScrollTo().performClick() }
            evidence("style-selection")
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("检测边缘").assertExists()
            ui.onNodeWithText("下一步").performClick()
            ui.onNodeWithText("白纸增强").assertIsSelected()
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            assertEquals("color", repository.loadDocumentPages(record.id).first().imageStyle)
            ui.onNodeWithText("···").performTouchInput { click() }
            ui.onNodeWithText("编辑").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("下一步").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("下一步").performClick()
            ui.onNodeWithText("灰度").performClick()
            val at = SystemClock.elapsedRealtime()
            ui.onNodeWithText("完成").performClick()
            ui.onNodeWithText("旋转").assertExists()
            val doneMs = SystemClock.elapsedRealtime() - at
            assertTrue("Done must not wait for full-size processing: " + doneMs, doneMs < 1800)
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ready(); openRecord(record.id, record.title)
            val after = repository.loadDocumentPages(record.id)
            assertEquals(listOf("grayscale", "color"), after.map { it.imageStyle })
            assertEquals(before.map { it.contentRevision }, after.map { it.contentRevision })
            assertEquals(modified, sourceFiles.map { it.lastModified() })
            val pdf = repository.ensureDocumentPdf(record.id)
            assertTrue(pdf.length() > 0)
            val prepared = repository.prepareDocumentPages(record.id)
            assertEquals(listOf("grayscale", "color"), prepared.map { it.imageStyle })
            pdf.copyTo(File(artifacts, "mixed-styles.pdf"), overwrite = true)
            android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                (0..1).forEach { index -> renderer.openPage(index).use { pdfPage ->
                    val rendered = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    pdfPage.render(rendered, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixel = rendered.getPixel(200, 280)
                    if (index == 0) assertTrue(kotlin.math.abs(Color.red(pixel) - Color.blue(pixel)) <= 2)
                    else assertTrue("Color page must keep its warm paper color", Color.red(pixel) > Color.blue(pixel) + 10)
                    File(artifacts, "pdf-page-" + index + ".png").outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    rendered.recycle()
                } }
            }
            val png = File(artifacts, "grayscale-export.png")
            writeSingleImageToFile(png, prepared.first(), ExportFileType.PNG, ExportFileSize.Actual)
            val exported = requireNotNull(BitmapFactory.decodeFile(png.path))
            val center = exported.getPixel(exported.width / 2, exported.height / 2)
            assertEquals(Color.red(center), Color.blue(center))
            exported.recycle()
            File(artifacts, "editor-metrics.json").writeText(JSONObject().put("doneMs", doneMs)
                .put("styles", JSONArray(after.map { it.imageStyle })).put("sourceUnchanged", true).toString(2))
            evidence("history-restored")
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun twentyPagesKeepIndependentStylesAndMissingCachesRebuild() {
        val source = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 190, 170)) }
        val record = repository.saveDocumentRecord((0..20).map { ScannedPage(sourceBitmap = source,
            editableQuad = fullQuad(), imageStyle = if (it == 0) "default" else styles[it % 5]) })
        try {
            val pages = repository.loadDocumentPages(record.id)
            val coordinator = cn.yuandianai.juziscan.history.DocumentSaveCoordinator.get(context)
            instrumentation.runOnMainSync {
                coordinator.attach(record.id, cn.yuandianai.juziscan.history.SavedDocumentState(record.updatedAtMillis, pages))
                var version = record.updatedAtMillis
                listOf("bw", "white", "grayscale").forEach { style ->
                    version = coordinator.commit(record.id, pages.mapIndexed { i, p -> if (i == 1) p.copy(imageStyle = style,
                        processedImagePath = null, previewBitmap = p.sourceBitmap) else p }, version).updatedAtMillis
                }
            }
            ui.waitUntil(60_000) { coordinator.documents.value[record.id]?.pages?.all { it.hasProcessedOutput } == true }
            val restored = repository.loadDocumentPages(record.id)
            assertEquals("grayscale", restored[1].imageStyle)
            assertEquals(pages.map { it.contentRevision }, restored.map { it.contentRevision })
            val current = coordinator.documents.value[record.id]!!.pages[1]
            assertEquals("grayscale", current.imageStyle)
            assertEquals(repository.processedCacheFileFor(record.id, restored[1])!!.path, current.processedImagePath)
            File(restored[1].processedImagePath!!).delete()
            val recovered = repository.prepareDocumentPages(record.id)
            assertTrue(recovered.all { it.hasProcessedOutput })
            assertEquals("grayscale", recovered[1].imageStyle)
            File(artifacts, "twenty-one-pages.json").writeText(JSONObject().put("pages", 21)
                .put("latestStyle", current.imageStyle).put("missingCacheRebuilt", true).toString(2))
            instrumentation.runOnMainSync { coordinator.detach(record.id) }
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun defaultSettingSurvivesRecreationAndOnlyNewCameraPagesUseIt() {
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("default_image_style", null)
        val before = repository.loadRecords().map { it.id }.toSet()
        try {
            prefs.edit().remove("default_image_style").commit()
            ready()
            ui.onNodeWithText("我的").performClick()
            ui.onNodeWithText("应用设置").performClick()
            ui.onNodeWithText("彩色增强").assertExists()
            ui.onNodeWithText("默认图片处理风格").performClick()
            ui.onNodeWithText("原图").performClick()
            assertEquals("original", prefs.getString("default_image_style", null))
            ready()
            ui.onNodeWithText("我的").performClick()
            ui.onNodeWithText("应用设置").performClick()
            ui.onNodeWithText("原图").assertExists()
            evidence("default-style-settings")
            ready()
            ui.onNodeWithText("文件扫描").performClick()
            enterCameraWhenReady(ui)
            ui.onNodeWithContentDescription("拍摄").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty() }
            val record = repository.loadRecords().single { it.id !in before }
            assertEquals("original", repository.loadDocumentPages(record.id).single().imageStyle)
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            ui.onNodeWithContentDescription("返回").performTouchInput { click() }
            ui.onNodeWithText("暂存并返回首页").performClick()
            ui.onNodeWithText("我的").performClick()
            ui.onNodeWithText("应用设置").performClick()
            ui.onNodeWithText("默认图片处理风格").performClick()
            ui.onNodeWithText("黑白文档").performClick()
            ready(); openRecord(record.id, record.title)
            ui.onNodeWithText("添加").performClick()
            enterCameraWhenReady(ui)
            ui.onNodeWithContentDescription("拍摄").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf("original", "bw"), repository.loadDocumentPages(record.id).map { it.imageStyle })
            evidence("continued-capture-mixed-defaults")
        } finally {
            if (previous == null) prefs.edit().remove("default_image_style").commit()
            else prefs.edit().putString("default_image_style", previous).commit()
            repository.loadRecords().filter { it.id !in before }.forEach { repository.deleteRecord(it.id) }
        }
    }

    @Test fun fullResolutionPreservesDimensionsAndLegacyGrayPixels() {
        val source = Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 200, 180)) }
        val canvas = android.graphics.Canvas(source)
        val paint = android.graphics.Paint().apply { color = Color.BLUE; strokeWidth = 2f }
        for (y in 100..3800 step 40) canvas.drawLine(100f, y.toFloat(), 2800f, y.toFloat(), paint)
        val record = repository.saveDocumentRecord(listOf("color", "white").map { ScannedPage(sourceBitmap = source,
            editableQuad = fullQuad(), correctionMode = cn.yuandianai.juziscan.scanner.SimpleDocumentScanner.CorrectionMode.FREE, imageStyle = it) })
        source.recycle()
        try {
            val start = SystemClock.elapsedRealtime()
            val prepared = repository.prepareDocumentPages(record.id)
            prepared.forEach { page ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(page.processedImagePath, bounds)
                assertTrue(bounds.outWidth >= 2900 && bounds.outHeight >= 3900)
            }
            File(artifacts, "full-resolution.json").writeText(JSONObject().put("sourceWidth", 3000).put("sourceHeight", 4000)
                .put("styles", "color,white").put("elapsedMs", SystemClock.elapsedRealtime() - start)
                .put("heapLimitBytes", Runtime.getRuntime().maxMemory()).toString(2))
        } finally { repository.deleteRecord(record.id) }
        val input = File(context.getExternalFilesDir(null), "document-style-inputs").listFiles()!!.first { it.extension == "png" }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(input.path))
        val corrected = cn.yuandianai.juziscan.scanner.SimpleDocumentScanner.perspectiveCorrect(bitmap, fullQuad(),
            cn.yuandianai.juziscan.scanner.SimpleDocumentScanner.CorrectionMode.AUTO, maxOutputSide = DOCUMENT_INPUT_MAX_SIDE)
        val expected = cn.yuandianai.juziscan.scanner.SimpleDocumentScanner.enhanceDocument(corrected)
        val output = File(artifacts, "legacy-default.png").apply { delete() }
        try {
            val page = processDocumentPage(context, ScannedPage(sourceBitmap = bitmap, editableQuad = fullQuad(), imageStyle = "default"), output)
            val actual = requireNotNull(BitmapFactory.decodeFile(output.path))
            assertTrue("Legacy default cache rebuild must retain the existing algorithm", expected.sameAs(actual))
            actual.recycle(); page.previewBitmap?.recycle()
        } finally {
            if (expected !== corrected) expected.recycle()
            if (corrected !== bitmap) corrected.recycle()
            bitmap.recycle()
        }
    }
}
