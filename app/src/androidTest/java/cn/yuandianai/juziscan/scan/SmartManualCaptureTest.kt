package cn.yuandianai.juziscan.scan

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side checks for the shutter gate; physical camera acceptance is documented separately. */
@RunWith(AndroidJUnit4::class)
class SmartManualCaptureTest {
    private val quad = listOf(PointF(.1f, .1f), PointF(.9f, .1f), PointF(.9f, .9f), PointF(.1f, .9f))

    @Test
    fun stableManualDocumentNeverRequestsCapture() {
        val tracker = AutoCaptureStabilityTracker()
        for (now in 10_000L..16_000L step 320L) {
            val result = tracker.update(now, quad, autoCaptureEnabled = false)
            assertFalse(result.shouldCapture)
            assertTrue(result.uiState.documentDetected)
            assertEquals(4, result.uiState.liveQuad.size)
            assertEquals(0f, result.uiState.stableProgress, 0f)
        }
    }

    @Test
    fun autoRetainsStabilityThresholdAndCooldown() {
        val tracker = AutoCaptureStabilityTracker()
        assertFalse(tracker.update(10_000L, quad).shouldCapture)
        assertFalse(tracker.update(10_899L, quad).shouldCapture)
        assertTrue(tracker.update(10_900L, quad).shouldCapture)
        assertFalse(tracker.update(11_800L, quad).shouldCapture)
        assertTrue(tracker.update(12_700L, quad).shouldCapture)
    }

    @Test
    fun manualCaptureDoesNotDependOnDetectionAndRejectsDoubleTap() {
        val vm = ScanViewModel()
        vm.startEntry(ScanEntry.Document)
        assertFalse(vm.autoCaptureUiState.documentDetected)
        assertTrue(vm.tryBeginCapture())
        assertFalse(vm.tryBeginCapture())
        vm.isCapturing = false // Camera error/success releases the gate.
        assertTrue(vm.tryBeginCapture())
        vm.isCapturing = false
        vm.stage = ScanStage.Preview
        assertFalse(vm.tryBeginCapture())
    }

    @Test
    fun lostDocumentClearsFeedbackAndNeverRequestsCapture() {
        val tracker = AutoCaptureStabilityTracker()
        tracker.update(10_000L, quad, autoCaptureEnabled = false)
        val result = tracker.update(16_000L, emptyList(), autoCaptureEnabled = false)
        assertFalse(result.shouldCapture)
        assertFalse(result.uiState.documentDetected)
        assertTrue(result.uiState.liveQuad.isEmpty())
    }
}
