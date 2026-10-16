package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.history.HistoryRepository
import cn.yuandianai.juziscan.scan.ui.PreviewResultScreen
import cn.yuandianai.juziscan.scan.ui.ReorderScreen
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real rendered screens and persisted image files, with repeatable visual artifacts. */
@RunWith(AndroidJUnit4::class)
class HistoryQualityAndReorderE2eTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val artifacts get() = File(context.getExternalFilesDir(null), "history-rescan-e2e").apply { mkdirs() }

    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(artifacts, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun sixPageDragReachesLastCellAndSettlesAfterRelease() {
        val pages = (0..5).map { index ->
            val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.HSVToColor(floatArrayOf(index * 60f, 0.8f, 0.9f)))
            }
            ScannedPage(sourceBitmap = bitmap, processedBitmap = bitmap, previewBitmap = bitmap)
        }
        var result: List<ScannedPage>? = null
        ui.setContent { ReorderScreen(pages, onCancel = {}, onDone = { result = it }) }
        val cells = (1..6).map { ui.onNodeWithContentDescription("重新排序 $it").fetchSemanticsNode().boundsInRoot }
        // Separate event batches let real recompositions happen while the finger is down.
        ui.onRoot().performTouchInput { down(cells[0].center) }
        listOf(1, 2, 5).forEach { index ->
            ui.onRoot().performTouchInput { moveTo(cells[index].center, 300) }
            ui.waitForIdle()
        }
        ui.onRoot().performTouchInput { up() }
        ui.waitForIdle()
        (1..6).forEach { index ->
            val actual = ui.onNodeWithContentDescription("重新排序 $index").fetchSemanticsNode().boundsInRoot
            assertTrue("Card $index must settle in its cell", abs(actual.left - cells[index - 1].left) < 2f &&
                abs(actual.top - cells[index - 1].top) < 2f)
        }
        screenshot("reorder-six-pages-settled")
        ui.onNodeWithText("完成").performTouchInput { click() }
        assertEquals(pages.drop(1).map { it.id } + pages.first().id, requireNotNull(result).map { it.id })
    }

    @Test fun reopenedPageShowsFineDetailAndRetainsFullSizeExport() {
        val source = Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        for (x in 0 until source.width step 8) canvas.drawRect(x.toFloat(), 0f, (x + 4).toFloat(), 4000f, paint)
        val repository = HistoryRepository(context)
        val record = repository.saveDocumentRecord(listOf(ScannedPage(sourceBitmap = source,
            processedBitmap = source, previewBitmap = preparePreviewBitmap(source))))
        try {
            val pages = repository.loadDocumentPages(record.id)
            assertTrue(pages.all { it.processedBitmap == null && it.previewBitmap!!.height <= 640 })
            var shownPages by mutableStateOf(pages.map { it.copy(processedImagePath = null) })
            ui.setContent { PreviewResultScreen(shownPages, 0, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
            ui.waitForIdle()
            // The first camera preview is processed asynchronously without changing ID/revision.
            ui.runOnIdle { shownPages = shownPages.map { it.copy(processedBitmap = source) } }
            // Wait for asynchronous current-page loading, measuring the rendered pixels.
            var contrast = 0
            var transitions = 0
            ui.waitUntil(15_000) {
                val rendered = ui.onNodeWithContentDescription("扫描预览 1").captureToImage().asAndroidBitmap()
                val levels = (rendered.width / 4 until rendered.width * 3 / 4).map {
                    Color.red(rendered.getPixel(it, rendered.height / 2))
                }
                val sorted = levels.sorted()
                contrast = sorted[sorted.size * 9 / 10] - sorted[sorted.size / 10]
                transitions = levels.zipWithNext().count { (a, b) -> (a < 128) != (b < 128) }
                contrast > 100 && transitions > 300
            }
            screenshot("quality-reopened-preview")
            ui.runOnIdle { shownPages = pages }
            ui.waitForIdle()
            val export = File(artifacts, "quality-full-size.png")
            writeSingleImageToFile(export, pages.single(), ExportFileType.PNG, ExportFileSize.Actual)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(export.path, bounds)
            assertEquals(3000, bounds.outWidth)
            assertEquals(4000, bounds.outHeight)
            File(artifacts, "quality-metrics.json").writeText(JSONObject().put("exportWidth", bounds.outWidth)
                .put("exportHeight", bounds.outHeight).put("previewContrast", contrast).put("lineTransitions", transitions)
                .put("residentPreviewHeight", pages.single().previewBitmap!!.height).toString())
        } finally { repository.deleteRecord(record.id) }
    }
}
