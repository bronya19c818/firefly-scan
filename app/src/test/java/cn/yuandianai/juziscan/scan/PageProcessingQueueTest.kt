package cn.yuandianai.juziscan.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageProcessingQueueTest {

    @Test
    fun addDeduplicatesPageTasks() {
        val scheduler = PriorityTaskScheduler()
        assertTrue(scheduler.add("p1"))
        assertFalse(scheduler.add("p1"))
        assertTrue(scheduler.contains("p1"))
    }

    @Test
    fun promotedPageRunsFirst() {
        val scheduler = PriorityTaskScheduler()
        scheduler.add("p1")
        scheduler.add("p2")
        scheduler.add("p3")
        scheduler.promote("p2")
        // 当前查看页面优先于其余页面。
        assertEquals("p2", scheduler.take())
    }

    @Test
    fun latestBoostWinsAmongPromotedPages() {
        val scheduler = PriorityTaskScheduler()
        scheduler.add("p1")
        scheduler.add("p2")
        scheduler.add("p3")
        scheduler.promote("p1")
        scheduler.promote("p3")
        assertEquals("p3", scheduler.take())
        assertEquals("p1", scheduler.take())
    }

    @Test
    fun unpromotedPagesRunNewestFirst() {
        val scheduler = PriorityTaskScheduler()
        scheduler.add("page-a")
        scheduler.add("page-b")
        scheduler.add("page-c")
        // 未提升的页面按提交先后，最近拍摄 / 修改的先处理。
        assertEquals("page-c", scheduler.take())
        assertEquals("page-b", scheduler.take())
        assertEquals("page-a", scheduler.take())
        assertNull(scheduler.take())
    }

    @Test
    fun promotingCompletedOrUnknownPageIsIgnored() {
        val scheduler = PriorityTaskScheduler()
        scheduler.add("p1")
        assertFalse(scheduler.promote("missing"))
        scheduler.take()
        assertFalse(scheduler.promote("p1"))
    }

    @Test
    fun clearDropsAwaitingTasks() {
        val scheduler = PriorityTaskScheduler()
        scheduler.add("p1")
        scheduler.add("p2")
        scheduler.clear()
        assertFalse(scheduler.contains("p1"))
        assertNull(scheduler.take())
    }

    @Test
    fun pendingPageAcceptsMatchingResult() {
        assertTrue(shouldApplyProcessedPage(currentRevision = "rev-1", currentProcessed = false, resultRevision = "rev-1"))
    }

    @Test
    fun replacedPageDiscardsStaleResult() {
        // 用户在处理过程中替换页面：旧任务结果不得覆盖新页面。
        assertFalse(shouldApplyProcessedPage(currentRevision = "rev-2", currentProcessed = false, resultRevision = "rev-1"))
    }

    @Test
    fun deletedPageDiscardsResult() {
        assertFalse(shouldApplyProcessedPage(currentRevision = null, currentProcessed = false, resultRevision = "rev-1"))
    }

    @Test
    fun readyPageDoesNotRollBackToStaleOutput() {
        assertFalse(shouldApplyProcessedPage(currentRevision = "rev-1", currentProcessed = true, resultRevision = "rev-1"))
    }
}
