package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.history.HistoryRepository
import cn.yuandianai.juziscan.history.DocumentSaveCoordinator
import cn.yuandianai.juziscan.history.SavedDocumentState
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Failure modes: raw-only restart, stale cache, index failure, manifest failure, deleted task revival. */
@RunWith(AndroidJUnit4::class)
class ImmediateDurabilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun page() = Bitmap.createBitmap(100, 180, Bitmap.Config.ARGB_8888).let {
        it.eraseColor(Color.GREEN)
        ScannedPage(sourceBitmap = it, previewBitmap = it)
    }

    @Test fun rawOnlyDocumentAndEditsRecoverWithoutProcessingOrPdf() {
        val repo = HistoryRepository(context)
        val id = UUID.randomUUID().toString()
        try {
            repo.saveDocumentRecord(listOf(page(), page()), id)
            val raw = HistoryRepository(context).loadDocumentPages(id)
            assertFalse(raw.first().isProcessed)
            val source = File(requireNotNull(raw[0].sourceImagePath))
            val bytes = source.readBytes()
            val modified = source.lastModified()
            val edited = raw.reversed().mapIndexed { index, p -> if (index == 0) p.copy(rotationDegrees = 90f) else p }
            repo.saveDocumentRecord(edited, id)
            val restored = HistoryRepository(context).loadDocumentPages(id)
            assertEquals(edited.map { it.id }, restored.map { it.id })
            assertEquals(90f, restored.first().rotationDegrees)
            assertArrayEquals(bytes, source.readBytes())
            assertEquals(modified, source.lastModified())
            assertFalse(File(repo.loadRecords().first { it.id == id }.filePath).exists())
        } finally { repo.deleteRecord(id) }
    }

    @Test fun indexFailureRetainsDurableManifestAndManifestFailureRetainsLastGoodState() {
        val repo = HistoryRepository(context)
        val record = repo.saveDocumentRecord(listOf(page()))
        val indexBlock = File(context.filesDir, "history/records.json.new")
        val manifestBlock = File(requireNotNull(record.documentManifestPath) + ".new")
        try {
            check(indexBlock.mkdir())
            File(indexBlock, "owned-by-test").writeText("index failure")
            val rotated = repo.loadDocumentPages(record.id).map { it.copy(rotationDegrees = 90f) }
            assertTrue(runCatching { repo.saveDocumentRecord(rotated, record.id) }.isFailure)
            assertEquals(90f, HistoryRepository(context).loadDocumentPages(record.id).first().rotationDegrees)
            check(indexBlock.deleteRecursively())
            check(manifestBlock.mkdir())
            File(manifestBlock, "owned-by-test").writeText("manifest failure")
            assertTrue(runCatching { repo.saveDocumentRecord(rotated.map { it.copy(rotationDegrees = 180f) }, record.id) }.isFailure)
            assertEquals(90f, HistoryRepository(context).loadDocumentPages(record.id).first().rotationDegrees)
        } finally {
            if (File(indexBlock, "owned-by-test").isFile) indexBlock.deleteRecursively()
            if (File(manifestBlock, "owned-by-test").isFile) manifestBlock.deleteRecursively()
            repo.deleteRecord(record.id)
        }
    }

    @Test fun deletedDocumentCannotBeRecreatedByLateSave() {
        val repo = HistoryRepository(context)
        val record = repo.saveDocumentRecord(listOf(page()))
        val snapshot = repo.loadDocumentPages(record.id)
        repo.deleteRecord(record.id)
        assertTrue(runCatching { repo.saveDocumentRecord(snapshot, record.id) }.isFailure)
        assertFalse(HistoryRepository(context).loadRecords().any { it.id == record.id })
        assertFalse(File(context.filesDir, "history/documents/${record.id}").exists())
    }

    @Test fun deletingDuringPdfEncodingCannotRecreateDocumentFiles() {
        val repo = HistoryRepository(context)
        val bitmap = Bitmap.createBitmap(2400, 3200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val record = repo.saveDocumentRecord((1..3).map {
            ScannedPage(sourceBitmap = bitmap, processedBitmap = bitmap, previewBitmap = preparePreviewBitmap(bitmap, 640))
        })
        val executor = Executors.newSingleThreadExecutor()
        val existing = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        try {
            val future = executor.submit<Boolean> { runCatching { repo.ensureDocumentPdf(record.id) }.isSuccess }
            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
            var encoding = false
            while (android.os.SystemClock.elapsedRealtime() < deadline && !future.isDone) {
                encoding = context.cacheDir.listFiles().orEmpty().any {
                    it.name.startsWith("document-export-") && it.name !in existing
                }
                if (encoding) break
                android.os.SystemClock.sleep(1)
            }
            assertTrue("Must observe real encoding before concurrent deletion", encoding)
            repo.deleteRecord(record.id)
            assertFalse("Deleted export must not publish", future.get(30, TimeUnit.SECONDS))
            assertFalse(File(context.filesDir, "history/documents/${record.id}").exists())
            assertFalse(repo.loadRecords().any { it.id == record.id })
            File(context.getExternalFilesDir(null), "immediate-save-e2e").apply { mkdirs() }
                .resolve("delete-during-pdf.txt").writeText("Observed PDF encoding, deleted document, export rejected, no files or record recreated.")
        } finally {
            executor.shutdownNow()
            repo.deleteRecord(record.id)
            bitmap.recycle()
        }
    }

    @Test fun lateImportPreservesLatestOrderAndRotationAndRejectsOldSnapshots() {
        val repo = HistoryRepository(context)
        val record = repo.saveDocumentRecord(listOf(page(), page()))
        val saver = DocumentSaveCoordinator.get(context)
        try {
            val old = repo.loadDocumentPages(record.id)
            val latest = old.reversed().mapIndexed { index, p -> if (index == 0) p.copy(rotationDegrees = 90f) else p }
            repo.saveDocumentRecord(latest, record.id)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                saver.attach(record.id, SavedDocumentState(record.updatedAtMillis, old))
            }
            val stale = runCatching { saver.commit(record.id, old, record.updatedAtMillis) }.exceptionOrNull()
            assertTrue(stale is java.util.ConcurrentModificationException)
            assertFalse("A version conflict must not queue an obsolete retry", saver.hasPendingChanges(record.id))
            val imported = page()
            val saved = saver.addPages(record.id, listOf(imported))
            assertEquals(latest.map { it.id } + imported.id, saved.pages.map { it.id })
            val restored = repo.loadDocumentPages(record.id)
            assertEquals(90f, restored.first().rotationDegrees)
            assertEquals(saved.pages.map { it.id }, restored.map { it.id })
            File(context.getExternalFilesDir(null), "immediate-save-e2e").apply { mkdirs() }
                .resolve("late-import.txt").writeText("Rejected old snapshot; appended to latest reordered/rotated document; 3 pages restored.")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { saver.detach(record.id) }
            repo.deleteRecord(record.id)
        }
    }
}
