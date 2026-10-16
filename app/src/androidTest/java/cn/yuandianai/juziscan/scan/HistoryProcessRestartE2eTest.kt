package cn.yuandianai.juziscan.scan

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run the three methods separately, with am force-stop between them (see runner). */
@RunWith(AndroidJUnit4::class)
class HistoryProcessRestartE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = HistoryRepository(context)
    private val artifacts get() = File(context.getExternalFilesDir(null), "history-rescan-e2e").apply { mkdirs() }
    private val fixture get() = File(artifacts, "process-document.json")

    @Before fun requireColdStartRunner() {
        assumeTrue("Use run-history-rescan-e2e.ps1 for separate processes",
            InstrumentationRegistry.getArguments().getString("coldStartPhase") == "true")
    }

    @After fun captureLastScreen() {
        if (InstrumentationRegistry.getArguments().getString("coldStartPhase") == "true") screenshot("process-last-screen")
    }

    private fun ready() {
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("我同意").performClick()
    }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(artifacts, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(id: String) {
        val record = repository.loadRecords().first { it.id == id }
        ui.waitUntil(30_000) { ui.onAllNodesWithText(record.title).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText(record.title)[0].performScrollTo().performTouchInput { click() }
        ui.waitUntil(60_000) { ui.onAllNodesWithText("添加").fetchSemanticsNodes().isNotEmpty() }
        idle()
    }

    private fun idle() = ui.waitUntil(90_000) { ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty() }
    private fun capture(count: Int) {
        ui.onNodeWithContentDescription("拍摄").performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun pdfCount(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { it.pageCount }
    }

    @Test fun captureThreeRealPages() {
        if (fixture.isFile) repository.deleteRecord(JSONObject(fixture.readText()).getString("id"))
        val started = System.currentTimeMillis()
        ready()
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        for (count in 1..3) capture(count)
        ui.onNodeWithContentDescription("已拍页面预览").performClick()
        idle()
        ui.waitUntil(90_000) { repository.loadRecords().any { it.createdAtMillis >= started && it.pageCount == 3 } }
        val record = repository.loadRecords().first { it.createdAtMillis >= started && it.pageCount == 3 }
        val pages = repository.loadDocumentPages(record.id)
        assertEquals(3, pdfCount(File(record.filePath)))
        fixture.writeText(JSONObject().put("id", record.id).put("createdAtMillis", record.createdAtMillis)
            .put("threePid", Process.myPid()).put("originalIds", JSONArray(pages.map { it.id }))
            .put("originalHashes", JSONArray(pages.map { digest(File(requireNotNull(it.sourceImagePath))) })).toString())
        screenshot("process-three-real-pages")
    }

    @Test fun reopenThreeAppendAutoAndManualPages() {
        val metadata = JSONObject(fixture.readText())
        assertNotEquals(metadata.getInt("threePid"), Process.myPid())
        val id = metadata.getString("id")
        assertEquals(3, repository.loadDocumentPages(id).size)
        ready()
        open(id)
        ui.onNodeWithText("1 / 3").assertExists()
        ui.onNodeWithText("添加").performTouchInput { click() }
        enterCameraWhenReady(ui)
        ui.onNodeWithText("自动拍").performTouchInput { click() }
        ui.waitUntil(60_000) { ui.onAllNodesWithText("4").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("智能手拍").performTouchInput { click() }
        capture(5)
        ui.onNodeWithContentDescription("已拍页面预览").performClick()
        idle()
        ui.waitUntil(90_000) { repository.loadRecords().any { it.id == id && it.pageCount == 5 } }
        val record = repository.loadRecords().first { it.id == id }
        val pages = repository.loadDocumentPages(id)
        assertEquals(metadata.getLong("createdAtMillis"), record.createdAtMillis)
        assertEquals((0..2).map { metadata.getJSONArray("originalIds").getString(it) }, pages.take(3).map { it.id })
        assertEquals((0..2).map { metadata.getJSONArray("originalHashes").getString(it) },
            pages.take(3).map { digest(File(requireNotNull(it.sourceImagePath))) })
        assertEquals(1, repository.loadRecords().count { it.id == id })
        assertEquals(5, pdfCount(File(record.filePath)))
        metadata.put("fivePid", Process.myPid()).put("fiveIds", JSONArray(pages.map { it.id }))
            .put("autoCaptureVerified", true)
        fixture.writeText(metadata.toString())
        screenshot("process-same-document-five-pages")
    }

    @Test fun reopenFiveNoOpShareAndPrint() {
        val metadata = JSONObject(fixture.readText())
        assertNotEquals(metadata.getInt("fivePid"), Process.myPid())
        val id = metadata.getString("id")
        val record = repository.loadRecords().first { it.id == id }
        val pages = repository.loadDocumentPages(id)
        assertEquals((0..4).map { metadata.getJSONArray("fiveIds").getString(it) }, pages.map { it.id })
        assertEquals(5, pdfCount(File(record.filePath)))
        val files = File(requireNotNull(record.documentManifestPath)).parentFile!!.walkTopDown().filter { it.isFile }.toList() +
            File(context.filesDir, "history/records.json")
        val timestamps = files.associate { it.path to it.lastModified() }
        ready()
        open(id)
        ui.onNodeWithText("1 / 5").assertExists()
        screenshot("process-five-pages-after-cold-start")
        instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("最近文档").assertExists()
        assertEquals(timestamps, files.associate { it.path to it.lastModified() })
        ui.runOnIdle { repository.shareRecord(record) }
        ui.waitUntil(15_000) { ui.activityRule.scenario.state != Lifecycle.State.RESUMED }
        assertEquals(5, pdfCount(File(context.cacheDir, record.fileName)))
        screenshot("process-five-page-system-share")
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        ui.waitUntil(15_000) { ui.activityRule.scenario.state == Lifecycle.State.RESUMED }
        open(id)
        val printStarted = System.currentTimeMillis()
        ui.onNodeWithText("打印").performTouchInput { click() }
        ui.waitUntil(15_000) { ui.activityRule.scenario.state != Lifecycle.State.RESUMED }
        val printed = context.cacheDir.listFiles().orEmpty().first { it.name.endsWith("-print.pdf") && it.lastModified() >= printStarted }
        assertEquals(5, pdfCount(printed))
        screenshot("process-five-page-system-print")
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        File(record.filePath).copyTo(File(artifacts, "five-page-document.pdf"), overwrite = true)
        metadata.put("verifiedPid", Process.myPid()).put("pdfPages", 5).put("shareOpened", true)
            .put("printOpened", true).put("noOpFilesUnchanged", true)
        fixture.writeText(metadata.toString())
    }
}
