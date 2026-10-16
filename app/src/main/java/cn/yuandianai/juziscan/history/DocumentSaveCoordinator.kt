package cn.yuandianai.juziscan.history

import android.content.Context
import android.util.Log
import cn.yuandianai.juziscan.scan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

data class SavedDocumentState(val updatedAtMillis: Long, val pages: List<ScannedPage>)

/** Application-owned persistence and existing page queues. Every closure owns a fixed document ID. */
class DocumentSaveCoordinator private constructor(context: Context) {
    private val context = context.applicationContext
    private val repository = HistoryRepository(this.context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val indexWake = Channel<Unit>(Channel.CONFLATED)
    private val pendingIndex = linkedSetOf<String>()
    private val retrySnapshots = ConcurrentHashMap<String, List<ScannedPage>>()
    private val commitLocks = ConcurrentHashMap<String, Any>()
    private val processingSlots = Semaphore(2)
    private val sessions = mutableMapOf<String, Session>()
    private val _documents = MutableStateFlow<Map<String, SavedDocumentState>>(emptyMap())
    val documents = _documents.asStateFlow()
    private val _phases = MutableStateFlow<Map<String, Map<String, PageProcessingPhase>>>(emptyMap())
    val phases = _phases.asStateFlow()
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures = _failures.asStateFlow()
    private val _changes = MutableStateFlow(0L)
    val changes = _changes.asStateFlow()

    private inner class Session(val id: String, var state: SavedDocumentState, var attached: Boolean) {
        val queue = PageProcessingQueue(scope, pageProvider = { pageId -> state.pages.firstOrNull { it.id == pageId } },
            processPage = { page ->
                processingSlots.acquire()
                try {
                    repository.processCachedPage(id, page) { intermediate ->
                        scope.launch { applyCache(intermediate, final = false) }
                    }
                } finally { processingSlots.release() }
            },
            onPageProcessed = { applyCache(it, final = true) },
            onPhaseChanged = { pageId, phase ->
                _phases.update { all -> all + (id to all[id].orEmpty().toMutableMap().apply {
                    if (phase == null) remove(pageId) else put(pageId, phase)
                }) }
                if (phase == PageProcessingPhase.FAILED && !repository.isDocumentDeleted(id)) {
                    reportFailure(id, IllegalStateException("页面处理失败，可稍后重试"))
                }
                scope.launch { yield(); releaseIfIdle() }
            })

        fun applyCache(result: ScannedPage, final: Boolean) {
            if (repository.isDocumentDeleted(id)) { releaseIfIdle(); return }
            state = state.copy(pages = state.pages.map { current ->
                if (current.id == result.id && current.contentRevision == result.contentRevision &&
                    current.editableQuad.map { it.x to it.y } == result.editableQuad.map { it.x to it.y } &&
                    current.correctionMode == result.correctionMode && current.imageStyle == result.imageStyle) {
                    current.copy(previewBitmap = result.previewBitmap,
                        processedImagePath = if (final) result.processedImagePath else current.processedImagePath,
                        detectedQuad = if (final) result.detectedQuad else current.detectedQuad)
                } else current
            })
            publish()
            if (final) scheduleIndex(id)
        }

        fun publish() { _documents.update { it + (id to state) } }
        fun releaseIfIdle() {
            if (!attached && queue.isIdle && sessions[id] === this) {
                sessions.remove(id)
                _documents.update { it - id }
                _phases.update { it - id }
            }
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                java.io.File(this@DocumentSaveCoordinator.context.cacheDir, "document-page-processing")
                    .listFiles()?.filter { it.name.startsWith("page-") &&
                        it.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
                    ?.forEach { it.delete() }
                repository.loadRecords().filter { it.kind == HistoryRecordKind.Document }.forEach { record ->
                    val pages = repository.loadDocumentPages(record.id)
                    if (pages.any { !it.hasProcessedOutput }) withContext(Dispatchers.Main) {
                        install(record.id, SavedDocumentState(record.updatedAtMillis, pages), attached = false)
                    }
                    scheduleIndex(record.id)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportFailure("recovery", error)
            }
        }
        scope.launch(Dispatchers.IO) {
            for (signal in indexWake) while (true) {
                val id = synchronized(pendingIndex) { pendingIndex.firstOrNull()?.also { pendingIndex.remove(it) } } ?: break
                try {
                    val retried = synchronized(commitLocks.getOrPut(id) { Any() }) {
                        retrySnapshots[id]?.let { snapshot ->
                            repository.commitDocument(snapshot, id)
                        }
                    }
                    if (retried != null) {
                        val pages = repository.loadDocumentPages(id)
                        withContext(Dispatchers.Main) { install(id, SavedDocumentState(retried.updatedAtMillis, pages)) }
                        retrySnapshots.remove(id)
                    }
                    repository.mergeDocumentIndex(id)
                    // Only disposable data is maintained here; reliable commits never decode thumbnails.
                    synchronized(commitLocks.getOrPut(id) { Any() }) {
                        // A failed reliable commit may already own new immutable assets needed by retry.
                        if (!retrySnapshots.containsKey(id)) repository.maintainDocumentCaches(id)
                    }
                    if (_phases.value[id].orEmpty().values.none { it == PageProcessingPhase.FAILED }) {
                        _failures.update { it - id }
                    }
                    _changes.update { it + 1 }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (repository.isDocumentDeleted(id)) {
                        retrySnapshots.remove(id)
                        _failures.update { it - id }
                    } else reportFailure(id, error)
                }
            }
        }
    }

    fun commit(id: String, pages: List<ScannedPage>, expectedVersion: Long? = null): HistoryRecord = synchronized(commitLocks.getOrPut(id) { Any() }) {
        check(!retrySnapshots.containsKey(id)) { "仍有未保存的修改，请先重试" }
        val snapshot = pages.map { it.copy(editableQuad = it.editableQuad.map { p -> android.graphics.PointF(p.x, p.y) }) }
        try {
            val record = repository.commitDocument(snapshot, id, expectedVersion = expectedVersion)
            retrySnapshots.remove(id)
            val rebound = snapshot.map { page ->
                val (source, processed) = repository.pageAssetPaths(id, page)
                page.copy(sourceImagePath = source, processedImagePath = processed,
                    sourceBitmap = page.previewBitmap ?: page.sourceBitmap, reviewBitmap = null, processedBitmap = null)
            }
            scope.launch { install(id, SavedDocumentState(record.updatedAtMillis, rebound)) }
            scheduleIndex(id)
            _changes.update { it + 1 }
            record
        } catch (error: Exception) {
            if (!repository.isDocumentDeleted(id) && error !is java.util.ConcurrentModificationException) retrySnapshots[id] = snapshot
            reportFailure(id, error)
            throw error
        }
    }

    /** Merge camera/import additions with the latest document, rather than a suspended UI snapshot. */
    fun addPages(id: String, incoming: List<ScannedPage>, replacingPageId: String? = null): SavedDocumentState =
        synchronized(commitLocks.getOrPut(id) { Any() }) {
            check(!retrySnapshots.containsKey(id)) { "仍有未保存的修改，请先重试" }
            val record = repository.documentRecord(id)
            val current = _documents.value[id]?.takeIf { it.updatedAtMillis == record?.updatedAtMillis }?.pages
                ?: if (record == null) emptyList() else repository.loadDocumentPages(id)
            require(replacingPageId == null || current.any { it.id == replacingPageId }) { "要替换的页面已删除，请重新选择" }
            val merged = if (replacingPageId == null) current + incoming else
                current.map { if (it.id == replacingPageId) incoming.first() else it } + incoming.drop(1)
            val saved = commit(id, merged, record?.updatedAtMillis ?: 0L)
            SavedDocumentState(saved.updatedAtMillis, merged.map { page ->
                val (source, processed) = repository.pageAssetPaths(id, page)
                page.copy(sourceImagePath = source, processedImagePath = processed,
                    sourceBitmap = page.previewBitmap ?: page.sourceBitmap, reviewBitmap = null, processedBitmap = null)
            })
        }

    /** Main-thread UI attachment; detaching retains work until the queue settles. */
    fun attach(id: String, state: SavedDocumentState) { install(id, state, attached = true) }
    fun detach(id: String?) { sessions[id]?.let { it.attached = false; it.releaseIfIdle() } }
    fun promote(id: String, pageId: String) { sessions[id]?.queue?.promote(pageId) }
    fun retry(id: String) {
        scheduleIndex(id)
        sessions[id]?.let { session -> session.state.pages.filter { !it.hasProcessedOutput }.forEach { session.queue.retry(it.id) } }
    }

    private fun install(id: String, state: SavedDocumentState, attached: Boolean? = null) {
        if (repository.isDocumentDeleted(id)) return
        val session = sessions[id]
        if (session != null && session.state.updatedAtMillis > state.updatedAtMillis) return
        val current = session ?: Session(id, state, attached ?: false).also { sessions[id] = it }
        current.state = if (current.state.updatedAtMillis == state.updatedAtMillis) {
            val cached = current.state.pages.associateBy { it.id }
            state.copy(pages = state.pages.map { page ->
                val ready = cached[page.id]
                if (!page.hasProcessedOutput && ready?.hasProcessedOutput == true &&
                    ready.contentRevision == page.contentRevision) {
                    page.copy(processedImagePath = ready.processedImagePath, previewBitmap = ready.previewBitmap,
                        detectedQuad = ready.detectedQuad)
                } else page
            })
        } else state
        if (attached != null) current.attached = attached
        current.publish()
        state.pages.filter { !it.hasProcessedOutput }.forEach { current.queue.submit(it.id) }
    }

    private fun scheduleIndex(id: String) {
        synchronized(pendingIndex) { pendingIndex.add(id) }
        indexWake.trySend(Unit)
    }

    fun reportFailure(id: String, error: Throwable) {
        if (repository.isDocumentDeleted(id)) { _failures.update { it - id }; return }
        Log.e("JuZiScan", "Document $id save/processing failed", error)
        _failures.update { it + (id to (error.message ?: "保存失败，请检查存储空间")) }
    }
    fun dismissFailure(id: String) { _failures.update { it - id } }
    fun hasPendingChanges(id: String?): Boolean = id != null && retrySnapshots.containsKey(id)

    companion object {
        @Volatile private var instance: DocumentSaveCoordinator? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: DocumentSaveCoordinator(context).also { instance = it }
        }
    }
}
