package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, real navigation and system photo picker; retains user documents. */
@RunWith(AndroidJUnit4::class)
class FireflyIconNavigationE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val artifacts get() = File(instrumentation.targetContext.getExternalFilesDir(null), "firefly-icons-e2e").apply { mkdirs() }

    private fun ready() {
        instrumentation.runOnMainSync { ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (ui.onAllNodesWithText("我同意").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("我同意").performClick()
    }

    private fun evidence(name: String) {
        ui.waitForIdle()
        val bitmap = if (name.contains("picker")) {
            requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        } else {
            ui.onRoot().captureToImage().asAndroidBitmap()
        }
        try { File(artifacts, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private fun back() = ui.onNodeWithContentDescription("返回").performTouchInput { click() }

    private fun systemBack() {
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        instrumentation.waitForIdleSync()
    }

    private fun awaitPicker() {
        ui.waitUntil(15_000) {
            val pkg = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
            pkg != instrumentation.targetContext.packageName && listOf("photo", "media", "documents").any { pkg.contains(it, ignoreCase = true) }
        }
        instrumentation.uiAutomation.waitForIdle(500, 10_000)
    }

    @Test fun homeToolsMineAndCameraKeepTheirRoutes() {
        ready()
        ui.onNode(hasText("相册导入") and hasClickAction()).assertExists()
        evidence("home")
        ui.onNodeWithText("更多工具").performTouchInput { click() }
        ui.onNodeWithText("全部工具").assertExists()
        listOf(
            listOf("文件扫描", "文字识别", "证件扫描", "表格识别"),
            listOf("添加水印", "长图拼接", "图片转PDF", "图片压缩")
        ).forEach { labels ->
            val bounds = labels.map { ui.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
            assertTrue("Four tools must share one row: $labels", bounds.maxOf { it.top } - bounds.minOf { it.top } < 2f)
            assertTrue("Tools keep their order: $labels", bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
        }
        evidence("tools")
        listOf("添加水印", "长图拼接", "图片转PDF", "图片压缩", "二维码工具", "WiFi传图").forEachIndexed { index, label ->
            ui.onNodeWithText(label).performScrollTo().performTouchInput { click() }
            if (index < 4) {
                awaitPicker()
                evidence("tool-picker-$index")
                systemBack()
                ui.onNodeWithText("全部工具").assertExists()
                return@forEachIndexed
            }
            ui.onNodeWithText("全部工具").assertDoesNotExist()
            val title = when (label) {
                "图片转PDF" -> "图片转 PDF"
                "WiFi传图" -> "WiFi 传图"
                else -> label
            }
            assertTrue("Tool page must open: $title", ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
            evidence("tool-$index")
            back()
            ui.onNodeWithText("全部工具").assertExists()
        }
        back()
        ui.onNodeWithText("我的").performTouchInput { click() }
        ui.onNodeWithText("账号与安全").assertExists()
        evidence("mine")
        ui.onNode(hasText("开通高级会员") or hasText("高级会员 · 尊享权益")).performTouchInput { click() }
        evidence("membership")
        ui.onNodeWithContentDescription("关闭").performTouchInput { click() }
        ui.onNodeWithText("应用设置").performTouchInput { click() }
        evidence("settings")
        back()
        ui.onNodeWithText("首页").performTouchInput { click() }
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        ui.onNodeWithContentDescription("拍摄").assertExists()
        evidence("camera")
    }

    @Test fun documentActionsKeepPreviewReorderEditAndExportRoutes() {
        val repository = HistoryRepository(instrumentation.targetContext)
        val source = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(225, 240, 235)) }
        val record = repository.saveDocumentRecord((1..2).map {
            ScannedPage(sourceBitmap = source, editableQuad = listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)), imageStyle = "color")
        })
        try {
            ui.activityRule.scenario.recreate()
            ready()
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("history-" + record.id).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasText(record.title) and hasAnyAncestor(hasTestTag("history-" + record.id))).performScrollTo().performTouchInput { click() }
            ui.waitUntil(20_000) { ui.onAllNodesWithText("旋转").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("旋转").assertExists()
            evidence("preview")
            ui.onNodeWithText("重新排序").performTouchInput { click() }
            evidence("reorder")
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithContentDescription("更多操作").performTouchInput { click() }
            evidence("page-actions")
            ui.onNodeWithText("编辑").performTouchInput { click() }
            ui.waitUntil(20_000) { ui.onAllNodesWithText("下一步").fetchSemanticsNodes().isNotEmpty() }
            evidence("crop")
            ui.onNodeWithText("下一步").performClick()
            ui.onNodeWithText("图片处理").assertExists()
            evidence("styles")
            back()
            back()
            ui.onNodeWithText("导出").performTouchInput { click() }
            ui.onNodeWithText("导出分享").assertExists()
            evidence("export")
            ui.onNodeWithText("文件类型").performTouchInput { click() }
            evidence("export-format")
        } finally {
            repository.deleteRecord(record.id)
            source.recycle()
        }
    }

    @Test fun albumImportStillOpensTheSystemPicker() {
        ready()
        ui.onNodeWithText("相册导入").performTouchInput { click() }
        ui.waitUntil(15_000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root != null && root.packageName.toString() != instrumentation.targetContext.packageName
        }
        val pickerPackage = instrumentation.uiAutomation.rootInActiveWindow.packageName.toString()
        assertTrue("Expected Android media picker, got $pickerPackage", listOf("photo", "media", "documents").any { pickerPackage.contains(it, ignoreCase = true) })
        evidence("album-picker")
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        ui.onNodeWithText("相册导入").assertExists()
    }

    @Test fun scanToolsReturnToTheirOwnEntryPage() {
        ready()
        fun awaitCamera(label: String) {
            if (label == "证件扫描") {
                ui.waitUntil(15_000) { ui.onAllNodesWithText("立即扫描").fetchSemanticsNodes().isNotEmpty() }
            } else if (label == "文件扫描") enterCameraWhenReady(ui)
            else ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty() }
        }
        ui.onNodeWithText("更多工具").performTouchInput { click() }
        listOf("文件扫描", "文字识别", "证件扫描", "表格识别").forEach { label ->
            ui.onNodeWithText(label).performTouchInput { click() }
            awaitCamera(label)
            if (label == "证件扫描") back()
            else ui.onNodeWithContentDescription("关闭").performTouchInput { click() }
            ui.onNodeWithText("全部工具").assertExists()
            ui.onNodeWithText(label).performTouchInput { click() }
            awaitCamera(label)
            systemBack()
            ui.onNodeWithText("全部工具").assertExists()
        }
        evidence("tools-returned")
        back()
        listOf("文件扫描", "文字识别", "证件扫描").forEach { label ->
            ui.onNodeWithText(label).performTouchInput { click() }
            awaitCamera(label)
            if (label == "证件扫描") back()
            else ui.onNodeWithContentDescription("关闭").performTouchInput { click() }
            ui.onNodeWithText("相册导入").assertExists()
            ui.onNodeWithText(label).performTouchInput { click() }
            awaitCamera(label)
            systemBack()
            ui.onNodeWithText("相册导入").assertExists()
        }
        evidence("home-returned")
    }

    @Test fun flashlightControlsTheBoundCameraAndStopsOnExit() {
        ready()
        ui.onNodeWithText("文件扫描").performTouchInput { click() }
        enterCameraWhenReady(ui)
        val provider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(instrumentation.targetContext).get()
        val info = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()
        assertTrue("Device must provide rear flash for this hardware check", info.hasFlashUnit())
        val states = mutableListOf<String>()
        fun awaitTorch(enabled: Boolean, step: String) {
            val expected = if (enabled) androidx.camera.core.TorchState.ON else androidx.camera.core.TorchState.OFF
            ui.waitUntil(15_000) { info.torchState.value == expected }
            states += "$step: torchState=${info.torchState.value}"
            File(artifacts, "camera-flash-state.txt").writeText(states.joinToString("\n"))
        }
        ui.onNodeWithContentDescription("闪光灯：自动").performTouchInput { click() }
        awaitTorch(true, "manual-on")
        evidence("camera-flash-on")
        ui.onNodeWithContentDescription("闪光灯：开启").performTouchInput { click() }
        awaitTorch(false, "manual-off")
        ui.onNodeWithContentDescription("闪光灯：关闭").performTouchInput { click() }
        awaitTorch(false, "auto-flash")
        ui.onNodeWithContentDescription("闪光灯：自动").performTouchInput { click() }
        awaitTorch(true, "manual-on-again")
        repeat(3) {
            listOf("闪光灯：开启", "闪光灯：关闭", "闪光灯：自动").forEach { label ->
                ui.onNodeWithContentDescription(label).performTouchInput { click() }
            }
        }
        awaitTorch(true, "rapid-cycle-on")
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        awaitTorch(false, "background-off")
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        enterCameraWhenReady(ui)
        awaitTorch(true, "resume-on")
        ui.onNodeWithText("自动拍").performTouchInput { click() }
        awaitTorch(true, "auto-capture-on")
        ui.onNodeWithText("智能手拍").performTouchInput { click() }
        enterCameraWhenReady(ui)
        ui.onNodeWithContentDescription("关闭").performTouchInput { click() }
        awaitTorch(false, "exit-off")
        ui.onNodeWithText("相册导入").assertExists()
    }
}
