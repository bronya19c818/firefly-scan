package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real shutter, rendered navigation and durable files; artifacts are overwritten on rerun. */
@RunWith(AndroidJUnit4::class)
class ImmediateSaveE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = HistoryRepository(context)
    private val artifacts get() = File(context.getExternalFilesDir(null), "immediate-save-e2e").apply { mkdirs() }

    private fun ready() {
        ui.activityRule.scenario.recreate()
        instrumentation.runOnMainSync { ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("我同意").performClick()
    }

    private fun evidence(name: String) {
        ui.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300) // Platform dialog fade is outside Compose idling; timings are recorded before this.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(artifacts, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun fiveCapturesAreDurableBeforePreviewAndExitDoesNotWait() {
        ready()
        val before = repository.loadRecords().map { it.id }.toSet()
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        try {
            val captureTimes = org.json.JSONArray()
            for (count in 1..5) {
                val captureAt = SystemClock.elapsedRealtime()
                ui.onNodeWithContentDescription("拍摄").performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
                captureTimes.put(SystemClock.elapsedRealtime() - captureAt)
                assertTrue("Acknowledged capture must be durable before preview", repository.loadRecords().any {
                    it.id !in before && it.pageCount == count
                })
            }
            val record = repository.loadRecords().single { it.id !in before }
            assertFalse("Capture must not generate a PDF", File(record.filePath).exists())
            val pages = repository.loadDocumentPages(record.id)
            assertEquals(5, pages.size)
            assertTrue(pages.all { File(requireNotNull(it.sourceImagePath)).length() > 0 })
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(pages.last().sourceImagePath, bounds)
            assertTrue("Original camera resolution must be preserved", maxOf(bounds.outWidth, bounds.outHeight) >= 3000)
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            val backAt = SystemClock.elapsedRealtime()
            ui.onNodeWithContentDescription("返回").performTouchInput { click() }
            ui.onNodeWithText("暂存并返回首页").assertExists()
            val dialogMs = SystemClock.elapsedRealtime() - backAt
            ui.onNodeWithText("暂存并返回首页").performClick()
            ui.onNodeWithText("最近文档").assertExists()
            val homeMs = SystemClock.elapsedRealtime() - backAt
            assertTrue("Dialog should open immediately: $dialogMs ms", dialogMs < 1500)
            assertTrue("Exit should not wait for enhancement: $homeMs ms", homeMs < 2500)
            evidence("five-pages-immediate-home")
            File(artifacts, "navigation.json").writeText(JSONObject().put("documentId", record.id)
                .put("dialogMs", dialogMs).put("homeMs", homeMs).put("pages", 5)
                .put("captureAckMs", captureTimes).put("originalWidth", bounds.outWidth)
                .put("originalHeight", bounds.outHeight).toString())
            ready()
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-${record.id}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasText(record.title) and hasAnyAncestor(hasTestTag("history-${record.id}")))
                .performScrollTo().performTouchInput { click() }
            ui.waitUntil(20_000) { ui.onAllNodesWithText("1 / 5").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("1 / 5").assertExists()
            assertEquals(pages.map { it.id }, repository.loadDocumentPages(record.id).map { it.id })
            evidence("five-pages-reopened")
        } finally { repository.loadRecords().filter { it.id !in before }.forEach { repository.deleteRecord(it.id) } }
    }

    @Test fun saveDoesNotBuildPdfAndRapidHistoryEditsSurviveExit() {
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val record = repository.saveDocumentRecord((1..3).map {
            ScannedPage(sourceBitmap = bitmap, processedBitmap = bitmap, previewBitmap = bitmap)
        })
        try {
            assertFalse("Saving metadata must not create PDF", File(record.filePath).exists())
            ready()
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-${record.id}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasText(record.title) and hasAnyAncestor(hasTestTag("history-${record.id}")))
                .performScrollTo().performTouchInput { click() }
            ui.waitUntil(20_000) { ui.onAllNodesWithText("旋转").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("旋转").performTouchInput { click() }
            ui.onNodeWithText("旋转").performTouchInput { click() }
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("最近文档").assertExists()
            assertEquals(180f, repository.loadDocumentPages(record.id).first().rotationDegrees)
            assertFalse(File(repository.loadRecords().first { it.id == record.id }.filePath).exists())
            evidence("rapid-edits-home")
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun newScanImmediatelyOwnsDifferentDocumentWhileOldWorkContinues() {
        ready()
        val before = repository.loadRecords().map { it.id }.toSet()
        try {
            ui.onNodeWithText("文件扫描").performTouchInput { click() }
            enterCameraWhenReady(ui)
            for (count in 1..3) {
                ui.onNodeWithContentDescription("拍摄").performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
            }
            val first = repository.loadRecords().single { it.id !in before }
            val firstIds = repository.loadDocumentPages(first.id).map { it.id }
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("继续浏览").performClick()
            ui.onNodeWithText("预览").assertExists()
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            val start = SystemClock.elapsedRealtime()
            ui.onNodeWithText("新的扫描").performClick()
            ui.onNodeWithContentDescription("拍摄").assertExists()
            assertTrue(SystemClock.elapsedRealtime() - start < 2500)
            enterCameraWhenReady(ui)
            ui.onNodeWithContentDescription("拍摄").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty() }
            val documents = repository.loadRecords().filter { it.id !in before }
            assertEquals(2, documents.size)
            val second = documents.single { it.id != first.id }
            assertEquals(1, second.pageCount)
            assertEquals(firstIds, repository.loadDocumentPages(first.id).map { it.id })
            assertTrue(repository.loadDocumentPages(second.id).none { it.id in firstIds })
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("暂存并返回首页").performClick()
            ui.waitUntil(90_000) { repository.loadDocumentPages(first.id).all { it.hasProcessedOutput } }
            assertFalse(File(first.filePath).exists())
            assertFalse(File(second.filePath).exists())
            evidence("independent-documents-background-complete")
        } finally { repository.loadRecords().filter { it.id !in before }.forEach { repository.deleteRecord(it.id) } }
    }

    @Test fun manifestFailureRetainsPendingEditAndCanRetryFromHome() {
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val record = repository.saveDocumentRecord(listOf(ScannedPage(sourceBitmap = bitmap,
            processedBitmap = bitmap, previewBitmap = bitmap)))
        val blocker = File(requireNotNull(record.documentManifestPath) + ".new")
        var owned = false
        try {
            ready()
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-${record.id}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasText(record.title) and hasAnyAncestor(hasTestTag("history-${record.id}")))
                .performScrollTo().performTouchInput { click() }
            ui.waitUntil(20_000) { ui.onAllNodesWithText("旋转").fetchSemanticsNodes().isNotEmpty() }
            owned = blocker.mkdir()
            check(owned)
            File(blocker, "owned-by-test").writeText("manifest unavailable")
            ui.onNodeWithText("旋转").performTouchInput { click() }
            ui.onNodeWithText("文档未保存").assertExists()
            assertEquals(0f, repository.loadDocumentPages(record.id).first().rotationDegrees)
            // Another action must not discard the failed operation's retry snapshot.
            ui.onNodeWithText("旋转").performTouchInput { click() }
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("最近文档").assertExists()
            check(blocker.deleteRecursively())
            owned = false
            ui.onNodeWithText("重试").performClick()
            ui.waitUntil(20_000) { repository.loadDocumentPages(record.id).first().rotationDegrees == 90f }
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            evidence("failed-edit-retried-from-home")
        } finally {
            if (owned) blocker.deleteRecursively()
            repository.deleteRecord(record.id)
        }
    }
}
