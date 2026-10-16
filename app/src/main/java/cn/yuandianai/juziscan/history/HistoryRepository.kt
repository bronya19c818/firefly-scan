package cn.yuandianai.juziscan.history

import android.content.Context
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.util.AtomicFile
import androidx.core.content.FileProvider
import cn.yuandianai.juziscan.scan.ExportFileSize
import cn.yuandianai.juziscan.scan.ExportFileType
import cn.yuandianai.juziscan.scan.IdCardDocumentType
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.scan.documentFingerprint
import cn.yuandianai.juziscan.scan.processDocumentPage
import cn.yuandianai.juziscan.scan.writePdfToStream
import cn.yuandianai.juziscan.scan.writeSingleImageToFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryRepository(private val context: Context) {
    companion object {
        private val storageLock = Any()
        private val documentLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        private val processingLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    }
    private val rootDir = File(context.filesDir, "history").apply { mkdirs() }
    private val exportDir = File(rootDir, "exports").apply { mkdirs() }
    private val thumbDir = File(rootDir, "thumbs").apply { mkdirs() }
    private val recordsFile = File(rootDir, "records.json")
    private val atomicRecords = AtomicFile(recordsFile)
    private val documentsDir = File(rootDir, "documents").apply { mkdirs() }
    private val documentStore = DocumentStore(documentsDir)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    private fun loadIndex(): List<HistoryRecord> {
        if (!recordsFile.exists() && !File(recordsFile.path + ".bak").exists()) return emptyList()
        val raw = atomicRecords.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(obj.toHistoryRecord())
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    fun loadRecords(): List<HistoryRecord> = synchronized(storageLock) {
        val records = loadIndex().associateBy { it.id }.toMutableMap()
        documentsDir.listFiles()?.filter { it.isDirectory && !isDocumentDeleted(it.name) }?.forEach { directory ->
            documentStore.readManifestOrNull(directory)?.takeIf { it.recordId == directory.name }?.let {
                records[directory.name] = recordFromManifest(directory.name, it)
            }
        }
        records.values.filterNot { isDocumentDeleted(it.id) }.sortedByDescending { it.updatedAtMillis }
    }

    fun saveDocumentRecord(pages: List<ScannedPage>, recordId: String? = null): HistoryRecord {
        val record = commitDocument(pages, recordId ?: UUID.randomUUID().toString(), reliableOnly = false)
        mergeDocumentIndex(record.id)
        return record
    }

    /** Atomic per-document commit; encoding never holds the shared history-index lock. */
    fun commitDocument(pages: List<ScannedPage>, id: String, reliableOnly: Boolean = true,
                       expectedVersion: Long? = null): HistoryRecord =
        synchronized(documentLocks.getOrPut(id) { Any() }) {
            require(id.matches(Regex("[a-zA-Z0-9-]+"))) { "无效文档 ID" }
            check(!isDocumentDeleted(id)) { "文档已删除" }
            require(pages.isNotEmpty()) { "没有可保存的页面" }
            val dir = File(documentsDir, id)
            val old = documentStore.readManifestOrNull(dir)
            val previous = if (old?.recordId == id) recordFromManifest(id, old)
                else synchronized(storageLock) { loadIndex().firstOrNull { it.id == id } }
            if (expectedVersion != null && expectedVersion != (previous?.updatedAtMillis ?: 0L)) {
                throw java.util.ConcurrentModificationException("文档已更新，请重新操作")
            }
            require(previous == null || previous.kind == HistoryRecordKind.Document)
            val fingerprint = documentFingerprint(pages)
            if (previous?.documentFingerprint == fingerprint) return@synchronized previous
            val now = maxOf(System.currentTimeMillis(), (previous?.updatedAtMillis ?: 0L) + 1)
            val metadata = DocumentManifest(pages = emptyList(), coverSignature = "", recordId = id,
                createdAtMillis = previous?.createdAtMillis ?: now, updatedAtMillis = now,
                title = previous?.title ?: "文件扫描 ${timeFormat.format(Date(now))}",
                fileName = previous?.fileName ?: "桔子扫描_文件扫描_${timestampToken(now)}.pdf",
                fingerprint = fingerprint, version = (old?.version ?: 0L) + 1)
            val manifest = documentStore.save(dir, pages.toList(), metadata, reliableOnly)
            recordFromManifest(id, manifest)
        }

    fun captureFile(id: String, pageId: String, revision: String): File {
        require(listOf(id, pageId, revision).all { it.matches(Regex("[a-zA-Z0-9-]+")) })
        check(!isDocumentDeleted(id)) { "文档已删除" }
        val dir = File(rootDir, "captures/$id").apply { check(isDirectory || mkdirs()) { "无法保存原图，请检查存储空间" } }
        return File(dir, "$pageId-$revision.jpg")
    }

    fun documentRecord(id: String): HistoryRecord? {
        if (isDocumentDeleted(id)) return null
        val manifest = documentStore.readManifestOrNull(File(documentsDir, id))
        return if (manifest?.recordId == id) recordFromManifest(id, manifest)
            else synchronized(storageLock) { loadIndex().firstOrNull { it.id == id } }
    }

    fun mergeDocumentIndex(id: String) = synchronized(storageLock) {
        if (!isDocumentDeleted(id)) {
            val manifest = documentStore.readManifestOrNull(File(documentsDir, id)) ?: return@synchronized
            if (manifest.recordId == null) return@synchronized
            val record = recordFromManifest(id, manifest)
            if (loadIndex().firstOrNull { it.id == id } != record) upsert(record)
        }
    }

    fun maintainDocumentCaches(id: String) = synchronized(documentLocks.getOrPut(id) { Any() }) {
        if (!isDocumentDeleted(id)) documentStore.maintainCaches(File(documentsDir, id))
    }

    fun isDocumentDeleted(id: String): Boolean = File(rootDir, "deleted/$id").isFile

    private fun recordFromManifest(id: String, manifest: DocumentManifest): HistoryRecord {
        val dir = File(documentsDir, id)
        return HistoryRecord(id = id, kind = HistoryRecordKind.Document, title = requireNotNull(manifest.title),
            subtitle = "${manifest.pages.size} 页扫描文档", createdAtMillis = manifest.createdAtMillis,
            updatedAtMillis = manifest.updatedAtMillis, pageCount = manifest.pages.size,
            fileName = requireNotNull(manifest.fileName), filePath = documentStore.pdfCacheFile(dir).path,
            mimeType = "application/pdf", thumbnailPath = File(dir, "thumbnail.jpg").path,
            documentManifestPath = File(dir, "document.json").path, documentFingerprint = manifest.fingerprint)
    }

    fun loadDocumentPages(recordId: String): List<ScannedPage> {
        val record = loadRecords().firstOrNull { it.id == recordId && it.kind == HistoryRecordKind.Document }
            ?: error("文档不存在")
        return documentStore.loadPages(requireNotNull(record.documentManifestPath) { "文档页面数据不存在" })
    }

    fun processCachedPage(id: String, page: ScannedPage,
                          onIntermediate: ((ScannedPage) -> Unit)? = null): ScannedPage =
        synchronized(processingLocks.getOrPut(id) { Any() }) {
            if (isDocumentDeleted(id)) throw cn.yuandianai.juziscan.scan.ObsoletePageResultException()
            val target = requireNotNull(processedCacheFileFor(id, page)) { "文档不存在" }
            if (target.isFile && target.length() > 0) return@synchronized page.copy(processedImagePath = target.path,
                previewBitmap = cn.yuandianai.juziscan.scan.decodeBitmapFromFileBounded(target.path, 640),
                detectedQuad = cn.yuandianai.juziscan.scan.readDetectedQuad(target))
            val staged = cn.yuandianai.juziscan.scan.createTempProcessedFile(context)
            val stagedPreview = File(staged.parentFile, staged.nameWithoutExtension + ".jpg")
            val stagedQuad = File(staged.path + ".quad")
            try {
                val result = processDocumentPage(context, page, staged, onIntermediate)
                synchronized(documentLocks.getOrPut(id) { Any() }) {
                    if (isDocumentDeleted(id)) throw cn.yuandianai.juziscan.scan.ObsoletePageResultException()
                    val manifest = documentStore.readManifestOrNull(File(documentsDir, id))
                    val current = manifest?.findPage(page.id)
                    val expected = computePageCacheKey(page.contentRevision, page.correctionMode.name,
                        page.imageStyle, page.editableQuad.map { QuadPoint(it.x, it.y) })
                    if (manifest != null && (current == null || current.contentRevision != page.contentRevision ||
                            current.cacheKey != expected)) throw cn.yuandianai.juziscan.scan.ObsoletePageResultException()
                    check(current != null || documentRecord(id) != null) { "文档不存在" }
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建页面缓存目录" }
                    if (stagedPreview.isFile) check(stagedPreview.renameTo(File(target.parentFile, target.nameWithoutExtension + ".jpg")))
                    if (stagedQuad.isFile) check(stagedQuad.renameTo(File(target.path + ".quad")))
                    // Publish the PNG last: its presence marks a completed cache write.
                    check(staged.renameTo(target)) { "页面缓存提交失败" }
                    result.copy(processedImagePath = target.path)
                }
            } finally {
                listOf(staged, stagedPreview, stagedQuad, File(stagedQuad.path + ".new")).forEach { it.delete() }
            }
        }

    /** 已保存文档某页的处理缓存目标（后台逐页处理直接写入文档缓存）。 */
    fun processedCacheFileFor(recordId: String, page: ScannedPage): File? {
        val documentDir = File(documentsDir, recordId)
        return if (documentDir.isDirectory) documentStore.processedCacheFileFor(documentDir, page) else null
    }

    /** 已保存文档的页面文件路径（保存提交后重绑定内存页面使用）。 */
    fun pageAssetPaths(recordId: String, page: ScannedPage): Pair<String, String?> {
        val documentDir = File(documentsDir, recordId)
        val sourcePath = documentStore.pageSourcePathFor(documentDir, page)
        // 只绑定真正可取用的处理图：缓存被清理过的页面退回待处理，由后台队列补齐。
        val processedPath = documentStore.processedCacheFileFor(documentDir, page)
            .takeIf { it.isFile && it.length() > 0 }?.path
        return sourcePath to processedPath
    }

    fun prepareDocumentPages(id: String): List<ScannedPage> {
        return loadDocumentPages(id).map { page -> if (page.hasProcessedOutput) page else processCachedPage(id, page) }
    }

    /**
     * PDF 按需生成与缓存复用：
     * - 文档指纹未变化且缓存存在 → 直接复用；
     * - 缺失或失效 → 补齐未处理页面后重新生成并替换缓存。
     * 文档自动保存不再触发本方法，仅分享 / 打开 / 导出等明确需要 PDF 时调用。
     */
    fun ensureDocumentPdf(recordId: String): File = synchronized(processingLocks.getOrPut(recordId) { Any() }) {
        val record = loadRecords().firstOrNull { it.id == recordId && it.kind == HistoryRecordKind.Document }
            ?: error("文档不存在")
        val manifestPath = requireNotNull(record.documentManifestPath) { "文档页面数据不存在" }
        val documentDir = File(documentsDir, record.id)
        try {
        val pdfFile = documentStore.pdfCacheFile(documentDir)
        val metaFile = documentStore.pdfMetaFile(pdfFile)
        val fingerprint = record.documentFingerprint
        if (pdfFile.isFile && metaFile.isFile && fingerprint != null &&
            metaFile.readText(Charsets.UTF_8) == fingerprint
        ) {
            return pdfFile
        }

        val pages = documentStore.loadPages(manifestPath)
        val processedPages = pages.map { page ->
            val target = documentStore.processedCacheFileFor(documentDir, page)
            when {
                // 缓存已在磁盘上：即便清单还没刷新（后台补处理先于清单提交），
                // 也按缓存回填路径，PDF 生成不依赖内存位图或清单时序。
                target.isFile && target.length() > 0 -> page.copy(processedImagePath = target.path)
                page.processedImagePath?.let { File(it).isFile && File(it).length() > 0 } == true -> {
                    // schema 1 遗留处理图迁移到新缓存位置。
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建页面缓存目录" }
                    File(page.processedImagePath!!).copyTo(target, overwrite = true)
                    page.copy(processedImagePath = target.path)
                }
                else -> processCachedPage(recordId, page)
            }
        }
        // 后台补处理可能完善了四角，收敛清单与缓存键（失败不阻断 PDF 生成）。
        writePdfCacheFile(recordId, pdfFile, processedPages, fingerprint)
        return pdfFile
        } finally {
            if (isDocumentDeleted(recordId)) File(documentsDir, recordId).deleteRecursively()
        }
    }

    fun saveIdCardRecord(
        documentType: IdCardDocumentType,
        compositePage: ScannedPage,
        sourcePageCount: Int
    ): HistoryRecord = synchronized(storageLock) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val file = File(exportDir, "$id.png")
        writeSingleImageToFile(
            file = file,
            page = compositePage,
            fileType = ExportFileType.PNG,
            fileSize = ExportFileSize.Actual
        )
        val thumb = saveThumbnail(
            recordId = id,
            bitmap = compositePage.previewBitmap ?: compositePage.processedBitmap ?: compositePage.sourceBitmap
        )
        val record = HistoryRecord(
            id = id,
            kind = HistoryRecordKind.IdCard,
            title = "${documentType.label}扫描",
            subtitle = "$sourcePageCount 张证件图 · PNG 图片",
            createdAtMillis = now,
            pageCount = sourcePageCount,
            fileName = "桔子扫描_${documentType.label}_${timestampToken(now)}.png",
            filePath = file.absolutePath,
            mimeType = "image/png",
            thumbnailPath = thumb.absolutePath
        )
        upsert(record)
        return record
    }

    fun saveOrUpdateOcrRecord(
        recordId: String?,
        page: ScannedPage,
        text: String
    ): HistoryRecord = synchronized(storageLock) {
        val normalizedText = text.trim()
        require(normalizedText.isNotBlank()) { "No OCR text to save." }
        val now = System.currentTimeMillis()
        val id = recordId ?: UUID.randomUUID().toString()
        val file = File(exportDir, "$id.txt")
        file.writeText(normalizedText, Charsets.UTF_8)
        val thumb = saveThumbnail(
            recordId = id,
            bitmap = page.previewBitmap ?: page.processedBitmap ?: page.sourceBitmap
        )
        val record = HistoryRecord(
            id = id,
            kind = HistoryRecordKind.Ocr,
            title = "文字识别 ${timeFormat.format(Date(now))}",
            subtitle = buildOcrSubtitle(normalizedText),
            createdAtMillis = now,
            pageCount = 1,
            fileName = "桔子扫描_文字识别_${timestampToken(now)}.txt",
            filePath = file.absolutePath,
            mimeType = "text/plain",
            thumbnailPath = thumb.absolutePath,
            textPreview = normalizedText.take(120)
        )
        upsert(record)
        return record
    }

    fun deleteRecord(recordId: String) = synchronized(documentLocks.getOrPut(recordId) { Any() }) {
        require(recordId.matches(Regex("[a-zA-Z0-9-]+")))
        synchronized(storageLock) {
            val records = loadRecords().toMutableList()
            val target = records.firstOrNull { it.id == recordId }
            val marker = File(rootDir, "deleted/$recordId")
            check(marker.parentFile!!.isDirectory || marker.parentFile!!.mkdirs())
            val atomic = AtomicFile(marker)
            val stream = atomic.startWrite()
            try {
                stream.write("deleted".toByteArray()); stream.fd.sync(); atomic.finishWrite(stream)
                check(!File(marker.path + ".new").exists()) { "删除未完成" }
            } catch (error: Throwable) { atomic.failWrite(stream); throw error }
            records.removeAll { it.id == recordId }
            persist(records)
            target?.let { File(it.filePath).delete(); it.thumbnailPath?.let { path -> File(path).delete() } }
        }
        File(documentsDir, recordId).deleteRecursively()
        File(rootDir, "captures/$recordId").deleteRecursively()
        Unit
    }

    fun shareRecord(record: HistoryRecord) {
        val (sharedName, uri) = prepareExternalFile(record)
        val chooserTitle = when (record.kind) {
            HistoryRecordKind.Document -> "分享扫描文档"
            HistoryRecordKind.IdCard -> "分享证件扫描"
            HistoryRecordKind.Ocr -> "分享识别文本"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, sharedName)
            clipData = ClipData.newUri(context.contentResolver, sharedName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openRecord(record: HistoryRecord) {
        val (displayName, uri) = prepareExternalFile(record)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, record.mimeType)
            clipData = ClipData.newUri(context.contentResolver, displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Starting an activity does not require package visibility. A preflight
        // resolveActivity() can hide installed PDF viewers on Android 11+.
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw IllegalStateException("未找到可预览此类文件的应用", error)
        }
    }

    private fun prepareExternalFile(record: HistoryRecord): Pair<String, android.net.Uri> {
        // 文档记录不再保证随时存在最新 PDF；需要时按指纹生成或复用缓存。
        val sourceFile = if (record.kind == HistoryRecordKind.Document && record.documentManifestPath != null) {
            ensureDocumentPdf(record.id)
        } else {
            File(record.filePath).also { require(it.isFile) { "文档文件不存在，可能已被清理" } }
        }
        val displayName = record.fileName
            .let { if (it.startsWith("桔子扫描_")) it else "桔子扫描_$it" }
        val externalFile = File(context.cacheDir, displayName)
        sourceFile.copyTo(externalFile, overwrite = true)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            externalFile
        )
        return displayName to uri
    }

    private fun writePdfCacheFile(id: String, pdfFile: File, pages: List<ScannedPage>, fingerprint: String?) {
        // Encode outside document storage: deleting a document must not be undone by a late export.
        val tmp = File.createTempFile("document-export-", ".pdf", context.cacheDir)
        try {
            FileOutputStream(tmp).use { output ->
                writePdfToStream(output, pages, ExportFileSize.Actual)
                output.flush()
                output.fd.sync()
            }
            synchronized(documentLocks.getOrPut(id) { Any() }) {
                check(!isDocumentDeleted(id)) { "文档已删除" }
                check(loadRecords().firstOrNull { it.id == id }?.documentFingerprint == fingerprint) {
                    "文档已更新，请重新导出"
                }
                val cacheDir = requireNotNull(pdfFile.parentFile)
                check(cacheDir.isDirectory || cacheDir.mkdirs()) { "无法创建 PDF 缓存目录" }
                check(tmp.renameTo(pdfFile)) { "PDF 缓存写入失败" }
                val atomic = AtomicFile(documentStore.pdfMetaFile(pdfFile))
                val stream = atomic.startWrite()
                try {
                    stream.write((fingerprint ?: "").toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                    atomic.finishWrite(stream)
                } catch (error: Throwable) { atomic.failWrite(stream); throw error }
            }
        } finally { tmp.delete() }
    }

    fun decodeThumbnail(record: HistoryRecord): Bitmap? {
        val path = record.thumbnailPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    private fun saveThumbnail(recordId: String, bitmap: Bitmap): File {
        val output = File(thumbDir, "$recordId.jpg")
        val scaled = scaleThumbnail(bitmap, 360)
        output.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return output
    }

    private fun scaleThumbnail(bitmap: Bitmap, maxSize: Int): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxEdge.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun buildOcrSubtitle(text: String): String {
        val lineCount = text.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        return "$lineCount 行识别文字 · ${text.length} 字"
    }

    private fun timestampToken(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
    }

    private fun upsert(record: HistoryRecord) {
        val records = loadRecords().toMutableList()
        records.removeAll { it.id == record.id }
        records.add(0, record)
        persist(records)
    }

    private fun persist(records: List<HistoryRecord>) {
        val array = JSONArray()
        records.sortedByDescending { it.updatedAtMillis }.forEach { record ->
            array.put(record.toJson())
        }
        val stream = atomicRecords.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            // AtomicFile logs some sync/rename errors instead of throwing them.
            stream.flush()
            stream.fd.sync()
            atomicRecords.finishWrite(stream)
            check(!File(recordsFile.path + ".new").exists()) { "历史索引保存失败" }
        } catch (error: Throwable) {
            atomicRecords.failWrite(stream)
            throw error
        }
    }

    private fun HistoryRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("subtitle", subtitle)
            .put("createdAtMillis", createdAtMillis)
            .put("updatedAtMillis", updatedAtMillis)
            .put("documentManifestPath", documentManifestPath)
            .put("documentFingerprint", documentFingerprint)
            .put("pageCount", pageCount)
            .put("fileName", fileName)
            .put("filePath", filePath)
            .put("mimeType", mimeType)
            .put("thumbnailPath", thumbnailPath)
            .put("textPreview", textPreview)
    }

    private fun JSONObject.toHistoryRecord(): HistoryRecord {
        return HistoryRecord(
            id = getString("id"),
            kind = HistoryRecordKind.valueOf(getString("kind")),
            title = getString("title"),
            subtitle = getString("subtitle"),
            createdAtMillis = getLong("createdAtMillis"),
            updatedAtMillis = getLong("updatedAtMillis"),
            documentManifestPath = optString("documentManifestPath").ifBlank { null },
            documentFingerprint = optString("documentFingerprint").ifBlank { null },
            pageCount = optInt("pageCount", 1),
            fileName = getString("fileName"),
            filePath = getString("filePath"),
            mimeType = getString("mimeType"),
            thumbnailPath = optString("thumbnailPath").ifBlank { null },
            textPreview = optString("textPreview").ifBlank { null }
        )
    }
}
