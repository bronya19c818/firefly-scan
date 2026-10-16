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
import cn.yuandianai.juziscan.scan.writeSingleImageToFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    fun saveDocumentRecord(pages: List<ScannedPage>, recordId: String? = null): HistoryRecord = synchronized(storageLock) {
        require(pages.isNotEmpty()) { "No pages to save." }
        require(pages.all { it.isProcessed }) { "页面尚未处理完成" }
        val previous = recordId?.let { id -> loadRecords().firstOrNull { it.id == id }
            ?: error("原文档不存在") }
        require(previous == null || previous.kind == HistoryRecordKind.Document)
        val fingerprint = documentFingerprint(pages)
        if (previous?.documentFingerprint == fingerprint) return previous
        val now = maxOf(System.currentTimeMillis(), (previous?.updatedAtMillis ?: 0L) + 1)
        val id = previous?.id ?: UUID.randomUUID().toString()
        val directory = File(File(documentsDir, id).apply { mkdirs() }, UUID.randomUUID().toString())
        var committed = false
        try {
            documentStore.writeVersion(directory, pages)
            val record = HistoryRecord(
                id = id,
                kind = HistoryRecordKind.Document,
                title = previous?.title ?: "文件扫描 ${timeFormat.format(Date(now))}",
                subtitle = "${pages.size} 页 PDF 文档",
                createdAtMillis = previous?.createdAtMillis ?: now,
                pageCount = pages.size,
                fileName = previous?.fileName ?: "桔子扫描_文件扫描_${timestampToken(now)}.pdf",
                filePath = File(directory, "document.pdf").absolutePath,
                mimeType = "application/pdf",
                thumbnailPath = File(directory, "thumbnail.jpg").absolutePath,
                updatedAtMillis = now,
                documentManifestPath = File(directory, "document.json").absolutePath,
                documentFingerprint = fingerprint
            )
            upsert(record)
            committed = true
            // Cleanup is after the commit and cannot turn a successful save into a failure.
            runCatching {
                val liveVersions = loadRecords().filter { it.kind == HistoryRecordKind.Document }
                    .associate { it.id to it.documentManifestPath?.let { path -> File(path).parentFile } }
                documentsDir.listFiles()?.forEach { document ->
                    val live = liveVersions[document.name]
                    val obsolete = if (live == null) listOf(document)
                        else document.listFiles()?.filter { it != live }.orEmpty()
                    obsolete.forEach {
                        if (!it.deleteRecursively()) Log.w("JuZiScan", "Could not remove obsolete document version")
                    }
                }
            }.onFailure {
                Log.w("JuZiScan", "Could not clean obsolete document versions", it)
            }
            return record
        } finally {
            if (!committed) directory.deleteRecursively()
        }
    }

    fun loadDocumentPages(recordId: String): List<ScannedPage> = synchronized(storageLock) {
        val record = loadRecords().firstOrNull { it.id == recordId && it.kind == HistoryRecordKind.Document }
            ?: error("文档不存在")
        documentStore.loadPages(requireNotNull(record.documentManifestPath) { "文档页面数据不存在" })
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
        val sourceFile = File(record.filePath)
        require(sourceFile.isFile) { "文档文件不存在，可能已被清理" }
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
