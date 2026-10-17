package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.os.Process
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImmediateProcessRestartE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repo get() = HistoryRepository(context)
    private val dir get() = File(context.getExternalFilesDir(null), "immediate-save-e2e").apply { mkdirs() }
    private val fixture get() = File(dir, "restart-document.json")

    @Before fun dedicatedRunner() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("immediatePhase") == "true")
        instrumentation.runOnMainSync { ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("我同意").performClick()
    }
    private fun digest(path: String) = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes())
        .joinToString("") { "%02x".format(it) }
    private fun evidence(name: String) {
        ui.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun open(id: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-$id").fetchSemanticsNodes().isNotEmpty() }
        val record = repo.loadRecords().first { it.id == id }
        ui.onNode(hasText(record.title) and hasAnyAncestor(hasTestTag("history-$id")))
            .performScrollTo().performTouchInput { click() }
        ui.waitUntil(20_000) { ui.onAllNodesWithText("添加").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The runner force-stops this process once the durable checkpoint exists, during the sleep. */
    @Test fun captureAndWaitForForceStop() {
        if (fixture.isFile) {
            val previous = JSONObject(fixture.readText())
            if (previous.optString("owner") == "immediate-save-e2e") repo.deleteRecord(previous.getString("id"))
        }
        val before = repo.loadRecords().map { it.id }.toSet()
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        for (count in 1..5) {
            ui.onNodeWithContentDescription("拍摄").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty() }
        }
        val record = repo.loadRecords().single { it.id !in before }
        val pages = repo.loadDocumentPages(record.id)
        val value = JSONObject().put("owner", "immediate-save-e2e").put("id", record.id)
            .put("capturePid", Process.myPid()).put("ids", JSONArray(pages.map { it.id }))
            .put("hashes", JSONArray(pages.map { digest(requireNotNull(it.sourceImagePath)) }))
            .put("pendingAtCheckpoint", pages.count { !it.hasProcessedOutput })
        fixture.writeText(value.toString())
        evidence("before-force-stop-without-preview")
        File(dir, "force-stop-ready").writeText("ready")
        SystemClock.sleep(30_000)
        fail("Runner must force-stop after checkpoint")
    }

    @Test fun recoverThenEditAndReturnImmediately() {
        val value = JSONObject(fixture.readText())
        val id = value.getString("id")
        assertNotEquals(value.getInt("capturePid"), Process.myPid())
        val pages = repo.loadDocumentPages(id)
        assertEquals((0..4).map { value.getJSONArray("ids").getString(it) }, pages.map { it.id })
        assertEquals((0..4).map { value.getJSONArray("hashes").getString(it) }, pages.map { digest(requireNotNull(it.sourceImagePath)) })
        open(id)
        ui.onNodeWithText("重新排序").performTouchInput { click() }
        val first = ui.onNodeWithContentDescription("重新排序 1").fetchSemanticsNode().boundsInRoot.center
        val third = ui.onNodeWithContentDescription("重新排序 3").fetchSemanticsNode().boundsInRoot.center
        ui.onRoot().performTouchInput { swipe(first, third, 900) }
        ui.onNodeWithText("完成").performTouchInput { click() }
        ui.onNodeWithText("旋转").performTouchInput { click() }
        ui.onNodeWithContentDescription("更多操作").performTouchInput { click() }
        ui.onNodeWithText("编辑").performTouchInput { click() }
        ui.waitUntil(20_000) { ui.onAllNodesWithText("铺满").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("铺满").performTouchInput { click() }
        ui.onNodeWithText("下一步").performTouchInput { click() }
        ui.onNodeWithText("图片处理").assertExists()
        ui.onNodeWithText("完成").performTouchInput { click() }
        ui.onNodeWithContentDescription("扫描预览 1").performTouchInput { swipeLeft() }
        ui.onNodeWithContentDescription("更多操作").performTouchInput { click() }
        ui.onNodeWithText("删除").performTouchInput { click() }
        instrumentation.runOnMainSync { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("最近文档").assertExists()
        val edited = repo.loadDocumentPages(id)
        value.put("editPid", Process.myPid()).put("editedIds", JSONArray(edited.map { it.id }))
            .put("editedFingerprint", documentFingerprint(edited))
        fixture.writeText(value.toString())
        evidence("edited-immediate-home")
    }

    @Test fun recoverEditsAfterSecondProcessTermination() {
        val value = JSONObject(fixture.readText())
        val id = value.getString("id")
        try {
            assertNotEquals(value.getInt("editPid"), Process.myPid())
            val edited = repo.loadDocumentPages(id)
            assertEquals(4, edited.size)
            assertEquals(90f, edited.first().rotationDegrees)
            assertEquals(4, edited.first().editableQuad.size)
            assertEquals((0..3).map { value.getJSONArray("editedIds").getString(it) }, edited.map { it.id })
            assertEquals(value.getString("editedFingerprint"), documentFingerprint(edited))
            assertFalse(File(repo.loadRecords().first { it.id == id }.filePath).exists())
            open(id)
            ui.onNodeWithText("1 / 4").assertExists()
            evidence("edits-restored-after-second-stop")
        } finally { repo.deleteRecord(id) }
    }
}
