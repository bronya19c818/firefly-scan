package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.history.HistoryRecord
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

    private fun pdfCount(file: File): Int = ParcelFileDescriptor.open(
        file, ParcelFileDescriptor.MODE_READ_ONLY
    ).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    private fun documentDir(record: HistoryRecord): File =
        File(requireNotNull(record.documentManifestPath)).parentFile!!

    private fun manifestBytes(record: HistoryRecord): ByteArray =
        File(requireNotNull(record.documentManifestPath)).readBytes()

    @Test fun createReopenAppendEditAndDeleteSameDocument() {
        var repository = HistoryRepository(context)
        val initial = listOf(page(Color.RED), page(Color.GREEN), page(Color.BLUE))
        val record = repository.saveDocumentRecord(initial)
        try {
            // PDF 按需生成：普通保存只持久化页面数据与清单。
            assertFalse("保存不应生成 PDF", File(record.filePath).exists())

            repository = HistoryRepository(context)
            val reopened = repository.loadDocumentPages(record.id)
            assertEquals(initial.map { it.id }, reopened.map { it.id })
            assertTrue(reopened.all { it.processedBitmap == null && it.sourceImagePath != null })

            // 增量保存：追加页面不重写未变化页面的原图文件。
            val untouched = reopened.map { File(requireNotNull(it.sourceImagePath)) }
            val untouchedStamps = untouched.map { it.lastModified() to it.length() }

            val five = reopened + listOf(page(Color.YELLOW), page(Color.CYAN))
            val appended = repository.saveDocumentRecord(five, record.id)
            assertEquals(record.id, appended.id)
            assertEquals(record.createdAtMillis, appended.createdAtMillis)
            assertTrue(appended.updatedAtMillis > record.updatedAtMillis)
            assertEquals("未变化页面文件不得重写", untouchedStamps,
                untouched.map { it.lastModified() to it.length() })
            assertEquals(1, repository.loadRecords().count { it.id == record.id })
            assertFalse("追加保存同样不生成 PDF", File(appended.filePath).exists())

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
            // 替换页面：保留页面 ID，写入新的内容版本。
            val replacement = page(Color.MAGENTA).copy(id = edited[1].id,
                editableQuad = defaultDocumentQuad(), contentRevision = java.util.UUID.randomUUID().toString())
            edited[1] = replacement
            // 只考察本次提交保留下来的页面：被移除页面的目录会在提交后清理。
            val keptSources = restored.filter { it.id in edited.map { p -> p.id }.toSet() - replacement.id }
                .map { File(requireNotNull(it.sourceImagePath)) }
            val keptStamps = keptSources.map { it.lastModified() to it.length() }

            val updated = repository.saveDocumentRecord(edited, record.id)
            assertEquals("旋转与排序不得重建图片", keptStamps, keptSources.map { it.lastModified() to it.length() })

            val again = HistoryRepository(context).loadDocumentPages(record.id)
            assertEquals(edited.map { it.id }, again.map { it.id })
            assertEquals(90f, again[0].rotationDegrees)
            assertEquals(replacement.contentRevision, again[1].contentRevision)
            assertEquals(defaultDocumentQuad().map { it.x to it.y }, again[1].editableQuad.map { it.x to it.y })

            // PDF 按需生成：需要时生成完整文档，未修改时复用缓存。
            val pdf = repository.ensureDocumentPdf(record.id)
            assertEquals(4, pdfCount(pdf))
            assertEquals("PDF 必须落在文档缓存目录", pdf.path, File(documentDir(updated), "cache/document.pdf").path)
            val pdfStamp = pdf.lastModified()
            val pdfBytes = pdf.readBytes()

            val indexFile = File(context.filesDir, "history/records.json")
            val noOpFiles = documentDir(updated).walkTopDown().filter { it.isFile }.toList() + indexFile
            val noOpStamps = noOpFiles.associate { it.path to it.lastModified() }
            val noOp = repository.saveDocumentRecord(again, record.id)
            assertEquals(updated, noOp)
            assertEquals(updated.updatedAtMillis, noOp.updatedAtMillis)
            assertEquals("无修改退出不得写盘", noOpStamps, noOpFiles.associate { it.path to it.lastModified() })
            assertEquals("无修改不得重建 PDF 缓存", pdfStamp, pdf.lastModified())
            assertArrayEquals(pdfBytes, pdf.readBytes())
            assertEquals(4, pdfCount(pdf))

            // Export from restored file-backed pages must use full processed assets.
            val export = File(context.cacheDir, "history-export-test.pdf")
            try {
                export.outputStream().use { writePdfToStream(it, again, ExportFileSize.Actual) }
                assertEquals(4, pdfCount(export))
            } finally { export.delete() }
            File(context.getExternalFilesDir(null), "history-rescan-e2e").apply { mkdirs() }
                .resolve("persistence.txt").writeText(
                    "id=${record.id}\n3 -> 5 -> 4 pages; order, edits, rotation, replacement, no-op, " +
                        "incremental files and on-demand PDF verified\n"
                )
        } finally {
            repository.deleteRecord(record.id)
            assertFalse(File(context.filesDir, "history/documents/${record.id}").exists())
        }
    }

    @Test fun failedUpdateKeepsCommittedFilesAndIndex() {
        val repository = HistoryRepository(context)
        val record = repository.saveDocumentRecord(listOf(page(Color.RED)))
        try {
            val manifest = manifestBytes(record)
            val index = File(context.filesDir, "history/records.json").readBytes()
            val pages = repository.loadDocumentPages(record.id)
            // Force a real asset-write failure: the new source path is occupied by a non-empty
            // directory, so the atomic replace of the page asset cannot succeed.
            val newRevision = java.util.UUID.randomUUID().toString()
            val blockedAsset = File(documentDir(record), "pages/${pages[0].id}/$newRevision.png")
            check(blockedAsset.mkdirs()) { "无法创建阻塞目录" }
            File(blockedAsset, "blocked").writeText("simulate asset write failure")
            try {
                assertTrue(runCatching {
                    repository.saveDocumentRecord(listOf(pages[0].copy(contentRevision = newRevision)), record.id)
                }.isFailure)
            } finally { blockedAsset.deleteRecursively() }
            assertArrayEquals("失败保存不得破坏已提交清单", manifest, manifestBytes(record))
            assertArrayEquals(index, File(context.filesDir, "history/records.json").readBytes())
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
                assertEquals("索引失败后可靠清单仍保留已提交修改", 90f,
                    HistoryRepository(context).loadDocumentPages(record.id).first().rotationDegrees)
            } finally { blockedIndex.deleteRecursively() }
            assertEquals("索引失败后文档仍可读", pages.map { it.id },
                HistoryRepository(context).loadDocumentPages(record.id).map { it.id })
        } finally { repository.deleteRecord(record.id) }
    }

    @Test fun unprocessedPagePersistsSourceAndResumesProcessing() {
        val repository = HistoryRepository(context)
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        // 尚未完成高清处理的页面：原图照常落盘，处理图留空。
        val pending = ScannedPage(sourceBitmap = bitmap,
            editableQuad = listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)))
        val record = repository.saveDocumentRecord(listOf(pending))
        try {
            val reopened = HistoryRepository(context).loadDocumentPages(record.id)
            assertEquals(1, reopened.size)
            assertNotNull("原图必须已保存", reopened[0].sourceImagePath)
            assertNull("未处理页面不得持有处理图", reopened[0].processedImagePath)
            assertFalse("未处理页面不得产生 PDF", File(record.filePath).exists())
            // 需要 PDF 时再补处理该页，并生成完整文档。
            assertEquals(1, pdfCount(repository.ensureDocumentPdf(record.id)))
        } finally { repository.deleteRecord(record.id) }
    }
}
