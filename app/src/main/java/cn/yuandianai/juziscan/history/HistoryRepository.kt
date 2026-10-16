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
    companion object { private val storageLock = Any() }
    private val rootDir = File(context.filesDir, "history").apply { mkdirs() }
    private val exportDir = File(rootDir, "exports").apply { mkdirs() }
    private val thumbDir = File(rootDir, "thumbs").apply { mkdirs() }
    private val recordsFile = File(rootDir, "records.json")
    private val atomicRecords = AtomicFile(recordsFile)
    private val documentsDir = File(rootDir, "documents").apply { mkdirs() }
    private val documentStore = DocumentStore(documentsDir)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun loadRecords(): List<HistoryRecord> = synchronized(storageLock) {
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

    /**
     * 增量保存：只写入新增或真正变化的页面资产，清单原子提交；
     * 不再生成 PDF，也不复制整份文档。页面尚未完成处理不妨碍原始数据保存。
     */
    fun saveDocumentRecord(pages: List<ScannedPage>, recordId: String? = null): HistoryRecord = synchronized(storageLock) {
        require(pages.isNotEmpty()) { "No pages to save." }
        val previous = recordId?.let { id -> loadRecords().firstOrNull { it.id == id }
            ?: error("原文档不存在") }
        require(previous == null || previous.kind == HistoryRecordKind.Document)
        val fingerprint = documentFingerprint(pages)
        if (previous?.documentFingerprint == fingerprint) return previous
        val now = maxOf(System.currentTimeMillis(), (previous?.updatedAtMillis ?: 0L) + 1)
        val id = previous?.id ?: UUID.randomUUID().toString()
        val documentDir = File(documentsDir, id)
        // 索引是文档记录的提交点，清单是文档内容的提交点：后者先落盘，前者失败时必须回滚后者。
        val rollbackManifest = previous?.documentManifestPath
            ?.let { runCatching { File(it).readBytes() }.getOrNull() }
        var committed = false
        try {
            check(documentDir.exists() || documentDir.mkdirs()) { "无法创建文档目录" }
            documentStore.save(documentDir, pages)
            val record = HistoryRecord(
                id = id,
                kind = HistoryRecordKind.Document,
                title = previous?.title ?: "文件扫描 ${timeFormat.format(Date(now))}",
                subtitle = "${pages.size} 页 PDF 文档",
                createdAtMillis = previous?.createdAtMillis ?: now,
                pageCount = pages.size,
                fileName = previous?.fileName ?: "桔子扫描_文件扫描_${timestampToken(now)}.pdf",
                filePath = documentStore.pdfCacheFile(documentDir).absolutePath,
                mimeType = "application/pdf",
                thumbnailPath = File(documentDir, "thumbnail.jpg").absolutePath,
                updatedAtMillis = now,
                documentManifestPath = File(documentDir, "document.json").absolutePath,
                documentFingerprint = fingerprint
            )
            runCatching { upsert(record) }.onFailure { error ->
                // 索引提交失败：回滚已提交的清单，否则文档内容会领先于索引，用户会看到「未保存」但内容已变。
                rollbackManifest?.let { bytes ->
                    runCatching { documentStore.restoreManifest(documentDir, bytes) }
                        .onFailure { rollback -> Log.w("JuZiScan", "Could not roll back document manifest", rollback) }
                }
                throw error
            }
            committed = true
            return record
        } finally {
            // 新建文档保存失败可整体回退；已有文档依赖旧清单继续指向旧资产。
            if (!committed && previous == null) {
                runCatching { documentDir.deleteRecursively() }
            }
        }
    }

    fun loadDocumentPages(recordId: String): List<ScannedPage> = synchronized(storageLock) {
        val record = loadRecords().firstOrNull { it.id == recordId && it.kind == HistoryRecordKind.Document }
            ?: error("文档不存在")
        documentStore.loadPages(requireNotNull(record.documentManifestPath) { "文档页面数据不存在" })
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
        val processedPath = if (page.hasProcessedOutput) {
            documentStore.processedCacheFileFor(documentDir, page).path
        } else null
        return sourcePath to processedPath
    }

    /**
     * PDF 按需生成与缓存复用：
     * - 文档指纹未变化且缓存存在 → 直接复用；
     * - 缺失或失效 → 补齐未处理页面后重新生成并替换缓存。
     * 文档自动保存不再触发本方法，仅分享 / 打开 / 导出等明确需要 PDF 时调用。
     */
    fun ensureDocumentPdf(recordId: String): File = synchronized(storageLock) {
        val record = loadRecords().firstOrNull { it.id == recordId && it.kind == HistoryRecordKind.Document }
            ?: error("文档不存在")
        val manifestPath = requireNotNull(record.documentManifestPath) { "文档页面数据不存在" }
        val documentDir = File(documentsDir, record.id)
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
                target.isFile && target.length() > 0 -> page
                page.processedImagePath?.let { File(it).isFile && File(it).length() > 0 } == true -> {
                    // schema 1 遗留处理图迁移到新缓存位置。
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建页面缓存目录" }
                    File(page.processedImagePath!!).copyTo(target, overwrite = true)
                    page.copy(processedImagePath = target.path)
                }
                else -> processDocumentPage(context, page, target)
            }
        }
        // 后台补处理可能完善了四角，收敛清单与缓存键（失败不阻断 PDF 生成）。
        val refreshedManifestPath = runCatching { documentStore.refreshManifest(documentDir, processedPages) }
            .onFailure { Log.w("JuZiScan", "Could not refresh manifest during PDF export", it) }
            .getOrNull()
        writePdfCacheFile(pdfFile, processedPages, fingerprint)
        if (refreshedManifestPath != null && refreshedManifestPath != record.documentManifestPath) {
            upsert(record.copy(documentManifestPath = refreshedManifestPath))
        }
        return pdfFile
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

    fun deleteRecord(recordId: String) = synchronized(storageLock) {
        val records = loadRecords().toMutableList()
        val target = records.firstOrNull { it.id == recordId } ?: return
        records.removeAll { it.id == recordId }
        persist(records)
        File(target.filePath).delete()
        target.thumbnailPath?.let { File(it).delete() }
        if (target.kind == HistoryRecordKind.Document) {
            File(documentsDir, target.id).deleteRecursively()
        }
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

    private fun writePdfCacheFile(pdfFile: File, pages: List<ScannedPage>, fingerprint: String?) {
        val cacheDir = pdfFile.parentFile
        check(cacheDir != null && (cacheDir.isDirectory || cacheDir.mkdirs())) { "无法创建 PDF 缓存目录" }
        val tmp = File(cacheDir, pdfFile.name + ".tmp")
        FileOutputStream(tmp).use { output ->
            writePdfToStream(output, pages, ExportFileSize.Actual)
            output.flush()
            output.fd.sync()
        }
        if (pdfFile.exists() && !pdfFile.delete()) {
            tmp.delete()
            throw IllegalStateException("PDF 缓存替换失败")
        }
        check(tmp.renameTo(pdfFile)) { "PDF 缓存写入失败" }
        val metaFile = documentStore.pdfMetaFile(pdfFile)
        FileOutputStream(metaFile).use { output ->
            output.write((fingerprint ?: "").toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
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
