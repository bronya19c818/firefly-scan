package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android files, bitmap codecs, atomic index and PDF renderer; no mocked storage. */
@RunWith(AndroidJUnit4::class)
class HistoryDocumentPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun page(color: Int): ScannedPage {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ScannedPage(sourceBitmap = bitmap, processedBitmap = bitmap,
            previewBitmap = bitmap, editableQuad = listOf(PointF(0f, 0f), PointF(1f, 0f),
                PointF(1f, 1f), PointF(0f, 1f)))
    }

    private fun pdfCount(path: String): Int = ParcelFileDescriptor.open(
        File(path), ParcelFileDescriptor.MODE_READ_ONLY
    ).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    @Test fun createReopenAppendEditAndDeleteSameDocument() {
        var repository = HistoryRepository(context)
        val initial = listOf(page(Color.RED), page(Color.GREEN), page(Color.BLUE))
        val record = repository.saveDocumentRecord(initial)
        try {
            repository = HistoryRepository(context)
            val reopened = repository.loadDocumentPages(record.id)
            assertEquals(initial.map { it.id }, reopened.map { it.id })
            assertTrue(reopened.all { it.processedBitmap == null && it.sourceImagePath != null })
            val five = reopened + listOf(page(Color.YELLOW), page(Color.CYAN))
            val appended = repository.saveDocumentRecord(five, record.id)
            assertEquals(record.id, appended.id)
            assertEquals(record.createdAtMillis, appended.createdAtMillis)
            assertTrue(appended.updatedAtMillis > record.updatedAtMillis)
            assertEquals(5, pdfCount(appended.filePath))
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            val restored = HistoryRepository(context).loadDocumentPages(record.id)
            assertEquals(five.map { it.id }, restored.map { it.id })
            restored.take(3).forEachIndexed { index, page ->
                val source = requireNotNull(android.graphics.BitmapFactory.decodeFile(page.sourceImagePath))
                try { assertEquals(listOf(Color.RED, Color.GREEN, Color.BLUE)[index], source.getPixel(20, 20)) }
                finally { source.recycle() }
            }
            val edited = restored.reversed().dropLast(1).mapIndexed { i, p ->
                if (i == 0) p.copy(rotationDegrees = 90f) else p
            }.toMutableList()
            val replacement = page(Color.MAGENTA).copy(id = edited[1].id,
                editableQuad = defaultDocumentQuad(), contentRevision = java.util.UUID.randomUUID().toString())
            edited[1] = replacement
            val updated = repository.saveDocumentRecord(edited, record.id)
            val again = HistoryRepository(context).loadDocumentPages(record.id)
            assertEquals(edited.map { it.id }, again.map { it.id })
            assertEquals(90f, again[0].rotationDegrees)
            assertEquals(replacement.contentRevision, again[1].contentRevision)
            assertEquals(defaultDocumentQuad().map { it.x to it.y }, again[1].editableQuad.map { it.x to it.y })
            assertEquals(4, pdfCount(updated.filePath))
            val before = File(updated.filePath).readBytes()
            val indexFile = File(context.filesDir, "history/records.json")
            val unchangedFiles = File(requireNotNull(updated.documentManifestPath)).parentFile!!.walkTopDown()
                .filter { it.isFile }.toList() + indexFile
            val timestamps = unchangedFiles.associate { it.path to it.lastModified() }
            val noOp = repository.saveDocumentRecord(again, record.id)
            assertEquals(updated, noOp)
            assertArrayEquals(before, File(updated.filePath).readBytes())
            assertEquals(updated.updatedAtMillis, noOp.updatedAtMillis)
            assertEquals(timestamps, unchangedFiles.associate { it.path to it.lastModified() })
            // Export from restored file-backed pages must use full processed assets.
            val export = File(context.cacheDir, "history-export-test.pdf")
            try {
                export.outputStream().use { writePdfToStream(it, again, ExportFileSize.Actual) }
                assertEquals(4, pdfCount(export.path))
            } finally { export.delete() }
            File(context.getExternalFilesDir(null), "history-rescan-e2e").apply { mkdirs() }
                .resolve("persistence.txt").writeText("id=${record.id}\n3 -> 5 -> 4 pages; order, edits, rotation, replacement, no-op and export verified\n")
        } finally {
            repository.deleteRecord(record.id)
            assertFalse(File(context.filesDir, "history/documents/${record.id}").exists())
        }
    }

    @Test fun failedUpdateKeepsCommittedFilesAndIndex() {
        val repository = HistoryRepository(context)
        val record = repository.saveDocumentRecord(listOf(page(Color.RED)))
        try {
            val pdf = File(record.filePath).readBytes()
            val index = File(context.filesDir, "history/records.json").readBytes()
            val pages = repository.loadDocumentPages(record.id)
            // Force a real asset-copy failure after creating a new version, before index commit.
            val broken = pages[0].copy(processedImagePath = "/missing/history-test.png",
                contentRevision = java.util.UUID.randomUUID().toString())
            assertTrue(runCatching { repository.saveDocumentRecord(listOf(broken), record.id) }.isFailure)
            assertArrayEquals(pdf, File(record.filePath).readBytes())
            assertArrayEquals(index, File(context.filesDir, "history/records.json").readBytes())
            assertEquals(1, pdfCount(record.filePath))
            assertEquals(pages.map { it.id }, HistoryRepository(context).loadDocumentPages(record.id).map { it.id })
            // A blocked AtomicFile staging path fails after all document assets are written.
            val blockedIndex = File(context.filesDir, "history/records.json.new")
            check(blockedIndex.mkdir())
            File(blockedIndex, "blocked").writeText("simulate index write failure")
            try {
                assertTrue(runCatching {
                    repository.saveDocumentRecord(pages.map { it.copy(rotationDegrees = 90f) }, record.id)
                }.isFailure)
                assertArrayEquals(index, File(context.filesDir, "history/records.json").readBytes())
                assertArrayEquals(pdf, File(record.filePath).readBytes())
            } finally { blockedIndex.deleteRecursively() }
        } finally { repository.deleteRecord(record.id) }
    }
}
