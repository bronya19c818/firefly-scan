package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import cn.yuandianai.juziscan.history.DocumentSaveCoordinator
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRescanE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val repository get() = HistoryRepository(instrumentation.targetContext)

    private fun seed() = repository.saveDocumentRecord(listOf(Color.RED, Color.GREEN, Color.BLUE).map { color ->
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        ScannedPage(sourceBitmap = bitmap, processedBitmap = bitmap, previewBitmap = bitmap)
    })

    private fun ready() {
        ui.activityRule.scenario.recreate()
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("我同意").performClick()
        }
    }

    private fun open(id: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("history-$id").fetchSemanticsNodes().isNotEmpty() }
        val title = repository.loadRecords().first { it.id == id }.title
        ui.onNode(hasText(title) and hasAnyAncestor(hasTestTag("history-$id")))
            .performScrollTo().performTouchInput { click() }
        ui.waitUntil(60_000) { ui.onAllNodesWithText("添加").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(60_000) { ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("预览").assertExists()
    }

    private fun evidence(name: String) {
        ui.waitForIdle()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "history-rescan-e2e").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        File(dir, "device.txt").writeText("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\n")
    }

    @Test fun recentAndAllDocumentsRestorePreviewAndRotationWithoutNoOpWrites() {
        val record = seed()
        try {
            ready()
            open(record.id)
            ui.onNodeWithText("1 / 3").assertExists()
            assertEquals(record.updatedAtMillis, repository.loadRecords().first { it.id == record.id }.updatedAtMillis)
            ui.onNodeWithText("旋转").performTouchInput { click() }
            ui.waitUntil(60_000) { repository.loadRecords().first { it.id == record.id }.updatedAtMillis > record.updatedAtMillis }
            evidence("recent-three-pages-rotated")
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("最近文档").assertExists()
            ready()
            ui.onNodeWithText("查看全部").performScrollTo().performTouchInput { click() }
            open(record.id)
            ui.onNodeWithText("1 / 3").assertExists()
            assertEquals(90f, repository.loadDocumentPages(record.id)[0].rotationDegrees)
            evidence("all-documents-restored")
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("全部文档").assertExists()
        } finally { repository.deleteRecord(record.id) }
    }

    /** Requires real camera; fixture pages are followed by two real shutter captures. */
    @Test fun addTwoCameraPagesToExistingThreeAndReopenSameFivePageDocument() {
        val record = seed()
        try {
            ready()
            open(record.id)
            ui.onNodeWithText("添加").performTouchInput { click() }
            enterCameraWhenReady(ui)
            ui.onNodeWithText("智能手拍").assertExists()
            for (count in 4..5) {
                ui.onNodeWithContentDescription("拍摄").performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
            }
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            ui.waitUntil(90_000) { repository.loadRecords().any { it.id == record.id && it.pageCount == 5 } }
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            evidence("same-document-five-pages")
            ready()
            open(record.id)
            ui.onNodeWithText("1 / 5").assertExists()
            evidence("five-pages-reopened")
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun reorderCropCancelAndDeleteAllPersistThroughExistingControls() {
        val record = seed()
        try {
            ready()
            open(record.id)
            ui.onNodeWithText("1 / 3").assertExists()
            val originalIds = repository.loadDocumentPages(record.id).map { it.id }
            ui.onNodeWithText("重新排序").performTouchInput { click() }
            val first = ui.onNodeWithContentDescription("重新排序 1").fetchSemanticsNode().boundsInRoot.center
            val last = ui.onNodeWithContentDescription("重新排序 3").fetchSemanticsNode().boundsInRoot.center
            ui.onRoot().performTouchInput { swipe(first, last, 900) }
            ui.onNodeWithText("完成").performTouchInput { click() }
            ui.waitUntil(60_000) { repository.loadDocumentPages(record.id).map { it.id } != originalIds }
            val ordered = repository.loadDocumentPages(record.id).map { it.id }
            assertEquals(originalIds.toSet(), ordered.toSet())
            ui.waitUntil(60_000) { ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithText("···").performTouchInput { click() }
            ui.onNodeWithText("编辑").performTouchInput { click() }
            ui.waitUntil(60_000) { ui.onAllNodesWithText("检测边缘").fetchSemanticsNodes().isNotEmpty() }
            // Cancel must preserve the committed record and its timestamp.
            val beforeCancel = repository.loadRecords().first { it.id == record.id }
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            assertEquals(beforeCancel, repository.loadRecords().first { it.id == record.id })
            ui.onNodeWithText("···").performTouchInput { click() }
            ui.onNodeWithText("编辑").performTouchInput { click() }
            ui.waitUntil(60_000) { ui.onAllNodesWithText("铺满").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("铺满").performTouchInput { click() }
            ui.onNodeWithText("下一步").performTouchInput { click() }
            ui.waitUntil(60_000) { repository.loadRecords().first { it.id == record.id }.updatedAtMillis > beforeCancel.updatedAtMillis }
            val edited = repository.loadDocumentPages(record.id)
            assertEquals(ordered, edited.map { it.id })
            assertEquals(defaultDocumentQuad().map { it.x to it.y }, edited[0].editableQuad.map { it.x to it.y })
            evidence("reordered-cropped")
            for (remaining in 2 downTo 0) {
                ui.waitUntil(60_000) { ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("···").performTouchInput { click() }
                ui.onNodeWithText("删除").performTouchInput { click() }
                ui.waitUntil(60_000) {
                    val saved = repository.loadRecords().firstOrNull { it.id == record.id }
                    if (remaining == 0) saved == null else saved?.pageCount == remaining
                }
            }
            // The durable tombstone hides the record before disposable-file cleanup finishes.
            // Acknowledgement is the completed UI return, then no old task may recreate files.
            ui.waitUntil(20_000) { ui.onAllNodesWithText("最近文档").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(File(instrumentation.targetContext.filesDir, "history/documents/${record.id}").exists())
            evidence("deleted-last-page")
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun failedAutoSaveKeepsOriginalAndRetryUpdatesSameRecord() {
        val record = seed()
        val blocked = File(instrumentation.targetContext.filesDir, "history/records.json.new")
        var ownsBlocker = false
        try {
            ready()
            open(record.id)
            // 失败保存的对照物是已提交的清单（PDF 已改为按需生成，保存时不再存在）。
            val previousManifest = File(requireNotNull(record.documentManifestPath)).readBytes()
            ownsBlocker = blocked.mkdir()
            check(ownsBlocker)
            File(blocked, "blocked").writeText("simulate index write failure")
            ui.onNodeWithText("旋转").performTouchInput { click() }
            ui.waitUntil(60_000) { ui.onAllNodesWithText("文档未保存").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("索引失败时旋转仍可从可靠清单恢复", 90f,
                repository.loadDocumentPages(record.id).first().rotationDegrees)
            assertFalse(previousManifest.contentEquals(File(requireNotNull(record.documentManifestPath)).readBytes()))
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("最近文档").assertExists()
            ui.onNodeWithText("文档未保存").assertExists()
            evidence("save-failed-original-retained")
            check(blocked.deleteRecursively())
            ownsBlocker = false
            ui.onNodeWithText("重试").performClick()
            ui.waitUntil(60_000) { repository.loadRecords().first { it.id == record.id }.updatedAtMillis > record.updatedAtMillis }
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            assertEquals(90f, repository.loadDocumentPages(record.id)[0].rotationDegrees)
            evidence("retry-same-record-saved")
        } finally {
            if (ownsBlocker) blocked.deleteRecursively()
            repository.deleteRecord(record.id)
        }
    }

    @Test fun missingCachesAreRebuiltOnReopenWithoutLosingOriginal() {
        val record = seed()
        val started = System.currentTimeMillis()
        val blocked = File(instrumentation.targetContext.filesDir, "history/records.json.new")
        var ownsBlocker = false
        try {
            ready()
            open(record.id)
            val originalIds = repository.loadDocumentPages(record.id).map { it.id }.toSet()
            ui.onNodeWithText("添加").performTouchInput { click() }
            enterCameraWhenReady(ui)
            ownsBlocker = blocked.mkdir()
            check(ownsBlocker)
            File(blocked, "blocked").writeText("simulate failed index commit")
            for (count in 4..5) {
                ui.onNodeWithContentDescription("拍摄").performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
            }
            ui.onNodeWithContentDescription("已拍页面预览").performClick()
            ui.waitUntil(90_000) { ui.onAllNodesWithText("文档未保存").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(5, repository.loadRecords().first { it.id == record.id }.pageCount)
            // Only remove this fixture's derived caches; immutable originals stay in place.
            val cachePages = File(File(requireNotNull(record.documentManifestPath)).parentFile, "cache/pages")
            val addedIds = repository.loadDocumentPages(record.id).map { it.id }.toSet() - originalIds
            assertEquals(2, addedIds.size)
            val coordinator = DocumentSaveCoordinator.get(instrumentation.targetContext)
            fun stagedCaches(): List<File> = cachePages.listFiles().orEmpty()
                .filter { it.isDirectory && it.name !in originalIds }
                .flatMap { it.listFiles().orEmpty().toList() }
            ui.waitUntil(90_000) {
                addedIds.all { id -> File(cachePages, id).listFiles().orEmpty().any { it.extension == "png" && it.length() > 0 } } &&
                    coordinator.phases.value[record.id].orEmpty().values.none {
                        it == PageProcessingPhase.PENDING || it == PageProcessingPhase.PROCESSING
                    }
            }
            instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithText("最近文档").assertExists()
            ui.waitUntil(30_000) { record.id !in coordinator.documents.value }
            val staged = stagedCaches()
            assertTrue("新增页面必须先落盘处理缓存", staged.isNotEmpty())
            staged.forEach { check(it.delete()) { "无法删除处理缓存：${it.name}" } }
            assertTrue(addedIds.all { id -> File(cachePages, id).listFiles().orEmpty().none { it.extension == "png" } })
            check(blocked.deleteRecursively())
            ownsBlocker = false
            // A finishing page may already retry the index and clear the banner. Reopen through
            // the real history flow to discover missing cache files and enqueue their rebuild.
            open(record.id)
            ui.waitUntil(90_000) { repository.loadRecords().any { it.id == record.id && it.pageCount == 5 } }
            // 处理缓存丢失后由后台逐页补齐，直到每页都有处理图。
            ui.waitUntil(90_000) {
                (originalIds + addedIds).all { id ->
                    File(cachePages, id).listFiles().orEmpty().any { it.extension == "png" && it.length() > 0 }
                }
            }
            ui.waitUntil(30_000) { ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty() }
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            val restored = repository.loadDocumentPages(record.id)
            assertEquals(5, restored.size)
            assertTrue("原三页 ID 必须保留", restored.map { it.id }.containsAll(originalIds))
            restored.forEach { page ->
                assertTrue("原图不得丢失", File(requireNotNull(page.sourceImagePath)).isFile)
                assertNotNull("处理缓存必须重建", page.processedImagePath)
                assertTrue(File(requireNotNull(page.processedImagePath)).isFile)
            }
            assertTrue(File(instrumentation.targetContext.cacheDir, "document-page-processing")
                .listFiles().orEmpty().none { it.lastModified() >= started })
            evidence("missing-cache-rebuilt-same-document")
        } finally {
            if (ownsBlocker) blocked.deleteRecursively()
            repository.deleteRecord(record.id)
        }
    }
}
