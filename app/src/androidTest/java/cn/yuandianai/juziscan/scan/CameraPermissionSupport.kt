package cn.yuandianai.juziscan.scan

import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.MainActivity

/** Use the real permission flow: some OEMs prohibit ADB/UiAutomation grants. */
internal fun enterCameraWhenReady(ui: AndroidComposeTestRule<*, MainActivity>) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    ui.waitUntil(15_000) {
        ui.onAllNodesWithText("智能手拍").fetchSemanticsNodes().isNotEmpty() ||
            ui.onAllNodesWithText("开启相机权限").fetchSemanticsNodes().isNotEmpty()
    }
    val permissionButtons = ui.onAllNodesWithText("开启相机权限")
    val count = permissionButtons.fetchSemanticsNodes().size
    if (count > 0) permissionButtons[count - 1].performTouchInput { click() }
    ui.waitUntil(15_000) {
        if (instrumentation.targetContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val labels = listOf("使用应用时", "使用时允许", "仅在使用中", "While using the app")
            labels.asSequence().flatMap { root?.findAccessibilityNodeInfosByText(it).orEmpty().asSequence() }
                .firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            false
        }
    }
    ui.waitUntil(30_000) { ui.onAllNodesWithText("智能手拍").fetchSemanticsNodes().isNotEmpty() }
    ui.waitUntil(30_000) {
        !ui.onNodeWithContentDescription("拍摄").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
    }
}
