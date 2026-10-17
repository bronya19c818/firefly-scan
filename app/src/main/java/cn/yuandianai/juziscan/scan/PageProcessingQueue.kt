package cn.yuandianai.juziscan.scan

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面后台处理状态（与文档的永久编辑参数无关）。 */
enum class PageProcessingPhase {
    /** 等待处理。 */
    PENDING,

    /** 正在处理。 */
    PROCESSING,

    /** 处理完成。 */
    READY,

    /** 处理失败，允许重试。 */
    FAILED
}

class ObsoletePageResultException : IllegalStateException("页面已更新或文档已删除，旧处理结果已忽略")

/**
 * 单页后台处理队列：
 * - 有限并发（默认 2），避免全速处理大量高清图像拖慢相机与界面；
 * - 优先级：当前查看页面（promote 提升）＞ 最近提交的页面 ＞ 其余；
 * - 任务绑定页面 ID，执行时重新读取最新页面快照，结果由回调按内容版本校验，
 *   用户替换 / 删除 / 重新编辑页面后旧结果不会被采用。
 *
 * 所有公开方法必须在主线程调用；重活内部切换到 Dispatchers.Default。
 */
class PageProcessingQueue(
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2,
    private val pageProvider: (String) -> ScannedPage?,
    private val processPage: (ScannedPage) -> ScannedPage,
    private val onPageProcessed: (ScannedPage) -> Unit,
    private val onPhaseChanged: (String, PageProcessingPhase?) -> Unit
) {
    private val scheduler = PriorityTaskScheduler()
    private var active = 0
    val isIdle: Boolean get() = active == 0 && scheduler.isEmpty

    /** 提交一个待处理页面；已处理完成的直接标记 READY。 */
    fun submit(pageId: String) {
        val page = pageProvider(pageId) ?: return
        if (page.hasProcessedOutput) {
            onPhaseChanged(pageId, PageProcessingPhase.READY)
            return
        }
        if (scheduler.contains(pageId)) return
        scheduler.add(pageId)
        onPhaseChanged(pageId, PageProcessingPhase.PENDING)
        pump()
    }

    /** 用户切换页面时提升等待任务的优先级。 */
    fun promote(pageId: String) {
        if (scheduler.promote(pageId)) pump()
    }

    /** 失败页面重试。 */
    fun retry(pageId: String) {
        onPhaseChanged(pageId, null)
        submit(pageId)
    }

    /** 文档关闭 / 切换任务时丢弃等待任务；执行中的任务自行失效。 */
    fun reset() {
        scheduler.clear()
    }

    private fun pump() {
        while (active < maxConcurrent) {
            val pageId = scheduler.take() ?: return
            active++
            onPhaseChanged(pageId, PageProcessingPhase.PROCESSING)
            scope.launch {
                val outcome = runCatching {
                    withContext(Dispatchers.Default) {
                        // 执行时读取最新页面：期间被替换 / 删除的任务直接跳过。
                        val page = pageProvider(pageId) ?: return@withContext null
                        if (page.hasProcessedOutput) page else processPage(page)
                    }
                }
                when {
                    outcome.isSuccess -> {
                        val processed = outcome.getOrThrow()
                        if (processed != null && pageProvider(pageId)?.hasSameProcessingParameters(processed) == true) {
                            onPhaseChanged(pageId, PageProcessingPhase.READY)
                            onPageProcessed(processed)
                        } else {
                            onPhaseChanged(pageId, null)
                        }
                    }
                    outcome.exceptionOrNull() is CancellationException ||
                        outcome.exceptionOrNull() is ObsoletePageResultException -> {
                        onPhaseChanged(pageId, null)
                    }
                    else -> {
                        Log.e("JuZiScan", "Background page processing failed", outcome.exceptionOrNull())
                        val stillPresent = pageProvider(pageId) != null
                        onPhaseChanged(pageId, if (stillPresent) PageProcessingPhase.FAILED else null)
                    }
                }
                active--
                pump()
            }
        }
    }
}

/**
 * 是否采用后台处理结果：任务绑定页面 ID 与内容版本，
 * 页面已被替换 / 删除或已经就绪时，旧结果不得覆盖新页面。
 */
fun shouldApplyProcessedPage(
    currentRevision: String?,
    currentProcessed: Boolean,
    resultRevision: String
): Boolean = currentRevision != null && currentRevision == resultRevision && !currentProcessed

/**
 * 优先级任务调度：boostedAt 大者先执行（最近被查看的页面），其次按提交先后
 * 新者先执行（最近拍摄 / 修改的页面）。纯逻辑，便于单元测试。
 */
internal class PriorityTaskScheduler {
    val isEmpty: Boolean get() = tasks.isEmpty()
    private class Task(val pageId: String, val seq: Long) {
        var boostedAt: Long = 0L
    }

    private val tasks = mutableListOf<Task>()
    private var seq = 0L

    fun add(pageId: String): Boolean {
        if (tasks.any { it.pageId == pageId }) return false
        tasks.add(Task(pageId, seq++))
        return true
    }

    fun promote(pageId: String): Boolean {
        val task = tasks.firstOrNull { it.pageId == pageId } ?: return false
        task.boostedAt = ++seq
        return true
    }

    fun take(): String? {
        val task = tasks.maxWithOrNull(compareBy({ it.boostedAt }, { it.seq })) ?: return null
        tasks.remove(task)
        return task.pageId
    }

    fun contains(pageId: String): Boolean = tasks.any { it.pageId == pageId }

    fun clear() = tasks.clear()
}
