package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a camera device pointed at a well-lit paper document. */
@RunWith(AndroidJUnit4::class)
class SmartManualCaptureE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val artifacts get() = File(
        instrumentation.targetContext.getExternalFilesDir(null), "smart-manual-e2e"
    ).apply { mkdirs() }

    private fun screenshot(name: String) {
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
            File(artifacts, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @After fun saveDeviceEvidence() {
        screenshot("final")
        File(artifacts, "device.txt").writeText(
            "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\n${Build.FINGERPRINT}\n"
        )
    }

    @Test fun recognizedManualDocumentWaitsForShutterAndCollectsFivePages() {
        val startedAt = System.currentTimeMillis()
        ui.activityRule.scenario.recreate()
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("我同意").performClick()
        }
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        ui.onNodeWithText("智能手拍").assertExists()
        ui.waitUntil(60_000) {
            ui.onAllNodesWithText("已识别文档，点击快门拍摄").fetchSemanticsNodes().isNotEmpty()
        }
        SystemClock.sleep(6_000)
        ui.onNodeWithContentDescription("拍摄").assertExists()
        ui.onNodeWithContentDescription("已拍页面预览").assertDoesNotExist()
        screenshot("manual-stable-six-seconds")
        for (count in 1..5) {
            ui.onNodeWithContentDescription("拍摄").performClick()
            ui.waitUntil(20_000) {
                ui.onAllNodesWithText(count.toString()).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithText("智能手拍").assertExists()
            screenshot("page-$count")
        }
        ui.onNodeWithContentDescription("已拍页面预览").performClick()
        ui.waitUntil(60_000) {
            ui.onAllNodesWithText("智能手拍").fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithText("预览").assertExists()
        ui.onNodeWithText("重新排序").assertExists()
        ui.waitUntil(60_000) {
            ui.onAllNodesWithText("正在", substring = true).fetchSemanticsNodes().isEmpty()
        }
        screenshot("five-page-preview")
        val history = HistoryRepository(instrumentation.targetContext)
        ui.waitUntil(20_000) {
            history.loadRecords().any { it.createdAtMillis >= startedAt && it.pageCount == 5 }
        }
        val saved = history.loadRecords().first { it.createdAtMillis >= startedAt && it.pageCount == 5 }
        ParcelFileDescriptor.open(File(saved.filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { assertEquals(5, it.pageCount) }
        }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(30_000) { ui.onAllNodesWithText(saved.title).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText(saved.title)[0].performScrollTo().performTouchInput { click() }
        ui.waitUntil(60_000) {
            ui.onAllNodesWithText("添加").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("预览").assertExists()
        assertEquals(Lifecycle.State.RESUMED, ui.activityRule.scenario.state)
        screenshot("recent-document-opened")
    }
}
